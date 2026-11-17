package quest.saigon.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import quest.saigon.android.ui.shop.ShopScreen
import quest.saigon.android.ui.stats.StatsScreen
import quest.saigon.shared.TownSharedClient
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
                )
            }
        }
        composeRule.onNodeWithText("Linh").assertIsDisplayed()
        composeRule.onNodeWithText("District 1").assertIsDisplayed()
        composeRule.onNodeWithText(Copy.text(Language.EN, "messageUser", mapOf("name" to "Linh"))).assertIsDisplayed()
        composeRule.onNodeWithText(Copy.text(Language.EN, "profile")).assertIsDisplayed()
    }

    @Test
    fun discoverShopMessagesAndStatsScreensRenderSharedStoreState() {
        val shared = fakeClient()
        composeRule.setContent {
            SaigonTheme {
                DiscoverScreen(shared = shared, onNavigate = {}, onRequireAuth = {}, onBack = {})
            }
        }
        composeRule.onNodeWithText(Copy.text(Language.EN, "discover")).assertIsDisplayed()
        composeRule.onNodeWithText(Copy.text(Language.EN, "communities")).assertIsDisplayed()

        composeRule.setContent {
            SaigonTheme {
                ShopScreen(shared = shared, onNavigate = {}, onRequireAuth = {}, onBack = {})
            }
        }
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
}
