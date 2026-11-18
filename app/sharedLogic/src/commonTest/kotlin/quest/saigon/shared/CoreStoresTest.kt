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
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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

    @Test
    fun sessionStoreFallsBackToSetCookieWhenTokenMissingAndAttachesBothHeaders() = runTest {
        val storage = InMemoryTokenStorage()
        var sawBearerAndCookie = false
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/api/auth/login" && request.method == HttpMethod.Post -> respond(
                    content = """{"id":"u1","name":"An","phone":"+84901234567","language":"en"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                        HttpHeaders.SetCookie to listOf("sq_session=cookie-tok-42; Path=/; HttpOnly"),
                    ),
                )
                path == "/api/me" -> {
                    val auth = request.headers[HttpHeaders.Authorization]
                    val cookie = request.headers[HttpHeaders.Cookie]
                    if (auth == "Bearer cookie-tok-42" && cookie == "sq_session=cookie-tok-42") {
                        sawBearerAndCookie = true
                        respond("""{"id":"u1","name":"An","phone":"+84901234567","language":"en"}""", HttpStatusCode.OK, jsonHeaders)
                    } else {
                        respond("""{"error":"unauthorized"}""", HttpStatusCode.Unauthorized, jsonHeaders)
                    }
                }
                else -> respond("""{"error":"not_found"}""", HttpStatusCode.NotFound, jsonHeaders)
            }
        }
        val api = TownApiClient(engine = engine, tokenStorage = storage, serverOriginProvider = { "https://saigon.town" })
        val sessionStore = TownSessionStore(api, backgroundScope) { "en-US" }

        assertTrue(sessionStore.login("0901234567", "secretpass"))
        assertEquals("cookie-tok-42", storage.readSessionToken())

        val refreshed = sessionStore.refreshMe()
        assertTrue(sawBearerAndCookie)
        assertEquals("u1", refreshed?.id)
    }

    @Test
    fun discoverStoreEnsureLocationOpenPreservesSeededPlaceAndFallsBackToServerPlace() = runTest {
        val storage = InMemoryTokenStorage(initialToken = "tok")
        var placeGetCalls = 0
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    path == "/api/places/loc1" -> {
                        placeGetCalls += 1
                        respond("""{"id":"loc1","name":"Cafe Saigon","lat":10.77,"lng":106.70,"custom":true}""", HttpStatusCode.OK, jsonHeaders)
                    }
                    path == "/api/places/description" -> respond("""{"description":"Cozy spot"}""", HttpStatusCode.OK, jsonHeaders)
                    path == "/api/places/photos" -> respond("""{"photos":[]}""", HttpStatusCode.OK, jsonHeaders)
                    path == "/api/activities" -> respond("""{"activities":[]}""", HttpStatusCode.OK, jsonHeaders)
                    path == "/api/places/posts" -> respond("""{"posts":[],"nextCursor":null,"count":0}""", HttpStatusCode.OK, jsonHeaders)
                    else -> respond("""{"error":"not_found"}""", HttpStatusCode.NotFound, jsonHeaders)
                }
            }
        })
        val api = TownApiClient(engine = engine, tokenStorage = storage, serverOriginProvider = { "https://saigon.town" })
        val storeScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val discover = quest.saigon.shared.store.DiscoverStore(api, storeScope)

        discover.ensureLocationOpen("loc1")
        advanceUntilIdle()
        assertEquals("Cafe Saigon", discover.state.value.locationDetail?.name)
        assertEquals(10.77, discover.state.value.locationDetail?.place?.lat)
        assertEquals(1, placeGetCalls)

        // Calling ensureLocationOpen again with same ID should not wipe or re-fetch
        discover.ensureLocationOpen("loc1")
        advanceUntilIdle()
        assertEquals(1, placeGetCalls)
    }

    @Test
    fun composerStoreDoesNotAutoSelectAvatarAndSubmitsPhotoField() = runTest {
        val storage = InMemoryTokenStorage(initialToken = "tok")
        var sawPhotoField = false
        var sawPluralPhotosField = false
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    path == "/api/avatars" -> respond(
                        """{"avatars":[{"id":"av1","prompt":"cat","status":"ready","imageUrl":"/media/av1.png","createdAt":1}]}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/posts" && request.method == HttpMethod.Post -> {
                        val bodyBytes = when (val body = request.body) {
                            is io.ktor.http.content.OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
                            is io.ktor.http.content.OutgoingContent.WriteChannelContent -> coroutineScope {
                                val ch = io.ktor.utils.io.ByteChannel(autoFlush = true)
                                launch {
                                    body.writeTo(ch)
                                    ch.close()
                                }
                                ch.readRemaining().readText()
                            }
                            else -> body.toString()
                        }
                        sawPhotoField = bodyBytes.contains("name=\"photo\"") || bodyBytes.contains("name=photo")
                        sawPluralPhotosField = bodyBytes.contains("name=\"photos\"") || bodyBytes.contains("name=photos")
                        respond(
                            """{"id":"p_new","authorId":"u1","authorName":"An","message":"Hello","location":"Cafe","photoUrl":"/media/p.jpg","createdAt":10}""",
                            HttpStatusCode.OK,
                            jsonHeaders,
                        )
                    }
                    else -> respond("""{"error":"not_found"}""", HttpStatusCode.NotFound, jsonHeaders)
                }
            }
        })
        val api = TownApiClient(engine = engine, tokenStorage = storage, serverOriginProvider = { "https://saigon.town" })
        val storeScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val feed = FeedStore(api, storeScope)
        val composer = quest.saigon.shared.store.ComposerStore(api, feed, storeScope)

        composer.loadAvatars()
        advanceUntilIdle()
        assertEquals(1, composer.state.value.avatars.size)
        assertEquals(null, composer.state.value.selectedAvatarId)

        val created = composer.submitPost(
            mapOf("message" to "Hello", "location" to "Cafe"),
            listOf(byteArrayOf(1, 2, 3)),
        )
        assertEquals("p_new", created?.id)
        assertTrue(sawPhotoField, "Expected multipart part named 'photo'")
        assertFalse(sawPluralPhotosField, "Should not send multipart part named 'photos'")
    }

    @Test
    fun messagesStoreCheckDirectChatPromptsFirstMessageOn404AndCreatesOnSend() = runTest {
        val storage = InMemoryTokenStorage(initialToken = "tok")
        var createdText: String? = null
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    path == "/api/chats/direct/u2" && request.method == HttpMethod.Get -> respond(
                        """{"error":"not_found"}""",
                        HttpStatusCode.NotFound,
                        jsonHeaders,
                    )
                    path == "/api/chats" && request.method == HttpMethod.Post -> {
                        createdText = request.body.toString()
                        respond(
                            """{"id":"c_dm","type":"direct","title":"Binh","members":[{"id":"u2","name":"Binh"}],"messages":[]}""",
                            HttpStatusCode.OK,
                            jsonHeaders,
                        )
                    }
                    path == "/api/chats/c_dm" -> respond(
                        """{"id":"c_dm","type":"direct","title":"Binh","members":[{"id":"u2","name":"Binh"}],"messages":[{"id":"m1","senderId":"u1","senderName":"An","text":"Xin chao","createdAt":100}]}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    else -> respond("""{"error":"not_found"}""", HttpStatusCode.NotFound, jsonHeaders)
                }
            }
        })
        val api = TownApiClient(engine = engine, tokenStorage = storage, serverOriginProvider = { "https://saigon.town" })
        val storeScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val messages = quest.saigon.shared.store.MessagesStore(api, storeScope)

        val check = messages.checkDirectChat("u2")
        assertTrue(check is quest.saigon.shared.store.DirectChatCheckResult.NeedsFirstMessage)

        val created = messages.startDirectChat("u2", "Xin chao")
        advanceUntilIdle()
        assertEquals("c_dm", created?.id)
        assertEquals("c_dm", messages.state.value.activeChat?.id)
        assertTrue(createdText != null)
    }

    @Test
    fun discoverStorePreservesMapCameraAcrossUpdates() = runTest {
        val storage = InMemoryTokenStorage()
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { respond("""{"ok":true}""", HttpStatusCode.OK, jsonHeaders) }
        })
        val api = TownApiClient(engine = engine, tokenStorage = storage, serverOriginProvider = { "https://saigon.town" })
        val storeScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val discover = quest.saigon.shared.store.DiscoverStore(api, storeScope)

        discover.updateMapCamera(10.7812, 106.7019, 16.5f)
        assertEquals(10.7812, discover.state.value.mapCameraLat)
        assertEquals(106.7019, discover.state.value.mapCameraLng)
        assertEquals(16.5f, discover.state.value.mapCameraZoom)

        // Non-finite camera coordinates should be ignored
        discover.updateMapCamera(Double.NaN, 106.7, 15f)
        assertEquals(10.7812, discover.state.value.mapCameraLat)
        assertEquals(16.5f, discover.state.value.mapCameraZoom)
    }

    @Test
    fun profileStoreRenamesMusicResolvesFeatureAndTracksOpenOrders() = runTest {
        val storage = InMemoryTokenStorage(initialToken = "tok")
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    path == "/api/me/music" -> respond(
                        """{"music":[{"id":"m1","name":"Old Name","prompt":"rain","status":"ready","createdAt":1}]}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/music/m1" && request.method == HttpMethod.Patch -> respond(
                        """{"id":"m1","name":"Rainy Cafe","prompt":"rain","status":"ready","createdAt":1}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/features" -> respond(
                        """{"features":[{"id":"f1","text":"Dark theme","voteCount":2,"voted":false,"resolved":false}]}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/features/f1/resolve" && request.method == HttpMethod.Post -> respond(
                        """{"id":"f1","text":"Dark theme","voteCount":2,"voted":false,"resolved":true}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/me/orders" -> respond(
                        """{"orders":[{"id":"o1","productId":"p1","productName":"Coffee","price":50000,"resolved":false}],"openCount":3}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    else -> respond("""{"error":"not_found"}""", HttpStatusCode.NotFound, jsonHeaders)
                }
            }
        })
        val api = TownApiClient(engine = engine, tokenStorage = storage, serverOriginProvider = { "https://saigon.town" })
        val storeScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val profile = quest.saigon.shared.store.ProfileStore(api, storeScope)

        profile.loadMyMusic()
        profile.loadFeatures()
        profile.loadMyOrders()
        advanceUntilIdle()

        assertEquals(3L, profile.state.value.openOrdersCount)
        assertEquals("Old Name", profile.state.value.myMusic.single().name)
        assertFalse(profile.state.value.features.single().resolved)

        val renamed = profile.renameMyMusic("m1", "Rainy Cafe")
        assertEquals("Rainy Cafe", renamed?.name)
        assertEquals("Rainy Cafe", profile.state.value.myMusic.single().name)

        val resolved = profile.resolveFeature("f1")
        assertEquals(true, resolved?.resolved)
        assertTrue(profile.state.value.features.single().resolved)
    }
}
