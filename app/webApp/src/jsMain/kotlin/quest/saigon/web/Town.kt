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
    val paused: Boolean = false,
    val admin: Boolean = false,
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
    val postText: String? = null,
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
    val photoUrls: List<String> = emptyList(),
    val avatarUrl: String?,
    val musicName: String?,
    val musicUrl: String?,
    val quickReplies: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val commentCount: Int = 0,
    val previewComments: List<CommentCard> = emptyList(),
    val communityId: String? = null,
    val communityName: String? = null,
    val communityEmoji: String? = null,
    val communityOnly: Boolean = false,
    val placeId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val eventStartAt: Long? = null,
    val eventEndAt: Long? = null,
    val eventPrice: Long? = null,
    val attendeeCount: Int = 0,
    val joined: Boolean = false,
    val attendees: List<EventPerson> = emptyList(),
)

internal data class EventPerson(
    val id: String,
    val name: String,
    val photoUrl: String?,
    val location: String = "",
    val intro: String = "",
    val phone: String? = null,
    val zaloUrl: String? = null,
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
    paused = node.paused == true,
    admin = node.admin == true,
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
    photoUrls = readStringList(node.photoUrls).map(::prefixServerOrigin),
    avatarUrl = (node.avatarUrl as? String)?.let(::prefixServerOrigin),
    musicName = (node.musicName as? String)?.takeIf { it.isNotBlank() },
    musicUrl = (node.musicUrl as? String)?.let(::prefixServerOrigin),
    quickReplies = readStringList(node.quickReplies),
    createdAt = num(node.createdAt).toLong(),
    commentCount = num(node.commentCount).toInt(),
    previewComments = readComments(node.comments),
    communityId = (node.communityId as? String)?.takeIf { it.isNotBlank() },
    communityName = (node.communityName as? String)?.takeIf { it.isNotBlank() },
    communityEmoji = (node.communityEmoji as? String)?.takeIf { it.isNotBlank() },
    communityOnly = node.communityOnly == true,
    placeId = (node.placeId as? String)?.takeIf { it.isNotBlank() },
    lat = num(node.lat).takeIf { it != 0.0 },
    lng = num(node.lng).takeIf { it != 0.0 },
    eventStartAt = num(node.eventStartAt).toLong().takeIf { it > 0 },
    eventEndAt = num(node.eventEndAt).toLong().takeIf { it > 0 },
    eventPrice = num(node.eventPrice).toLong().takeIf { it > 0 },
    attendeeCount = num(node.attendeeCount).toInt(),
    joined = node.joined == true,
    attendees = readEventPeople(node.attendees),
)

internal fun eventPersonFrom(node: dynamic): EventPerson = EventPerson(
    id = (node.id as? String).orEmpty(),
    name = (node.name as? String).orEmpty(),
    photoUrl = (node.photoUrl as? String)?.let(::prefixServerOrigin),
    location = (node.location as? String).orEmpty(),
    intro = (node.intro as? String).orEmpty(),
    phone = (node.phone as? String)?.takeIf { it.isNotBlank() },
    zaloUrl = (node.zaloUrl as? String)?.takeIf { it.isNotBlank() },
)

internal fun readEventPeople(node: dynamic): List<EventPerson> {
    val items = mutableListOf<EventPerson>()
    if (node == null || node == undefined) return items
    val length = node.length as? Int ?: return items
    for (i in 0 until length) {
        val person = eventPersonFrom(node[i])
        if (person.id.isNotBlank() || person.name.isNotBlank()) items += person
    }
    return items
}

internal val PostCard.hasEvent: Boolean get() = (eventStartAt ?: 0L) > 0

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
    postText = (node.postText as? String)?.takeIf { it.isNotBlank() },
)

internal fun readComments(node: dynamic): List<CommentCard> {
    val comments = mutableListOf<CommentCard>()
    if (node == null || node == undefined) return comments
    val length = node.length as? Int ?: return comments
    for (i in 0 until length) comments += commentFrom(node[i])
    return comments
}

internal fun PostCard.allPhotoUrls(): List<String> {
    val extras = photoUrls.filter { it.isNotBlank() && it != photoUrl }
    return if (photoUrl.isBlank()) extras else listOf(photoUrl) + extras
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
