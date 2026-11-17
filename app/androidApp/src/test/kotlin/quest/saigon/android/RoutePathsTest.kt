package quest.saigon.android

import quest.saigon.AppRoute
import quest.saigon.NotificationCopy
import quest.saigon.matchAppRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoutePathsTest {
    @Test
    fun roundTripsAllAppRoutesAndAuthGuards() {
        val routes = listOf(
            AppRoute.Home,
            AppRoute.NewPost,
            AppRoute.Profile,
            AppRoute.MyPosts,
            AppRoute.MyCommunities,
            AppRoute.MyMusic,
            AppRoute.MyProducts,
            AppRoute.MyActivities,
            AppRoute.MyOrders,
            AppRoute.FeatureSuggestions,
            AppRoute.Stats,
            AppRoute.Admin,
            AppRoute.Discover,
            AppRoute.Shop,
            AppRoute.Messages,
            AppRoute.Chat("c1"),
            AppRoute.Post("p1"),
            AppRoute.Community("cm1"),
            AppRoute.User("u1"),
            AppRoute.Product("pr1"),
            AppRoute.Activity("a1"),
            AppRoute.Location("loc1"),
        )
        routes.forEach { route ->
            val path = RoutePaths.toPath(route)
            assertEquals(route, matchAppRoute(path))
        }

        assertTrue(RoutePaths.requiresAuth(AppRoute.NewPost))
        assertTrue(RoutePaths.requiresAuth(AppRoute.Profile))
        assertTrue(RoutePaths.requiresAuth(AppRoute.Messages))
        assertTrue(RoutePaths.requiresAuth(AppRoute.Chat("c1")))
        assertTrue(RoutePaths.requiresAuth(AppRoute.Admin))
        assertFalse(RoutePaths.requiresAuth(AppRoute.Home))
        assertFalse(RoutePaths.requiresAuth(AppRoute.Discover))
        assertFalse(RoutePaths.requiresAuth(AppRoute.Shop))
        assertFalse(RoutePaths.requiresAuth(AppRoute.Post("p1")))
    }

    @Test
    fun parsesDeepLinksAndNotificationPaths() {
        assertEquals("/p/abc123", RoutePaths.parsePathFromUriString("https://saigon.town/p/abc123"))
        assertEquals("/messages/ch9", RoutePaths.parsePathFromUriString("https://saigon.town/messages/ch9"))
        assertEquals("/", RoutePaths.parsePathFromUriString("https://saigon.town"))
        assertEquals(
            AppRoute.Post("p7"),
            matchAppRoute(NotificationCopy.path("post_comment", postId = "p7", chatId = "")),
        )
        assertEquals(
            AppRoute.Chat("c9"),
            matchAppRoute(NotificationCopy.path("chat_message", postId = "", chatId = "c9")),
        )
    }
}
