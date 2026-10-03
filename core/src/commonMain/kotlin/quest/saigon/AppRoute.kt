package quest.saigon

sealed class AppRoute {
    data object Home : AppRoute()
    data object NewPost : AppRoute()
    data object Profile : AppRoute()
    data object MyPosts : AppRoute()
    data object Stats : AppRoute()
    data object Admin : AppRoute()
    data class Post(val id: String) : AppRoute()
    data class NotFound(val path: String) : AppRoute()
}

fun matchAppRoute(path: String): AppRoute {
    val normalized = path.trim().ifEmpty { "/" }.let { value ->
        if (value != "/" && value.endsWith("/")) value.dropLast(1) else value
    }
    val post = POST_PATH.matchEntire(normalized)
    return when {
        normalized == "/" || normalized.isEmpty() -> AppRoute.Home
        normalized == "/new" -> AppRoute.NewPost
        normalized == "/profile" -> AppRoute.Profile
        normalized == "/profile/posts" -> AppRoute.MyPosts
        normalized == "/stats" -> AppRoute.Stats
        normalized == "/admin" -> AppRoute.Admin
        post != null -> AppRoute.Post(post.groupValues[1])
        else -> AppRoute.NotFound(path)
    }
}

private val POST_PATH = Regex("^/p/([A-Za-z0-9_-]+)$")
