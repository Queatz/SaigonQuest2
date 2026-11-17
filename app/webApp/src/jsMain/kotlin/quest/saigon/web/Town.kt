package quest.saigon.web

import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.resolveLanguage
import quest.saigon.shared.model.CommentCardDto
import quest.saigon.shared.model.EventPersonDto
import quest.saigon.shared.model.MeDto
import quest.saigon.shared.model.PollChoiceDto
import quest.saigon.shared.model.PostCardDto
import quest.saigon.shared.store.SessionUiState
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
    val placeId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val locationId: String? = null,
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
    val locationId: String? = null,
    val eventStartAt: Long? = null,
    val eventEndAt: Long? = null,
    val eventPrice: Long? = null,
    val attendeeCount: Int = 0,
    val joined: Boolean = false,
    val attendees: List<EventPerson> = emptyList(),
    val pollOptions: List<PollChoice> = emptyList(),
    val hasQuestion: Boolean = false,
    val questionTitle: String = "",
    val answerCount: Int = 0,
    val answered: Boolean = false,
    val ctaTitle: String = "",
    val ctaText: String = "",
    val ctaDestination: String = "",
    val ctaUrl: String = "",
    val authorZaloUrl: String? = null,
    val openChatId: String? = null,
    val openChatName: String = "",
    val openChatExpiresAt: Long? = null,
    val openChatRemaining: Long? = null,
    val openChatJoined: Boolean = false,
)

internal data class PollChoice(
    val index: Int,
    val text: String,
    val voteCount: Int = 0,
    val voted: Boolean = false,
    val voters: List<EventPerson> = emptyList(),
)

internal data class EventPerson(
    val id: String,
    val name: String,
    val photoUrl: String?,
    val latestPostId: String? = null,
    val latestPostPhotoUrl: String? = null,
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

    fun syncFromShared(state: SessionUiState) {
        me = state.me?.toLegacyMe()
        meKnown = state.meKnown
        language = state.language
        document.asDynamic().documentElement.lang = if (language == Language.VI) "vi" else "en"
        document.asDynamic().title = t("appName")
    }
}

internal fun MeDto.toLegacyMe(): Me = Me(
    id = id,
    name = name,
    phone = phone,
    location = location,
    intro = intro,
    language = language,
    hasProfilePhoto = hasProfilePhoto,
    photoUrl = photoUrl,
    paused = paused,
    admin = admin,
    placeId = placeId,
    lat = lat,
    lng = lng,
    locationId = locationId,
)

internal fun CommentCardDto.toLegacyComment(): CommentCard = CommentCard(
    id = id,
    userId = userId,
    authorName = authorName,
    authorPhotoUrl = authorPhotoUrl,
    authorLocation = authorLocation,
    authorIntro = authorIntro,
    text = text,
    createdAt = createdAt,
    replyToAuthorName = replyToAuthorName,
    replyToText = replyToText,
    postText = postText,
)

internal fun EventPersonDto.toLegacyEventPerson(): EventPerson = EventPerson(
    id = id,
    name = name,
    photoUrl = photoUrl,
    latestPostId = latestPostId,
    latestPostPhotoUrl = latestPostPhotoUrl,
    location = location,
    intro = intro,
    phone = phone,
    zaloUrl = zaloUrl,
)

internal fun PollChoiceDto.toLegacyPollChoice(): PollChoice = PollChoice(
    index = index,
    text = text,
    voteCount = voteCount,
    voted = voted,
    voters = voters.map { it.toLegacyEventPerson() },
)

internal fun PostCardDto.toLegacyPost(): PostCard = PostCard(
    id = id,
    authorId = authorId,
    authorName = authorName,
    authorPhotoUrl = authorPhotoUrl,
    authorLocation = authorLocation,
    authorIntro = authorIntro,
    message = message,
    location = location,
    photoUrl = photoUrl,
    photoUrls = photoUrls,
    avatarUrl = avatarUrl,
    musicName = musicName,
    musicUrl = musicUrl,
    quickReplies = quickReplies,
    createdAt = createdAt,
    commentCount = commentCount,
    previewComments = comments.map { it.toLegacyComment() },
    communityId = communityId,
    communityName = communityName,
    communityEmoji = communityEmoji,
    communityOnly = communityOnly,
    placeId = placeId,
    lat = lat,
    lng = lng,
    locationId = locationId,
    eventStartAt = eventStartAt,
    eventEndAt = eventEndAt,
    eventPrice = eventPrice,
    attendeeCount = attendeeCount,
    joined = joined,
    attendees = attendees.map { it.toLegacyEventPerson() },
    pollOptions = pollOptions.map { it.toLegacyPollChoice() },
    hasQuestion = hasQuestion,
    questionTitle = questionTitle,
    answerCount = answerCount,
    answered = answered,
    ctaTitle = ctaTitle,
    ctaText = ctaText,
    ctaDestination = ctaDestination,
    ctaUrl = ctaUrl.orEmpty(),
    authorZaloUrl = authorZaloUrl,
    openChatId = openChatId,
    openChatName = openChatName.orEmpty(),
    openChatExpiresAt = openChatExpiresAt,
    openChatRemaining = openChatRemaining,
    openChatJoined = openChatJoined,
)

