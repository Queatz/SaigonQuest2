package quest.saigon.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import quest.saigon.Language
import quest.saigon.shared.network.TownApiClient
import quest.saigon.shared.platform.InMemoryTokenStorage
import quest.saigon.shared.store.FeedFilter
import quest.saigon.shared.store.FeedStore
import quest.saigon.shared.store.NotificationStore
import quest.saigon.shared.store.PostDetailStore
import quest.saigon.shared.store.TownSessionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CoreStoresTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun sessionStoreHandlesBootstrapLoginUnauthorizedAndLogout() = runTest {
        val storage = InMemoryTokenStorage()
        var loggedIn = false
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/api/config" -> respond("""{"mapsKey":"test-maps-key"}""", HttpStatusCode.OK, jsonHeaders)
                path == "/api/me" && request.headers[HttpHeaders.Authorization] == "Bearer session-1" && loggedIn -> {
                    respond(
                        """{"id":"u1","name":"An","phone":"+84901234567","language":"vi","photoUrl":"/api/me/photo"}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                }
                path == "/api/me" -> respond("""{"error":"unauthorized"}""", HttpStatusCode.Unauthorized, jsonHeaders)
                path == "/api/auth/login" && request.method == HttpMethod.Post -> {
                    loggedIn = true
                    respond(
                        """{"id":"u1","name":"An","phone":"+84901234567","language":"vi","photoUrl":"/api/me/photo","token":"session-1"}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                }
                path == "/api/auth/logout" && request.method == HttpMethod.Post -> {
                    loggedIn = false
                    respond("""{"ok":true}""", HttpStatusCode.OK, jsonHeaders)
                }
                else -> respond("""{"error":"not_found"}""", HttpStatusCode.NotFound, jsonHeaders)
            }
        }
        var sessionStore: TownSessionStore? = null
        val api = TownApiClient(
            engine = engine,
            tokenStorage = storage,
            serverOriginProvider = { "https://saigon.town" },
            languageProvider = { sessionStore?.currentLanguage ?: Language.EN },
            onUnauthorized = { sessionStore?.handleUnauthorized() },
        )
        sessionStore = TownSessionStore(api, backgroundScope) { "en-US" }

        sessionStore.bootstrap()
        assertTrue(sessionStore.state.value.isSignedOut)
        assertEquals("test-maps-key", sessionStore.state.value.mapsKey)

        assertTrue(sessionStore.login("0901234567", "secretpass"))
        assertTrue(sessionStore.state.value.isSignedIn)
        assertEquals("session-1", storage.readSessionToken())
        assertEquals(Language.VI, sessionStore.state.value.language)
        assertEquals("https://saigon.town/api/me/photo", sessionStore.state.value.me?.photoUrl)

        val refreshed = sessionStore.refreshMe()
        assertEquals("An", refreshed?.name)

        sessionStore.logout()
        assertTrue(sessionStore.state.value.isSignedOut)
        assertNull(storage.readSessionToken())
    }

    @Test
    fun feedAndPostDetailStoresPaginateDeduplicateAndSyncPostUpdates() = runTest {
        val storage = InMemoryTokenStorage(initialToken = "tok")
        val recordedViews = mutableListOf<String>()
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                val path = request.url.encodedPath
                val cursor = request.url.parameters["cursor"]
                when {
                    path == "/api/posts" && cursor == null -> respond(
                        """
                        {
                          "posts": [
                            {"id":"p1","authorName":"Linh","message":"First","location":"D1","photoUrl":"/media/p1.jpg","commentCount":0,"pollOptions":[{"index":0,"text":"Yes","voteCount":0,"voted":false}]}
                          ],
                          "nextCursor": "c1"
                        }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/posts" && cursor == "c1" -> respond(
                        """
                        {
                          "posts": [
                            {"id":"p1","authorName":"Linh","message":"First","location":"D1","photoUrl":"/media/p1.jpg"},
                            {"id":"p2","authorName":"Minh","message":"Second","location":"D2","photoUrl":"/media/p2.jpg"}
                          ],
                          "nextCursor": null
                        }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/posts/p99" && request.method == HttpMethod.Get -> respond(
                        """{"id":"p99","authorName":"Hoa","message":"Deep link","location":"D3","photoUrl":"/media/p99.jpg"}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path.endsWith("/view") -> {
                        recordedViews += path
                        respond("""{"ok":true}""", HttpStatusCode.OK, jsonHeaders)
                    }
                    path == "/api/posts/p1/poll/0" && request.method == HttpMethod.Post -> respond(
                        """{"id":"p1","authorName":"Linh","message":"First","location":"D1","photoUrl":"/media/p1.jpg","pollOptions":[{"index":0,"text":"Yes","voteCount":1,"voted":true}]}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/posts/p1/comments" && request.method == HttpMethod.Post -> respond(
                        """{"id":"cm1","postId":"p1","authorName":"Minh","text":"Count me in","createdAt":1000}""",
                        HttpStatusCode.Created,
                        jsonHeaders,
                    )
                    else -> respond("""{"error":"not_found"}""", HttpStatusCode.NotFound, jsonHeaders)
                }
            }
        })
        val api = TownApiClient(engine = engine, tokenStorage = storage, serverOriginProvider = { "https://saigon.town" })
        val storeScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val feedStore = FeedStore(api, storeScope)
        val detailStore = PostDetailStore(api, feedStore, storeScope)

        feedStore.loadInitial(FeedFilter.Home, focusPostId = "p99")
        advanceUntilIdle()
        val feedState = feedStore.state.value
        assertEquals(listOf("p99", "p1"), feedState.posts.map { it.id }, "isLoading=${feedState.isLoading}, error=${feedState.error}")
        assertEquals("p99", feedStore.state.value.selectedPostId)

        feedStore.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("p99", "p1", "p2"), feedStore.state.value.posts.map { it.id })
        assertNull(feedStore.state.value.nextCursor)

        val voted = detailStore.votePoll("p1", 0)
        assertEquals(1, voted?.pollOptions?.first()?.voteCount)
        assertTrue(feedStore.state.value.posts.first { it.id == "p1" }.pollOptions.first().voted)

        val comment = detailStore.addComment("p1", "Count me in")
        assertEquals("cm1", comment?.id)
        val updatedP1 = feedStore.state.value.posts.first { it.id == "p1" }
        assertEquals(1, updatedP1.commentCount)
        assertEquals("Count me in", updatedP1.comments.first().text)

        feedStore.selectPost("p1")
        feedStore.selectPost("p1")
        advanceUntilIdle()
        assertEquals(2, recordedViews.size) // p99 once + p1 once
    }

    @Test
    fun notificationStoreStreamsLiveEventsAndMarksRead() = runTest {
        val storage = InMemoryTokenStorage(initialToken = "tok")
        var streamCalls = 0
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    path == "/api/notifications" && request.url.parameters["limit"] == "1" -> respond(
                        """{"notifications":[],"nextCursor":null,"unreadCount":2}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/notifications/stream" -> {
                        streamCalls += 1
                        if (streamCalls == 1) {
                            respond(
                                content = ByteReadChannel(
                                    "event: ready\ndata: {}\n\ndata: {\"id\":\"n3\",\"type\":\"post_comment\",\"postId\":\"p1\",\"actorName\":\"Minh\",\"preview\":\"Hey\",\"createdAt\":5,\"read\":false}\n\n",
                                ),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.EventStream.toString()),
                            )
                        } else {
                            respond("""{"error":"unauthorized"}""", HttpStatusCode.Unauthorized, jsonHeaders)
                        }
                    }
                    path == "/api/notifications/n3/read" -> respond(
                        """{"id":"n3","type":"post_comment","postId":"p1","actorName":"Minh","preview":"Hey","createdAt":5,"read":true}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/notifications/read-all" -> respond(
                        """{"ok":true,"unreadCount":0}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    else -> respond("""{"error":"not_found"}""", HttpStatusCode.NotFound, jsonHeaders)
                }
            }
        })
        val api = TownApiClient(engine = engine, tokenStorage = storage, serverOriginProvider = { "https://saigon.town" })
        val storeScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val store = NotificationStore(api, storeScope)

        store.connect()
        advanceUntilIdle()
        val notifState = store.state.value
        assertEquals(3L, notifState.unreadCount, "connected=${notifState.connected}, error=${notifState.error}, items=${notifState.items}")
        assertEquals("n3", store.state.value.items.first().id)
        assertFalse(store.state.value.items.first().read)

        store.markRead("n3")
        assertEquals(2L, store.state.value.unreadCount)
        assertTrue(store.state.value.items.first().read)

        assertTrue(store.markAllRead())
        assertEquals(0L, store.state.value.unreadCount)
        store.disconnect()
    }
}
