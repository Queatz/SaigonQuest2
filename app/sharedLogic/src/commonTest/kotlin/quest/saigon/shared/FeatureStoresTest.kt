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
import quest.saigon.shared.model.CreateActivityRequest
import quest.saigon.shared.network.TownApiClient
import quest.saigon.shared.platform.InMemoryTokenStorage
import quest.saigon.shared.store.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureStoresTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun discoverStoreLoadsDiscoveryCommunitiesAndLocationDetails() = runTest {
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { req ->
                val path = req.url.encodedPath
                when {
                    path == "/api/events/summary" -> respond("""{"today":2,"tomorrow":1,"week":5}""", HttpStatusCode.OK, jsonHeaders)
                    path == "/api/events" -> respond(
                        """{"events":[{"id":"e1","message":"Meetup","location":"D1","photoUrl":"/media/e1.jpg","eventStartAt":1000}],"locations":[{"id":"loc1","name":"Bitexco","lat":10.77,"lng":106.70}],"people":[{"id":"u1","name":"An"}],"nextPeopleCursor":null}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/checkins" -> respond("""{"checkIns":[{"postId":"ci1","authorName":"Linh","location":"Ben Thanh","untilAt":9999}]}""", HttpStatusCode.OK, jsonHeaders)
                    path == "/api/communities" -> respond("""{"communities":[{"id":"c1","name":"Coffee","emoji":"☕","memberCount":4,"joined":false}]}""", HttpStatusCode.OK, jsonHeaders)
                    path == "/api/communities/c1/join" -> respond("""{"id":"c1","name":"Coffee","emoji":"☕","memberCount":5,"joined":true}""", HttpStatusCode.OK, jsonHeaders)
                    path == "/api/places/description" -> respond("""{"description":"Great view"}""", HttpStatusCode.OK, jsonHeaders)
                    path == "/api/places/photos" -> respond("""{"photos":[{"id":"ph1","url":"/media/ph1.jpg","userId":"u1","canDelete":true}]}""", HttpStatusCode.OK, jsonHeaders)
                    path == "/api/activities" -> respond("""{"activities":[{"id":"a1","name":"Sketching","locationName":"Bitexco","emoji":"🎨"}],"nextCursor":null}""", HttpStatusCode.OK, jsonHeaders)
                    path == "/api/places/posts" -> respond("""{"posts":[{"id":"p1","message":"Hi","location":"Bitexco","photoUrl":"/media/p1.jpg"}],"nextCursor":null,"count":1}""", HttpStatusCode.OK, jsonHeaders)
                    else -> respond("""{"error":"not_found"}""", HttpStatusCode.NotFound, jsonHeaders)
                }
            }
        })
        val api = TownApiClient(engine = engine, tokenStorage = InMemoryTokenStorage("tok"), serverOriginProvider = { "https://saigon.town" })
        val storeScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val discover = DiscoverStore(api, storeScope)

        discover.loadAll()
        advanceUntilIdle()
        assertEquals(2L, discover.state.value.summary.today)
        assertEquals(1, discover.state.value.events.size)
        assertEquals(1, discover.state.value.checkIns.size)
        assertEquals("Coffee", discover.state.value.communities.first().name)

        val joined = discover.toggleCommunityMembership("c1", currentlyJoined = false)
        assertTrue(joined?.joined == true)
        assertEquals(5L, discover.state.value.communities.first().memberCount)

        discover.openLocation(name = "Bitexco", locationId = "loc1")
        advanceUntilIdle()
        val detail = discover.state.value.locationDetail
        assertNotNull(detail)
        assertEquals("Great view", detail.description)
        assertEquals("https://saigon.town/media/ph1.jpg", detail.photos.first().url)
        assertEquals("Sketching", detail.activities.first().name)
        assertEquals(1, detail.posts.size)
    }

    @Test
    fun discoverSearchesUpdateOnlyTheirListsAndActivitiesSendQuery() = runTest {
        val requests = mutableListOf<Pair<String, String?>>()
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { req ->
                val path = req.url.encodedPath
                val query = req.url.parameters["q"]
                if (path == "/api/events" || path == "/api/activities" || path == "/api/communities") {
                    requests += path to query
                }
                when (path) {
                    "/api/events/summary" -> respond("""{}""", HttpStatusCode.OK, jsonHeaders)
                    "/api/events" -> when (query) {
                        "river" -> respond(
                            """{"events":[{"id":"searched-event","message":"River meetup"}],"people":[],"locations":[],"nextPeopleCursor":null}""",
                            HttpStatusCode.OK,
                            jsonHeaders,
                        )
                        "linh" -> respond(
                            """{"events":[],"people":[{"id":"searched-person","name":"Linh"}],"locations":[],"nextPeopleCursor":null}""",
                            HttpStatusCode.OK,
                            jsonHeaders,
                        )
                        else -> respond(
                            """{"events":[{"id":"initial-event","message":"Meetup"}],"people":[{"id":"initial-person","name":"An"}],"locations":[],"nextPeopleCursor":null}""",
                            HttpStatusCode.OK,
                            jsonHeaders,
                        )
                    }
                    "/api/checkins" -> respond("""{"checkIns":[]}""", HttpStatusCode.OK, jsonHeaders)
                    "/api/communities" -> respond(
                        if (query == "coffee") """{"communities":[{"id":"c2","name":"Coffee"}]}"""
                        else """{"communities":[{"id":"c1","name":"Initial"}]}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    "/api/activities" -> respond(
                        """{"activities":[{"id":"a1","name":"Morning walk"}],"nextCursor":null}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    else -> respond("""{"error":"not_found"}""", HttpStatusCode.NotFound, jsonHeaders)
                }
            }
        })
        val api = TownApiClient(engine = engine, tokenStorage = InMemoryTokenStorage("tok"), serverOriginProvider = { "https://saigon.town" })
        val storeScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val discover = DiscoverStore(api, storeScope)
        val activities = ActivityStore(api, storeScope)

        discover.loadAll()
        advanceUntilIdle()
        discover.searchEvents("river")
        advanceUntilIdle()
        assertEquals("searched-event", discover.state.value.events.single().id)
        assertEquals("initial-person", discover.state.value.people.single().id)

        discover.searchPeople("linh")
        advanceUntilIdle()
        assertEquals("searched-event", discover.state.value.events.single().id)
        assertEquals("searched-person", discover.state.value.people.single().id)

        discover.searchCommunities("coffee")
        activities.loadActivities(query = "walk")
        advanceUntilIdle()
        assertEquals("Coffee", discover.state.value.communities.single().name)
        assertEquals("a1", activities.state.value.activities.single().id)
        assertTrue(requests.any { it.first == "/api/events" && it.second == "river" })
        assertTrue(requests.any { it.first == "/api/events" && it.second == "linh" })
        assertTrue(requests.any { it.first == "/api/communities" && it.second == "coffee" })
        assertTrue(requests.any { it.first == "/api/activities" && it.second == "walk" })
    }

    @Test
    fun shopAndActivityStoresCreateMutateAndLocalize() = runTest {
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { req ->
                val path = req.url.encodedPath
                when {
                    path == "/api/products/search" -> respond(
                        """{"products":[{"id":"pr1","name":"Matcha","price":65000,"category":"drink","photoUrls":["/media/m.jpg"]}],"count":1,"nextCursor":null}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/products/pr1/orders" -> respond(
                        """{"id":"o1","productId":"pr1","productName":"Matcha","price":65000,"contactMethod":"messages","message":"2 cups","contactChatId":"ch9","resolved":false}""",
                        HttpStatusCode.Created,
                        jsonHeaders,
                    )
                    path == "/api/activities" && req.method == HttpMethod.Post -> respond(
                        """{"id":"a1","emoji":"🏃","name":"Morning Run","description":"5k park loop","locationName":"Tao Dan","interested":true,"interestedCount":1,"translations":{"status":"ready","en":{"name":"Morning Run"},"vi":{"name":"Chạy buổi sáng"}}}""",
                        HttpStatusCode.Created,
                        jsonHeaders,
                    )
                    path == "/api/activities/a1/leave" -> respond(
                        """{"id":"a1","emoji":"🏃","name":"Morning Run","description":"5k park loop","locationName":"Tao Dan","interested":false,"interestedCount":0}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    else -> respond("""{"error":"not_found"}""", HttpStatusCode.NotFound, jsonHeaders)
                }
            }
        })
        val api = TownApiClient(engine = engine, tokenStorage = InMemoryTokenStorage("tok"), serverOriginProvider = { "https://saigon.town" })
        val storeScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val shop = ShopStore(api, storeScope)
        val activities = ActivityStore(api, storeScope)

        shop.loadInitial()
        advanceUntilIdle()
        assertEquals(1, shop.state.value.products.size)
        val order = shop.placeOrder("pr1", "messages", "2 cups")
        assertEquals("ch9", order?.contactChatId)

        val createdAct = activities.createActivity(CreateActivityRequest(name = "Morning Run", locationName = "Tao Dan"))
        assertNotNull(createdAct)
        assertEquals("Chạy buổi sáng", ActivityStore.localizedName(createdAct, Language.VI))
        val left = activities.toggleInterest("a1", currentlyInterested = true)
        assertFalse(left?.interested == true)
    }

    @Test
    fun composerStorePollsGeneratingAvatarAndSubmitsPostIntoFeedStore() = runTest {
        var avatarPolls = 0
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { req ->
                val path = req.url.encodedPath
                when {
                    path == "/api/avatars" && req.method == HttpMethod.Post -> respond(
                        """{"id":"av1","prompt":"neon cyber","status":"generating"}""",
                        HttpStatusCode.Created,
                        jsonHeaders,
                    )
                    path == "/api/avatars/av1" -> {
                        avatarPolls += 1
                        if (avatarPolls < 2) {
                            respond("""{"id":"av1","prompt":"neon cyber","status":"generating"}""", HttpStatusCode.OK, jsonHeaders)
                        } else {
                            respond("""{"id":"av1","prompt":"neon cyber","status":"ready","imageUrl":"/media/av1.png"}""", HttpStatusCode.OK, jsonHeaders)
                        }
                    }
                    path == "/api/posts" && req.method == HttpMethod.Post -> respond(
                        """{"id":"p_new","authorName":"An","message":"Fresh post","location":"D1","photoUrl":"/media/p_new.jpg"}""",
                        HttpStatusCode.Created,
                        jsonHeaders,
                    )
                    else -> respond("""{"error":"not_found"}""", HttpStatusCode.NotFound, jsonHeaders)
                }
            }
        })
        val api = TownApiClient(engine = engine, tokenStorage = InMemoryTokenStorage("tok"), serverOriginProvider = { "https://saigon.town" })
        val storeScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val feed = FeedStore(api, storeScope)
        val composer = ComposerStore(api, feed, storeScope, pollIntervalMs = 1L)

        val avatar = composer.createAvatar("neon cyber")
        assertEquals("generating", avatar?.status)
        advanceUntilIdle()
        val ready = composer.state.value.avatars.firstOrNull { it.id == "av1" }
        assertEquals("ready", ready?.status)
        assertEquals("av1", composer.state.value.selectedAvatarId)

        val createdPost = composer.submitPost(mapOf("message" to "Fresh post", "location" to "D1"), listOf(byteArrayOf(1, 2, 3)))
        assertEquals("p_new", createdPost?.id)
        assertEquals("p_new", feed.state.value.posts.first().id)
        composer.stopPolling()
    }

    @Test
    fun messagesStoreStreamsEventsHandlesReactionsAndDeletesMessages() = runTest {
        var streamCalls = 0
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { req ->
                val path = req.url.encodedPath
                when {
                    path == "/api/chats" && req.url.parameters["limit"] == "1" -> respond(
                        """{"chats":[],"unreadCount":1}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/chats/stream" -> {
                        streamCalls += 1
                        if (streamCalls == 1) {
                            respond(
                                content = ByteReadChannel(
                                    "data: {\"type\":\"message\",\"unreadCount\":2,\"chat\":{\"id\":\"ch1\",\"type\":\"direct\",\"name\":\"Minh\",\"lastMessageAt\":50,\"unreadCount\":1},\"message\":{\"id\":\"m2\",\"chatId\":\"ch1\",\"userId\":\"u2\",\"authorName\":\"Minh\",\"text\":\"Ping\",\"createdAt\":50}}\n\n",
                                ),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.EventStream.toString()),
                            )
                        } else {
                            respond("""{"error":"unauthorized"}""", HttpStatusCode.Unauthorized, jsonHeaders)
                        }
                    }
                    path == "/api/chats/ch1" -> respond(
                        """{"id":"ch1","type":"direct","name":"Minh","defaultReactions":["👍","❤️"]}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/chats/ch1/messages" && req.method == HttpMethod.Get -> respond(
                        """{"messages":[{"id":"m1","chatId":"ch1","userId":"u1","authorName":"An","text":"Hi","createdAt":10}],"nextCursor":null}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/chats/ch1/read" -> respond("""{"ok":true,"unreadCount":0}""", HttpStatusCode.OK, jsonHeaders)
                    path == "/api/chats/ch1/messages/m1/reactions" && req.method == HttpMethod.Post -> respond(
                        """{"reactions":[{"emoji":"👍","count":1,"mine":true}]}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/chats/ch1/messages/m1" && req.method == HttpMethod.Delete -> respond(
                        """{"ok":true}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    else -> respond("""{"error":"not_found"}""", HttpStatusCode.NotFound, jsonHeaders)
                }
            }
        })
        val api = TownApiClient(engine = engine, tokenStorage = InMemoryTokenStorage("tok"), serverOriginProvider = { "https://saigon.town" })
        val storeScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val messages = MessagesStore(api, storeScope)

        messages.openChat("ch1")
        advanceUntilIdle()
        assertEquals("ch1", messages.state.value.activeChat?.id)
        assertEquals(1, messages.state.value.messages.size)

        messages.connectStream()
        advanceUntilIdle()
        assertEquals(2, messages.state.value.messages.size)
        assertEquals("m2", messages.state.value.messages.first().id)

        messages.toggleReaction("ch1", "m1", "👍", currentlyMine = false)
        assertTrue(messages.state.value.messages.first { it.id == "m1" }.reactions.first().mine)

        assertTrue(messages.deleteMessage("ch1", "m1"))
        assertNull(messages.state.value.messages.firstOrNull { it.id == "m1" })
        messages.disconnectStream()
    }

    @Test
    fun profileStatsAndAdminStoresLoadAndMutate() = runTest {
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { req ->
                val path = req.url.encodedPath
                when {
                    path == "/api/me/orders" -> respond(
                        """{"orders":[{"id":"ord1","productName":"Hat","price":100000,"resolved":false}],"nextCursor":null,"openCount":1}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/orders/ord1" -> respond(
                        """{"id":"ord1","productName":"Hat","price":100000,"resolved":true}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/features" -> respond(
                        """{"features":[{"id":"f1","text":"Dark mode","voteCount":3,"voted":false}]}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/features/f1/vote" -> respond(
                        """{"id":"f1","text":"Dark mode","voteCount":4,"voted":true}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/stats" -> respond(
                        """{"timezone":"UTC+7","totalUsers":42,"totalPosts":100,"days":[{"day":"2026-11-17","userVisits":10}]}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/admin/me" -> respond("""{"admin":true}""", HttpStatusCode.OK, jsonHeaders)
                    path == "/api/admin/users" -> respond(
                        """{"users":[{"id":"u2","name":"Minh","paused":false}],"nextCursor":null}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    path == "/api/admin/users/u2/pause" -> respond("""{"id":"u2","paused":true}""", HttpStatusCode.OK, jsonHeaders)
                    else -> respond("""{"error":"not_found"}""", HttpStatusCode.NotFound, jsonHeaders)
                }
            }
        })
        val api = TownApiClient(engine = engine, tokenStorage = InMemoryTokenStorage("tok"), serverOriginProvider = { "https://saigon.town" })
        val storeScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val profile = ProfileStore(api, storeScope)
        val stats = StatsStore(api, storeScope)
        val admin = AdminStore(api, storeScope)

        profile.loadMyOrders()
        profile.loadFeatures()
        advanceUntilIdle()
        assertEquals(1L, profile.state.value.openOrdersCount)
        assertTrue(profile.resolveOrder("ord1", true))
        assertEquals(0L, profile.state.value.openOrdersCount)
        assertTrue(profile.voteFeature("f1")?.voted == true)

        stats.load()
        advanceUntilIdle()
        assertEquals(42L, stats.state.value.stats?.totalUsers)

        assertTrue(admin.verifyAdmin())
        admin.loadUsers()
        advanceUntilIdle()
        assertTrue(admin.setUserPaused("u2", true))
        assertTrue(admin.state.value.users.first().paused)
    }

    @Test
    fun composerStoreHandlesCommunitySuggestionAndCreation() = runTest {
        var failSuggest = false
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { req ->
                val path = req.url.encodedPath
                when {
                    path == "/api/communities/suggest" -> if (failSuggest) {
                        respond("""{"error":"unavailable"}""", HttpStatusCode.ServiceUnavailable, jsonHeaders)
                    } else {
                        respond("""{"emoji":"☕","name":"Specialty Coffee","communityId":null}""", HttpStatusCode.OK, jsonHeaders)
                    }
                    path == "/api/communities" && req.method == HttpMethod.Post -> respond(
                        """{"id":"c9","name":"Specialty Coffee","emoji":"☕","memberCount":1,"joined":true}""",
                        HttpStatusCode.Created,
                        jsonHeaders,
                    )
                    else -> respond("""{"error":"not_found"}""", HttpStatusCode.NotFound, jsonHeaders)
                }
            }
        })
        val api = TownApiClient(engine = engine, tokenStorage = InMemoryTokenStorage("tok"), serverOriginProvider = { "https://saigon.town" })
        val storeScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val feed = FeedStore(api, storeScope)
        val composer = ComposerStore(api, feed, storeScope)

        val suggested = composer.suggestCommunity("Great espresso bar in District 1")
        assertNotNull(suggested)
        assertEquals("Specialty Coffee", composer.state.value.communitySuggestion?.name)
        assertNull(composer.state.value.communitySuggestion?.communityId)
        assertNull(composer.state.value.error)

        failSuggest = true
        assertNull(composer.suggestCommunity("Another long message text"))
        assertNull(composer.state.value.error)

        val created = composer.createCommunity("Specialty Coffee", "☕")
        assertNotNull(created)
        assertEquals("c9", created.id)
        assertEquals("c9", composer.state.value.communitySuggestion?.communityId)

        composer.clearCommunitySuggestion()
        assertNull(composer.state.value.communitySuggestion)
    }
}
