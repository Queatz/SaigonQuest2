package quest.saigon.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.android.theme.SaigonTheme
import quest.saigon.android.ui.components.MiniProfileDialog
import quest.saigon.android.ui.discover.DiscoverScreen
import quest.saigon.android.ui.messages.MessagesListScreen
import quest.saigon.android.ui.stats.StatsScreen
import quest.saigon.android.ui.wants.WantsOverlayScreen
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.CommunityDto
import quest.saigon.shared.platform.InMemoryTokenStorage

class SaigonComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun fakeClient(): TownSharedClient {
        val engine = MockEngine { req ->
            when (req.url.encodedPath) {
                "/api/events/summary" -> respond("""{"today":1,"tomorrow":0,"week":2}""", HttpStatusCode.OK, jsonHeaders)
                "/api/events" -> respond("""{"events":[],"locations":[],"people":[]}""", HttpStatusCode.OK, jsonHeaders)
                "/api/checkins" -> respond("""{"checkIns":[]}""", HttpStatusCode.OK, jsonHeaders)
                "/api/communities" -> respond("""{"communities":[]}""", HttpStatusCode.OK, jsonHeaders)
                "/api/activities" -> respond("""{"activities":[]}""", HttpStatusCode.OK, jsonHeaders)
                "/api/products/search" -> respond("""{"products":[{"id":"pr1","name":"Ca phe sua da","price":35000,"photoUrls":[]}],"count":1}""", HttpStatusCode.OK, jsonHeaders)
                "/api/chats" -> respond("""{"chats":[{"id":"c1","type":"direct","name":"Minh","lastMessageText":"Hello","unreadCount":1}],"unreadCount":1}""", HttpStatusCode.OK, jsonHeaders)
                "/api/stats" -> respond("""{"timezone":"UTC+7","totalUsers":12,"totalPosts":34,"days":[]}""", HttpStatusCode.OK, jsonHeaders)
                "/api/wants" -> respond("""{"wants":[{"id":"w1","name":"Go hiking","photoUrl":"/media/wants/hike.jpg","memberCount":2,"joined":false,"members":[{"id":"u1","name":"Linh"}]}]}""", HttpStatusCode.OK, jsonHeaders)
                "/api/wants/w1/join" -> respond("""{"id":"w1","name":"Go hiking","photoUrl":"/media/wants/hike.jpg","memberCount":3,"joined":true,"members":[{"id":"u1","name":"Linh"}]}""", HttpStatusCode.OK, jsonHeaders)
                else -> respond("""{"ok":true}""", HttpStatusCode.OK, jsonHeaders)
            }
        }
        return TownSharedClient(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            engine = engine,
            tokenStorage = InMemoryTokenStorage("tok"),
            serverOriginProvider = { "https://saigon.town" },
        )
    }

    @Test
    fun miniProfileDialogDisplaysUserDetailsAndActions() {
        composeRule.setContent {
            SaigonTheme {
                MiniProfileDialog(
                    name = "Linh",
                    photoUrl = null,
                    location = "District 1",
                    intro = "Coffee lover",
                    language = Language.EN,
                    onDismiss = {},
                    onOpenProfile = {},
                    onMessage = {},
                    communities = listOf(CommunityDto(id = "c1", name = "Board games", emoji = "🎲")),
                    onOpenCommunity = {},
                )
            }
        }
        composeRule.onNodeWithText("Linh").assertIsDisplayed()
        composeRule.onNodeWithText("District 1").assertIsDisplayed()
        composeRule.onNodeWithText("🎲 Board games").assertIsDisplayed()
        composeRule.onNodeWithText(Copy.text(Language.EN, "messageUser", mapOf("name" to "Linh"))).assertIsDisplayed()
        composeRule.onNodeWithText(Copy.text(Language.EN, "profile")).assertIsDisplayed()
    }

    @Test
    fun discoverLearnMessagesAndStatsScreensRenderSharedStoreState() {
        val shared = fakeClient()
        composeRule.setContent {
            SaigonTheme {
                DiscoverScreen(shared = shared, onNavigate = {}, onRequireAuth = {}, onBack = {})
            }
        }
        composeRule.onNodeWithText(Copy.text(Language.EN, "discover")).assertIsDisplayed()
        composeRule.onNodeWithText(Copy.text(Language.EN, "communities")).assertIsDisplayed()
        composeRule.onNodeWithText(Copy.text(Language.EN, "lessons")).performClick()
        composeRule.onNodeWithText("Ca phe sua da").assertIsDisplayed()

        composeRule.setContent {
            SaigonTheme {
                MessagesListScreen(shared = shared, onNavigate = {}, onBack = {})
            }
        }
        composeRule.onNodeWithText("Minh").assertIsDisplayed()

        composeRule.setContent {
            SaigonTheme {
                StatsScreen(shared = shared, onBack = {})
            }
        }
        composeRule.onNodeWithText("12").assertIsDisplayed()
    }

    @Test
    fun wantsOverlayScreenLoadsWantsAndSupportsJoining() {
        val shared = fakeClient()
        composeRule.setContent {
            SaigonTheme {
                WantsOverlayScreen(
                    shared = shared,
                    explodeOnEnter = false,
                    onRequireAuth = {},
                    onOpenWant = {},
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(2_200L)
        composeRule.onNodeWithText("Go hiking").assertIsDisplayed()
        composeRule.onNodeWithText(Copy.text(Language.EN, "wantJoin")).assertIsDisplayed()
    }
}
