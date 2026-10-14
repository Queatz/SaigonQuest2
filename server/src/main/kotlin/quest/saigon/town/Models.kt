package quest.saigon.town

data class UserRecord(
    val id: String,
    val phone: String,
    val passwordHash: String,
    val name: String,
    val location: String,
    val profilePhotoPath: String?,
    val language: String,
    val createdAt: Long,
    val intro: String = "",
    val paused: Boolean = false,
    val lastActiveAt: Long = 0,
)

data class AdminRecord(
    val userId: String,
    val createdAt: Long,
)

data class AdminUser(
    val id: String,
    val name: String,
    val intro: String,
    val profilePhotoPath: String?,
    val createdAt: Long,
    val postCount: Long,
    val paused: Boolean,
)

data class AdminUserPage(
    val items: List<AdminUser>,
    val nextCursor: String?,
)

data class AdminPost(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorPhotoPath: String?,
    val message: String,
    val location: String,
    val photoPath: String,
    val avatarPath: String?,
    val createdAt: Long,
    val views: Long,
    val replyClicks: Long,
    val shareClicks: Long,
    val musicName: String? = null,
)

data class AdminPostPage(
    val items: List<AdminPost>,
    val nextCursor: String?,
)

data class SessionRecord(
    val token: String,
    val userId: String,
    val expiresAt: Long,
)

data class AvatarRecord(
    val id: String,
    val userId: String,
    val prompt: String,
    val imagePath: String?,
    val status: String,
    val error: String?,
    val createdAt: Long,
)

data class MusicRecord(
    val id: String,
    val userId: String,
    val prompt: String,
    val name: String,
    val audioPath: String?,
    val status: String,
    val error: String?,
    val gender: String?,
    val instrumental: Boolean,
    val createdAt: Long,
    val lyrics: String? = null,
)

data class MusicPage(
    val items: List<MusicRecord>,
    val nextCursor: String?,
)

data class PostRecord(
    val id: String,
    val userId: String,
    val avatarId: String? = null,
    val photoPath: String,
    val message: String,
    val location: String,
    val createdAt: Long,
    val views: Long = 0,
    val replyClicks: Long = 0,
    val shareClicks: Long = 0,
    val musicId: String? = null,
    val quickReplies: List<String> = emptyList(),
    val commentCount: Long = 0,
    val extraPhotoPaths: List<String> = emptyList(),
    val communityId: String? = null,
    val communityOnly: Boolean = false,
    val placeId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val eventStartAt: Long? = null,
    val eventEndAt: Long? = null,
    val eventPrice: Long? = null,
    val attendeeCount: Long = 0,
    val eventHourNotified: Boolean = false,
    val eventNowNotified: Boolean = false,
)

data class FeedItem(
    val id: String,
    val authorId: String = "",
    val authorName: String,
    val authorPhotoPath: String?,
    val message: String,
    val location: String,
    val photoPath: String,
    val avatarPath: String?,
    val createdAt: Long,
    val authorLocation: String = "",
    val authorIntro: String = "",
    val musicName: String? = null,
    val musicPath: String? = null,
    val quickReplies: List<String> = emptyList(),
    val commentCount: Long = 0,
    val previewComments: List<CommentItem> = emptyList(),
    val extraPhotoPaths: List<String> = emptyList(),
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
    val attendeeCount: Long = 0,
    val joined: Boolean = false,
    val attendees: List<EventAttendee> = emptyList(),
)

data class FeedPage(
    val items: List<FeedItem>,
    val nextCursor: String?,
)

data class OwnedPost(
    val id: String,
    val message: String,
    val location: String,
    val photoPath: String,
    val avatarPath: String?,
    val createdAt: Long,
    val views: Long,
    val replyClicks: Long,
    val shareClicks: Long,
    val musicName: String? = null,
    val communityId: String? = null,
    val communityName: String? = null,
    val communityEmoji: String? = null,
    val communityOnly: Boolean = false,
)

data class OwnedPostPage(
    val items: List<OwnedPost>,
    val nextCursor: String?,
)

data class DayStats(
    val day: String,
    val visitorVisits: Long = 0,
    val userVisits: Long = 0,
    val uniqueUserVisits: Long = 0,
    val posts: Long = 0,
    val newUsers: Long = 0,
    val replyClicks: Long = 0,
    val shareClicks: Long = 0,
    val postViews: Long = 0,
)

data class AllTimeStats(
    val totalUsers: Long = 0,
    val totalPosts: Long = 0,
)

object AvatarStatus {
    const val GENERATING = "generating"
    const val READY = "ready"
    const val FAILED = "failed"
    const val REMOVED = "removed"
}

object MusicStatus {
    const val GENERATING = "generating"
    const val READY = "ready"
    const val FAILED = "failed"
    const val REMOVED = "removed"
}

data class CommentRecord(
    val id: String,
    val postId: String,
    val userId: String,
    val text: String,
    val replyToId: String?,
    val createdAt: Long,
)

data class CommentItem(
    val id: String,
    val postId: String,
    val userId: String,
    val authorName: String,
    val authorPhotoPath: String?,
    val authorLocation: String = "",
    val authorIntro: String = "",
    val text: String,
    val createdAt: Long,
    val replyToId: String? = null,
    val replyToAuthorName: String? = null,
    val replyToText: String? = null,
    val postText: String? = null,
)

data class CommentPage(
    val items: List<CommentItem>,
    val nextCursor: String?,
    val total: Long,
)

data class NotificationRecord(
    val id: String,
    val userId: String,
    val type: String,
    val postId: String,
    val commentId: String,
    val actorId: String,
    val actorName: String,
    val preview: String,
    val createdAt: Long,
    val readAt: Long? = null,
)

data class NotificationPage(
    val items: List<NotificationRecord>,
    val nextCursor: String?,
    val unreadCount: Long,
)

object NotificationType {
    const val POST_COMMENT = "post_comment"
    const val THREAD_COMMENT = "thread_comment"
    const val EVENT_JOINED = "event_joined"
    const val EVENT_LEFT = "event_left"
    const val EVENT_FRIEND_JOINED = "event_friend_joined"
    const val EVENT_STARTING_SOON = "event_starting_soon"
    const val EVENT_STARTING_NOW = "event_starting_now"
}

data class PushSubscriptionRecord(
    val endpoint: String,
    val userId: String,
    val p256dh: String,
    val auth: String,
    val language: String,
    val createdAt: Long,
)

data class EventAttendee(
    val id: String,
    val name: String,
    val profilePhotoPath: String?,
    val location: String = "",
    val intro: String = "",
    val phone: String? = null,
    val joinedAt: Long = 0,
)

data class EventAttendeePage(
    val items: List<EventAttendee>,
    val nextCursor: String?,
)

data class EventSummary(
    val today: Long,
    val tomorrow: Long,
)

data class PlaceSuggestion(
    val name: String,
    val address: String = "",
    val placeId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val recent: Boolean = false,
)

data class CommunityRecord(
    val id: String,
    val name: String,
    val emoji: String,
    val slug: String,
    val createdBy: String,
    val createdAt: Long,
    val memberCount: Long = 0,
    val description: String = "",
)

data class CommunityView(
    val community: CommunityRecord,
    val joined: Boolean,
)

data class CommunityMember(
    val id: String,
    val name: String,
    val profilePhotoPath: String?,
    val location: String,
    val intro: String,
    val joinedAt: Long,
)

data class CommunityMemberPage(
    val items: List<CommunityMember>,
    val nextCursor: String?,
)
