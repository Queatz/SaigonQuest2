package quest.saigon.android

import android.content.Intent
import quest.saigon.AppRoute
import quest.saigon.NotificationCopy
import quest.saigon.matchAppRoute

object RoutePaths {
    fun toPath(route: AppRoute): String = when (route) {
        AppRoute.Home -> "/"
        AppRoute.Privacy -> "/privacy"
        AppRoute.Csae -> "/CSAE"
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
        val extracted = when {
            text.startsWith("/") -> text
            text.contains("://") -> {
                val afterScheme = text.substring(text.indexOf("://") + 3)
                val slashIdx = afterScheme.indexOf('/')
                if (slashIdx >= 0) afterScheme.substring(slashIdx) else "/"
            }
            else -> return null
        }
        val clean = extracted.substringBefore('?').substringBefore('#')
        return clean.ifBlank { "/" }
    }

    fun resolveRouteFromValues(
        saigonRouteUrl: String? = null,
        fcmUrl: String? = null,
        dataUri: String? = null,
        fcmType: String? = null,
        fcmPostId: String? = null,
        fcmChatId: String? = null,
        fcmActorId: String? = null,
    ): AppRoute? {
        val explicitPath = parsePathFromUriString(saigonRouteUrl)
            ?: parsePathFromUriString(fcmUrl)
            ?: parsePathFromUriString(dataUri)
        if (explicitPath != null) {
            return matchAppRoute(explicitPath)
        }
        val type = fcmType?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val notificationPath = NotificationCopy.path(
            type = type,
            postId = fcmPostId.orEmpty().trim(),
            chatId = fcmChatId.orEmpty().trim(),
            actorId = fcmActorId.orEmpty().trim(),
        )
        val matched = matchAppRoute(notificationPath)
        return matched.takeUnless { it is AppRoute.NotFound }
    }

    fun resolveRouteFromIntent(intent: Intent?): AppRoute? {
        if (intent == null) return null
        return resolveRouteFromValues(
            saigonRouteUrl = intent.getStringExtra(EXTRA_ROUTE_URL),
            fcmUrl = intent.getStringExtra(EXTRA_FCM_URL),
            dataUri = intent.dataString,
            fcmType = intent.getStringExtra(EXTRA_FCM_TYPE),
            fcmPostId = intent.getStringExtra(EXTRA_FCM_POST_ID),
            fcmChatId = intent.getStringExtra(EXTRA_FCM_CHAT_ID),
            fcmActorId = intent.getStringExtra(EXTRA_FCM_ACTOR_ID),
        )
    }

    const val EXTRA_ROUTE_URL = "saigon_route_url"
    const val EXTRA_FCM_URL = "url"
    const val EXTRA_FCM_TYPE = "type"
    const val EXTRA_FCM_POST_ID = "postId"
    const val EXTRA_FCM_CHAT_ID = "chatId"
    const val EXTRA_FCM_ACTOR_ID = "actorId"
}
