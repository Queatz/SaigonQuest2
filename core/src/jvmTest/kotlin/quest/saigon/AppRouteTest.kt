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
    fun aboutPathNormalizesTrailingSlash() {
        assertEquals(AppRoute.About, matchAppRoute("/about"))
        assertEquals(AppRoute.About, matchAppRoute("/about/"))
    }

    @Test
    fun unknownPathIsNotFound() {
        val route = matchAppRoute("/quests/42")
        assertIs<AppRoute.NotFound>(route)
        assertEquals("/quests/42", route.path)
    }
}
