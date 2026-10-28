package quest.saigon

sealed class AppRoute {
    data object Home : AppRoute()
    data object NewPost : AppRoute()
    data object Profile : AppRoute()
    data object MyPosts : AppRoute()
    data object MyCommunities : AppRoute()
    data object MyMusic : AppRoute()
    data object FeatureSuggestions : AppRoute()
    data object Stats : AppRoute()
    data object Admin : AppRoute()
    data object Discover : AppRoute()
    data object Messages : AppRoute()
    data class Chat(val id: String) : AppRoute()
    data class Post(val id: String) : AppRoute()
    data class Community(val id: String) : AppRoute()
    data class NotFound(val path: String) : AppRoute()
}

fun matchAppRoute(path: String): AppRoute {
    val normalized = path.trim().ifEmpty { "/" }.let { value ->
        if (value != "/" && value.endsWith("/")) value.dropLast(1) else value
    }
    val post = POST_PATH.matchEntire(normalized)
    val community = COMMUNITY_PATH.matchEntire(normalized)
    val chat = CHAT_PATH.matchEntire(normalized)
    return when {
        normalized == "/" || normalized.isEmpty() -> AppRoute.Home
        normalized == "/new" -> AppRoute.NewPost
        normalized == "/profile" -> AppRoute.Profile
        normalized == "/profile/posts" -> AppRoute.MyPosts
        normalized == "/profile/communities" -> AppRoute.MyCommunities
        normalized == "/profile/music" -> AppRoute.MyMusic
        normalized == "/profile/features" -> AppRoute.FeatureSuggestions
        normalized == "/stats" -> AppRoute.Stats
        normalized == "/admin" -> AppRoute.Admin
        normalized == "/discover" -> AppRoute.Discover
        normalized == "/messages" -> AppRoute.Messages
        post != null -> AppRoute.Post(post.groupValues[1])
        community != null -> AppRoute.Community(community.groupValues[1])
        chat != null -> AppRoute.Chat(chat.groupValues[1])
        else -> AppRoute.NotFound(path)
    }
}

private val POST_PATH = Regex("^/p/([A-Za-z0-9_-]+)$")
private val COMMUNITY_PATH = Regex("^/c/([A-Za-z0-9_-]+)$")
private val CHAT_PATH = Regex("^/messages/([A-Za-z0-9_-]+)$")