internal fun localizedActivityText(activity: dynamic, field: String): String {
    val original = (activity[field] as? String).orEmpty()
    val locale = if (Town.language == Language.VI) "vi" else "en"
    val translations = activity.translations
    if (translations == null || translations == undefined) return original
    val localized = translations[locale]
    if (localized == null || localized == undefined) return original
    return (localized[field] as? String)?.takeIf { it.isNotBlank() } ?: original
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
    placeId = (node.placeId as? String)?.takeIf { it.isNotBlank() },
    lat = if (node.lat == null || node.lat == undefined) null else num(node.lat),
    lng = if (node.lng == null || node.lng == undefined) null else num(node.lng),
    locationId = (node.locationId as? String)?.takeIf { it.isNotBlank() },
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
    lat = if (node.lat == null || node.lat == undefined) null else num(node.lat),
    lng = if (node.lng == null || node.lng == undefined) null else num(node.lng),
    locationId = (node.locationId as? String)?.takeIf { it.isNotBlank() },
    eventStartAt = num(node.eventStartAt).toLong().takeIf { it > 0 },
    eventEndAt = num(node.eventEndAt).toLong().takeIf { it > 0 },
    eventPrice = num(node.eventPrice).toLong().takeIf { it > 0 },
    attendeeCount = num(node.attendeeCount).toInt(),
    joined = node.joined == true,
    attendees = readEventPeople(node.attendees),
    pollOptions = readPollOptions(node.pollOptions),
    hasQuestion = node.hasQuestion == true,
    questionTitle = (node.questionTitle as? String).orEmpty(),
    answerCount = num(node.answerCount).toInt(),
    answered = node.answered == true,
    ctaTitle = (node.ctaTitle as? String).orEmpty(),
    ctaText = (node.ctaText as? String).orEmpty(),
    ctaDestination = (node.ctaDestination as? String).orEmpty(),
    ctaUrl = (node.ctaUrl as? String).orEmpty(),
    authorZaloUrl = (node.authorZaloUrl as? String)?.takeIf { it.isNotBlank() },
    openChatId = (node.openChatId as? String)?.takeIf { it.isNotBlank() },
    openChatName = (node.openChatName as? String).orEmpty(),
    openChatExpiresAt = if (node.openChatExpiresAt == null || node.openChatExpiresAt == undefined) {
        null
    } else {
        num(node.openChatExpiresAt).toLong().takeIf { it > 0 }
    },
    openChatRemaining = if (node.openChatRemaining == null || node.openChatRemaining == undefined) {
        null
    } else {
        num(node.openChatRemaining).toLong()
    },
    openChatJoined = node.openChatJoined == true,
)

internal fun eventPersonFrom(node: dynamic): EventPerson = EventPerson(
    id = (node.id as? String).orEmpty(),
    name = (node.name as? String).orEmpty(),
    photoUrl = (node.photoUrl as? String)?.let(::prefixServerOrigin),
    latestPostId = (node.latestPostId as? String)?.takeIf { it.isNotBlank() },
    latestPostPhotoUrl = (node.latestPostPhotoUrl as? String)?.let(::prefixServerOrigin),
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

internal val PostCard.hasPoll: Boolean get() = pollOptions.isNotEmpty()

internal val PostCard.hasCta: Boolean get() = ctaText.isNotBlank()

internal val PostCard.hasOpenChat: Boolean get() = !openChatId.isNullOrBlank()

internal fun readPollOptions(node: dynamic): List<PollChoice> {
    val items = mutableListOf<PollChoice>()
    if (node == null || node == undefined) return items
    val length = node.length as? Int ?: return items
    for (i in 0 until length) {
        val option = node[i]
        val text = (option.text as? String).orEmpty()
        if (text.isBlank()) continue
        items += PollChoice(
            index = num(option.index).toInt().takeIf { it >= 0 } ?: i,
            text = text,
            voteCount = num(option.voteCount).toInt(),
            voted = option.voted == true,
            voters = readEventPeople(option.voters),
        )
    }
    return items
}

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
