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
    }

    @Test
    fun unknownPathIsNotFound() {
        val route = matchAppRoute("/about")
        assertIs<AppRoute.NotFound>(route)
        assertEquals("/about", route.path)
    }
}
