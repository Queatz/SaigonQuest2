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
    fun composerAndProfilePaths() {
        assertEquals(AppRoute.NewPost, matchAppRoute("/new"))
        assertEquals(AppRoute.NewPost, matchAppRoute("/new/"))
        assertEquals(AppRoute.Profile, matchAppRoute("/profile"))
        assertEquals(AppRoute.MyPosts, matchAppRoute("/profile/posts"))
        assertEquals(AppRoute.MyPosts, matchAppRoute("/profile/posts/"))
        assertEquals(AppRoute.Stats, matchAppRoute("/stats"))
        assertEquals(AppRoute.Stats, matchAppRoute("/stats/"))
        assertEquals(AppRoute.Admin, matchAppRoute("/admin"))
        assertEquals(AppRoute.Admin, matchAppRoute("/admin/"))
    }

    @Test
    fun postPathRevealsAFeedItem() {
        assertEquals(AppRoute.Post("abc123"), matchAppRoute("/p/abc123"))
        assertEquals(AppRoute.Post("abc123"), matchAppRoute("/p/abc123/"))
    }

    @Test
    fun unknownPathIsNotFound() {
        val route = matchAppRoute("/about")
        assertIs<AppRoute.NotFound>(route)
        assertEquals("/about", route.path)
    }
}
