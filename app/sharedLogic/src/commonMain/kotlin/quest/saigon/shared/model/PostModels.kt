package quest.saigon.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class CommentCardDto(
    val id: String,
    val postId: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String? = null,
    val authorLocation: String = "",
    val authorIntro: String = "",
    val text: String = "",
    val createdAt: Long = 0L,
    val replyToAuthorName: String? = null,
    val replyToText: String? = null,
    val postText: String? = null,
)

@Serializable
data class EventPersonDto(
    val id: String = "",
    val name: String = "",
    val photoUrl: String? = null,
    val latestPostId: String? = null,
    val latestPostPhotoUrl: String? = null,
    val location: String = "",
    val intro: String = "",
    val joinedAt: Long = 0L,
    val phone: String? = null,
    val zaloUrl: String? = null,
    val host: Boolean = false,
)

@Serializable
data class PollChoiceDto(
    val index: Int = 0,
    val text: String = "",
    val voteCount: Int = 0,
    val voted: Boolean = false,
    val voters: List<EventPersonDto> = emptyList(),
)

@Serializable
data class PostCardDto(
    val id: String,
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String? = null,
    val authorLocation: String = "",
    val authorIntro: String = "",
    val message: String = "",
    val location: String = "",
    val photoUrl: String = "",
    val photoUrls: List<String> = emptyList(),
    val avatarUrl: String? = null,
    val createdAt: Long = 0L,
    val musicName: String? = null,
    val musicUrl: String? = null,
    val quickReplies: List<String> = emptyList(),
    val commentCount: Int = 0,
    val comments: List<CommentCardDto> = emptyList(),
    val communityId: String? = null,
    val communityName: String? = null,
    val communityEmoji: String? = null,
    val communityOnly: Boolean = false,
    val placeId: String? = null,
    val locationId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val eventStartAt: Long? = null,
    val eventEndAt: Long? = null,
    val eventPrice: Long? = null,
    val attendeeCount: Int = 0,
    val joined: Boolean = false,
    val attendees: List<EventPersonDto> = emptyList(),
    val pollOptions: List<PollChoiceDto> = emptyList(),
    val hasQuestion: Boolean = false,
    val questionTitle: String = "",
    val answerCount: Int = 0,
    val answered: Boolean = false,
    val ctaTitle: String = "",
    val ctaText: String = "",
    val ctaDestination: String = "",
    val ctaUrl: String? = null,
    val authorZaloUrl: String? = null,
    val openChatId: String? = null,
    val openChatName: String? = null,
    val openChatExpiresAt: Long? = null,
    val openChatRemaining: Long? = null,
    val openChatJoined: Boolean = false,
) {
    val hasEvent: Boolean get() = (eventStartAt ?: 0L) > 0L
    val hasPoll: Boolean get() = pollOptions.isNotEmpty()
    val hasCta: Boolean get() = ctaText.isNotBlank()
    val hasOpenChat: Boolean get() = !openChatId.isNullOrBlank()

    fun allPhotoUrls(): List<String> {
        val extras = photoUrls.filter { it.isNotBlank() && it != photoUrl }
        return if (photoUrl.isBlank()) extras else listOf(photoUrl) + extras
    }
}

@Serializable
data class FeedPageDto(
    val posts: List<PostCardDto> = emptyList(),
    val nextCursor: String? = null,
    val count: Long? = null,
)

@Serializable
data class CommentsPageDto(
    val comments: List<CommentCardDto> = emptyList(),
    val nextCursor: String? = null,
    val total: Long = 0L,
)

@Serializable
data class CreateCommentRequest(
    val text: String,
    val replyToId: String? = null,
)

@Serializable
data class PatchPostRequest(
    val message: String,
)

@Serializable
data class PatchPostResponse(
    val ok: Boolean = true,
    val message: String = "",
)

@Serializable
data class AttendeesPageDto(
    val attendees: List<EventPersonDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class PollVotersResponse(
    val options: List<PollChoiceDto> = emptyList(),
)

@Serializable
data class QuestionAnswerRequest(
    val text: String,
)

@Serializable
data class QuestionAnswerDto(
    val id: String,
    val userId: String = "",
    val name: String = "",
    val photoUrl: String? = null,
    val location: String = "",
    val intro: String = "",
    val text: String = "",
    val createdAt: Long = 0L,
)

@Serializable
data class QuestionAnswersResponse(
    val answers: List<QuestionAnswerDto> = emptyList(),
)

@Serializable
data class CheckInDto(
    val postId: String,
    val message: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String? = null,
    val location: String = "",
    val placeId: String? = null,
    val locationId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val untilAt: Long = 0L,
    val postCount: Long = 0L,
    val activityCount: Long = 0L,
    val activityEmojis: List<String> = emptyList(),
    val hasActivities: Boolean = false,
    val photoUrl: String? = null,
)

@Serializable
data class CheckInsResponse(
    val checkIns: List<CheckInDto> = emptyList(),
)

@Serializable
data class CheckInUpdateRequest(
    val checkInMinutes: Int,
)

@Serializable
data class CheckInUpdateResponse(
    val ok: Boolean = true,
    val untilAt: Long = 0L,
)

@Serializable
data class ContactZaloResponse(
    val zaloUrl: String,
)

@Serializable
data class OwnedPostDto(
    val id: String,
    val message: String = "",
    val location: String = "",
    val photoUrl: String = "",
    val avatarUrl: String? = null,
    val createdAt: Long = 0L,
    val views: Long = 0L,
    val replyClicks: Long = 0L,
    val shareClicks: Long = 0L,
    val musicName: String? = null,
    val communityId: String? = null,
    val communityName: String? = null,
    val communityEmoji: String? = null,
    val communityOnly: Boolean = false,
)

@Serializable
data class OwnedPostsPageDto(
    val posts: List<OwnedPostDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class EventsSummaryDto(
    val today: Long = 0L,
    val tomorrow: Long = 0L,
    val week: Long = 0L,
)

@Serializable
data class AvatarDto(
    val id: String,
    val prompt: String = "",
    val status: String = "",
    val imageUrl: String? = null,
    val error: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
data class AvatarsResponse(
    val avatars: List<AvatarDto> = emptyList(),
)

@Serializable
data class CreateAvatarRequest(
    val prompt: String,
)

@Serializable
data class MusicDto(
    val id: String,
    val name: String = "",
    val prompt: String = "",
    val status: String = "",
    val audioUrl: String? = null,
    val error: String? = null,
    val gender: String? = null,
    val instrumental: Boolean = false,
    val lyrics: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
data class MusicPageDto(
    val music: List<MusicDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class CreateMusicRequest(
    val prompt: String,
    val gender: String? = null,
    val instrumental: Boolean = false,
)

@Serializable
data class RenameMusicRequest(
    val name: String,
)
