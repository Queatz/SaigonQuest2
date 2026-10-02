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
    val intro: String,
    val language: String,
    val hasProfilePhoto: Boolean,
    val photoUrl: String?,
)

internal data class CommentCard(
    val id: String,
    val userId: String = "",
    val authorName: String,
    val authorPhotoUrl: String?,
    val authorLocation: String = "",
    val authorIntro: String = "",
    val text: String,
    val createdAt: Long,
    val replyToAuthorName: String?,
    val replyToText: String?,
)

internal data class PostCard(
    val id: String,
    val authorId: String = "",
    val authorName: String,
    val authorPhotoUrl: String?,
    val authorLocation: String,
    val authorIntro: String,
    val message: String,
    val location: String,
    val photoUrl: String,
    val avatarUrl: String?,
    val musicName: String?,
    val musicUrl: String?,
    val quickReplies: List<String> = emptyList(),
    val commentCount: Int = 0,
    val previewComments: List<CommentCard> = emptyList(),
)

internal object Town {
    var me: Me? = null
    var meKnown = false
    var language: Language = Language.EN
    var returnTo: String = "/"
    var pending: PostCard? = null

    val signedOut: Boolean get() = meKnown && me == null

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
    intro = (node.intro as? String).orEmpty(),
    language = (node.language as? String) ?: "system",
    hasProfilePhoto = node.hasProfilePhoto == true,
    photoUrl = (node.photoUrl as? String)?.let(::prefixServerOrigin),
)

internal fun postFrom(node: dynamic): PostCard = PostCard(
    id = node.id as String,
    authorId = (node.authorId as? String).orEmpty(),
    authorName = (node.authorName as? String).orEmpty(),
    authorPhotoUrl = (node.authorPhotoUrl as? String)?.let(::prefixServerOrigin),
    authorLocation = (node.authorLocation as? String).orEmpty(),
    authorIntro = (node.authorIntro as? String).orEmpty(),
    message = (node.message as? String).orEmpty(),
    location = (node.location as? String).orEmpty(),
    photoUrl = prefixServerOrigin((node.photoUrl as? String).orEmpty()),
    avatarUrl = (node.avatarUrl as? String)?.let(::prefixServerOrigin),
    musicName = (node.musicName as? String)?.takeIf { it.isNotBlank() },
    musicUrl = (node.musicUrl as? String)?.let(::prefixServerOrigin),
    quickReplies = readStringList(node.quickReplies),
    commentCount = num(node.commentCount).toInt(),
    previewComments = readComments(node.comments),
)

internal fun commentFrom(node: dynamic): CommentCard = CommentCard(
    id = (node.id as? String).orEmpty(),
    userId = (node.userId as? String).orEmpty(),
    authorName = (node.authorName as? String).orEmpty(),
    authorPhotoUrl = (node.authorPhotoUrl as? String)?.let(::prefixServerOrigin),
    authorLocation = (node.authorLocation as? String).orEmpty(),
    authorIntro = (node.authorIntro as? String).orEmpty(),
    text = (node.text as? String).orEmpty(),
    createdAt = num(node.createdAt).toLong(),
    replyToAuthorName = (node.replyToAuthorName as? String)?.takeIf { it.isNotBlank() },
    replyToText = (node.replyToText as? String)?.takeIf { it.isNotBlank() },
)

internal fun readComments(node: dynamic): List<CommentCard> {
    val comments = mutableListOf<CommentCard>()
    if (node == null || node == undefined) return comments
    val length = node.length as? Int ?: return comments
    for (i in 0 until length) comments += commentFrom(node[i])
    return comments
}

internal fun readStringList(node: dynamic): List<String> {
    val values = mutableListOf<String>()
    if (node == null || node == undefined) return values
    val length = node.length as? Int ?: return values
    for (i in 0 until length) {
        val value = node[i] as? String
        if (!value.isNullOrBlank()) values += value
    }
    return values
}

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
