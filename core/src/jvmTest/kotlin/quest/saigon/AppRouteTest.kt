package quest.saigon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppRouteTest {
    @Test
    fun homePaths() {
        assertEquals(AppRoute.Home, matchAppRoute("/"))
        assertEquals(AppRoute.Home, matchAppRoute(""))
        assertEquals(AppRoute.Home, matchAppRoute(" / "))
    }

    @Test
    fun publicLegalPaths() {
        assertEquals(AppRoute.Privacy, matchAppRoute("/privacy"))
        assertEquals(AppRoute.Privacy, matchAppRoute("/privacy/"))
        assertEquals(AppRoute.Csae, matchAppRoute("/CSAE"))
        assertEquals(AppRoute.Csae, matchAppRoute("/CSAE/"))
        assertEquals(AppRoute.Csae, matchAppRoute("/csae"))
        assertEquals(AppRoute.Csae, matchAppRoute("/csae/"))
    }

    @Test
    fun composerAndProfilePaths() {
        assertEquals(AppRoute.NewPost, matchAppRoute("/new"))
        assertEquals(AppRoute.NewPost, matchAppRoute("/new/"))
        assertEquals(AppRoute.Profile, matchAppRoute("/profile"))
        assertEquals(AppRoute.MyPosts, matchAppRoute("/profile/posts"))
        assertEquals(AppRoute.MyPosts, matchAppRoute("/profile/posts/"))
        assertEquals(AppRoute.MyCommunities, matchAppRoute("/profile/communities"))
        assertEquals(AppRoute.MyCommunities, matchAppRoute("/profile/communities/"))
        assertEquals(AppRoute.MyMusic, matchAppRoute("/profile/music"))
        assertEquals(AppRoute.MyMusic, matchAppRoute("/profile/music/"))
        assertEquals(AppRoute.MyProducts, matchAppRoute("/profile/products"))
        assertEquals(AppRoute.MyProducts, matchAppRoute("/profile/products/"))
        assertEquals(AppRoute.MyActivities, matchAppRoute("/profile/activities"))
        assertEquals(AppRoute.MyActivities, matchAppRoute("/profile/activities/"))
        assertEquals(AppRoute.MyOrders, matchAppRoute("/profile/orders"))
        assertEquals(AppRoute.MyOrders, matchAppRoute("/profile/orders/"))
        assertEquals(AppRoute.FeatureSuggestions, matchAppRoute("/profile/features"))
        assertEquals(AppRoute.FeatureSuggestions, matchAppRoute("/profile/features/"))
        assertEquals(AppRoute.Stats, matchAppRoute("/stats"))
        assertEquals(AppRoute.Stats, matchAppRoute("/stats/"))
        assertEquals(AppRoute.Admin, matchAppRoute("/admin"))
        assertEquals(AppRoute.Admin, matchAppRoute("/admin/"))
        assertEquals(AppRoute.Discover, matchAppRoute("/discover"))
        assertEquals(AppRoute.Discover, matchAppRoute("/discover/"))
        assertEquals(AppRoute.Messages, matchAppRoute("/messages"))
        assertEquals(AppRoute.Messages, matchAppRoute("/messages/"))
        assertEquals(AppRoute.Chat("abc123"), matchAppRoute("/messages/abc123"))
        assertEquals(AppRoute.Chat("abc123"), matchAppRoute("/messages/abc123/"))
    }

    @Test
    fun postPathRevealsAFeedItem() {
        assertEquals(AppRoute.Post("abc123"), matchAppRoute("/p/abc123"))
        assertEquals(AppRoute.Post("abc123"), matchAppRoute("/p/abc123/"))
    }

    @Test
    fun communityPathOpensThatFeed() {
        assertEquals(AppRoute.Community("abc123"), matchAppRoute("/c/abc123"))
        assertEquals(AppRoute.Community("abc123"), matchAppRoute("/c/abc123/"))
    }

    @Test
    fun sharedEntityPathsOpenTheirSheets() {
        assertEquals(AppRoute.User("abc123"), matchAppRoute("/u/abc123"))
        assertEquals(AppRoute.User("abc123"), matchAppRoute("/u/abc123/"))
        assertEquals(AppRoute.Product("abc123"), matchAppRoute("/product/abc123"))
        assertEquals(AppRoute.Product("abc123"), matchAppRoute("/product/abc123/"))
        assertEquals(AppRoute.Activity("abc123"), matchAppRoute("/activity/abc123"))
        assertEquals(AppRoute.Activity("abc123"), matchAppRoute("/activity/abc123/"))
        assertEquals(AppRoute.Location("abc123"), matchAppRoute("/l/abc123"))
        assertEquals(AppRoute.Location("abc123"), matchAppRoute("/l/abc123/"))
    }

    @Test
    fun shopPathOpensTheShop() {
        assertEquals(AppRoute.Shop, matchAppRoute("/shop"))
        assertEquals(AppRoute.Shop, matchAppRoute("/shop/"))
    }

    @Test
    fun unknownPathIsNotFound() {
        val route = matchAppRoute("/about")
        assertIs<AppRoute.NotFound>(route)
        assertEquals("/about", route.path)
        assertIs<AppRoute.NotFound>(matchAppRoute("/u/"))
        assertIs<AppRoute.NotFound>(matchAppRoute("/product/name.with.dot"))
    }
}
