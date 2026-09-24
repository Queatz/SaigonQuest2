package quest.saigon

sealed class AppRoute {
    data object Home : AppRoute()
    data object About : AppRoute()
    data class NotFound(val path: String) : AppRoute()
}

fun matchAppRoute(path: String): AppRoute {
    val normalized = path.trim().ifEmpty { "/" }.let { value ->
        if (value != "/" && value.endsWith("/")) value.dropLast(1) else value
    }
    return when (normalized) {
        "/", "" -> AppRoute.Home
        "/about" -> AppRoute.About
        else -> AppRoute.NotFound(path)
    }
}
