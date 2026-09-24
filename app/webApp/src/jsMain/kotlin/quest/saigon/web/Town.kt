package quest.saigon.web

import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.resolveLanguage
import web.dom.document
import web.window.window

internal data class Me(
    val id: String,
    val name: String,
    val phone: String,
    val location: String,
    val language: String,
    val hasProfilePhoto: Boolean,
    val photoUrl: String?,
)

internal data class PostCard(
    val id: String,
    val authorName: String,
    val message: String,
    val location: String,
    val photoUrl: String,
    val avatarUrl: String?,
    val createdAt: Double,
)

internal object Town {
    var me: Me? = null
    var language: Language = Language.EN
    var returnTo: String = "/"
    var pending: PostCard? = null

    fun t(key: String, values: Map<String, String> = emptyMap()): String =
        Copy.text(language, key, values)

    fun applyLanguage() {
        val preference = me?.language ?: storedLanguage()
        language = resolveLanguage(preference, browserLanguage())
        document.asDynamic().documentElement.lang = if (language == Language.VI) "vi" else "en"
        document.asDynamic().title = t("appName")
    }

    fun rememberLanguage(preference: String) {
        window.asDynamic().localStorage.setItem("sq_lang", preference)
    }
}

internal fun meFrom(node: dynamic): Me = Me(
    id = node.id as String,
    name = node.name as String,
    phone = (node.phone as? String).orEmpty(),
    location = (node.location as? String).orEmpty(),
    language = (node.language as? String) ?: "system",
    hasProfilePhoto = node.hasProfilePhoto == true,
    photoUrl = node.photoUrl as? String,
)

internal fun postFrom(node: dynamic): PostCard = PostCard(
    id = node.id as String,
    authorName = (node.authorName as? String).orEmpty(),
    message = (node.message as? String).orEmpty(),
    location = (node.location as? String).orEmpty(),
    photoUrl = (node.photoUrl as? String).orEmpty(),
    avatarUrl = node.avatarUrl as? String,
    createdAt = num(node.createdAt),
)

internal fun go(router: HistoryRouter, path: String) {
    Town.returnTo = router.currentPath()
    router.navigate(path)
}

internal fun back(router: HistoryRouter) {
    val target = Town.returnTo.takeIf { it.isNotBlank() && it != router.currentPath() } ?: "/"
    Town.returnTo = "/"
    router.navigate(target)
}

internal fun refresh(router: HistoryRouter) {
    router.navigate(router.currentPath(), replace = true)
}

private fun storedLanguage(): String =
    window.asDynamic().localStorage.getItem("sq_lang") as? String ?: "system"

private fun browserLanguage(): String =
    window.asDynamic().navigator.language as? String ?: "en"
