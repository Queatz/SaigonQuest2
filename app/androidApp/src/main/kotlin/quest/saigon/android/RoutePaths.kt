package quest.saigon.android

import android.content.Intent
import quest.saigon.AppRoute
import quest.saigon.matchAppRoute

object RoutePaths {
    fun toPath(route: AppRoute): String = when (route) {
        AppRoute.Home -> "/"
        AppRoute.NewPost -> "/new"
        AppRoute.Profile -> "/profile"
        AppRoute.MyPosts -> "/profile/posts"
        AppRoute.MyCommunities -> "/profile/communities"
        AppRoute.MyMusic -> "/profile/music"
        AppRoute.MyProducts -> "/profile/products"
        AppRoute.MyActivities -> "/profile/activities"
        AppRoute.MyOrders -> "/profile/orders"
        AppRoute.FeatureSuggestions -> "/profile/features"
        AppRoute.Stats -> "/stats"
        AppRoute.Admin -> "/admin"
        AppRoute.Discover -> "/discover"
        AppRoute.Shop -> "/shop"
        AppRoute.Messages -> "/messages"
        is AppRoute.Chat -> "/messages/${route.id}"
        is AppRoute.Post -> "/p/${route.id}"
        is AppRoute.Community -> "/c/${route.id}"
        is AppRoute.User -> "/u/${route.id}"
        is AppRoute.Product -> "/product/${route.id}"
        is AppRoute.Activity -> "/activity/${route.id}"
        is AppRoute.Location -> "/l/${route.id}"
        is AppRoute.NotFound -> route.path
    }

    fun requiresAuth(route: AppRoute): Boolean = when (route) {
        AppRoute.NewPost,
        AppRoute.Profile,
        AppRoute.MyPosts,
        AppRoute.MyCommunities,
        AppRoute.MyMusic,
        AppRoute.MyProducts,
        AppRoute.MyActivities,
        AppRoute.MyOrders,
        AppRoute.FeatureSuggestions,
        AppRoute.Admin,
        AppRoute.Messages,
        is AppRoute.Chat -> true
        else -> false
    }

    fun parsePathFromUriString(raw: String?): String? {
        val text = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (text.startsWith("/")) return text
        val schemeIdx = text.indexOf("://")
        if (schemeIdx > 0) {
            val afterScheme = text.substring(schemeIdx + 3)
            val slashIdx = afterScheme.indexOf('/')
            return if (slashIdx >= 0) afterScheme.substring(slashIdx) else "/"
        }
        return null
    }

    fun resolveRouteFromIntent(intent: Intent?): AppRoute? {
        if (intent == null) return null
        val extraUrl = intent.getStringExtra(EXTRA_ROUTE_URL)
        val path = parsePathFromUriString(extraUrl)
            ?: parsePathFromUriString(intent.dataString)
            ?: return null
        return matchAppRoute(path)
    }

    const val EXTRA_ROUTE_URL = "saigon_route_url"
}
