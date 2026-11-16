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
    val placeId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val locationId: String? = null,
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
    val locationId: String? = null,
    val eventStartAt: Long? = null,
    val eventEndAt: Long? = null,
    val eventPrice: Long? = null,
    val attendeeCount: Long = 0,
    val eventHourNotified: Boolean = false,
    val eventNowNotified: Boolean = false,
    val pollOptions: List<String> = emptyList(),
    val hasQuestion: Boolean = false,
    val questionTitle: String = "",
    val ctaTitle: String = "",
    val ctaText: String = "",
    val ctaDestination: String = "",
    val ctaUrl: String = "",
    val openChatId: String? = null,
    val openChatName: String = "",
    val openChatExpiresAt: Long? = null,
    val openChatRemaining: Long? = null,
    val checkInUntilAt: Long? = null,
)

data class CheckInView(
    val postId: String,
    val message: String,
    val authorId: String,
    val authorName: String,
    val authorPhotoPath: String?,
    val location: String,
    val placeId: String?,
    val locationId: String?,
    val lat: Double,
    val lng: Double,
    val untilAt: Long,
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
    val locationId: String? = null,
    val eventStartAt: Long? = null,
    val eventEndAt: Long? = null,
    val eventPrice: Long? = null,
    val attendeeCount: Long = 0,
    val joined: Boolean = false,
    val attendees: List<EventAttendee> = emptyList(),
    val pollOptions: List<PollOptionView> = emptyList(),
    val hasQuestion: Boolean = false,
    val questionTitle: String = "",
    val answerCount: Long = 0,
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

data class ProductRecord(
    val id: String,
    val userId: String,
    val name: String,
    val price: Long,
    val category: String,
    val description: String,
    val photoPaths: List<String>,
    val createdAt: Long,
)

data class ProductPage(
    val items: List<ProductRecord>,
    val count: Long,
    val nextCursor: String?,
)

data class ProductOrderRecord(
    val id: String,
    val buyerId: String,
    val sellerId: String,
    val productId: String,
    val productName: String,
    val price: Long,
    val contactMethod: String,
    val message: String,
    val contactChatId: String? = null,
    val resolved: Boolean = false,
    val createdAt: Long,
)

data class ProductOrderView(
    val order: ProductOrderRecord,
    val buyerName: String,
    val buyerPhone: String,
    val buyerPhotoPath: String?,
    val buyerLocation: String,
    val buyerIntro: String,
)

data class ProductOrderPage(
    val items: List<ProductOrderView>,
    val nextCursor: String?,
    val openCount: Long = 0,
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
    val chatId: String = "",
)

data class NotificationPage(
    val items: List<NotificationRecord>,
    val nextCursor: String?,
    val unreadCount: Long,
)

object NotificationType {
    const val PRODUCT_ORDER = "product_order"
    const val POST_COMMENT = "post_comment"
    const val THREAD_COMMENT = "thread_comment"
    const val EVENT_JOINED = "event_joined"
    const val EVENT_LEFT = "event_left"
    const val EVENT_FRIEND_JOINED = "event_friend_joined"
    const val EVENT_STARTING_SOON = "event_starting_soon"
    const val EVENT_STARTING_NOW = "event_starting_now"
    const val POLL_VOTED = "poll_voted"
    const val QUESTION_ANSWERED = "question_answered"
    const val CHAT_MESSAGE = "chat_message"
    const val CHAT_ADDED = "chat_added"
    const val CHAT_REMOVED = "chat_removed"
    const val CHAT_DELETED = "chat_deleted"

    fun isChat(type: String): Boolean = type == CHAT_MESSAGE || type == CHAT_ADDED ||
        type == CHAT_REMOVED || type == CHAT_DELETED
}

object ChatType {
    const val DIRECT = "direct"
    const val GROUP = "group"
}

data class ChatRecord(
    val id: String,
    val type: String,
    val name: String,
    val createdBy: String,
    val createdAt: Long,
    val lastMessageAt: Long,
    val lastMessageId: String = "",
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageSenderName: String = "",
    val pairKey: String = "",
)

data class ChatMemberRecord(
    val chatId: String,
    val userId: String,
    val owner: Boolean,
    val joinedAt: Long,
    val lastReadAt: Long = 0,
    val unreadCount: Long = 0,
)

data class ChatMessageRecord(
    val id: String,
    val chatId: String,
    val userId: String,
    val text: String,
    val photoPaths: List<String> = emptyList(),
    val createdAt: Long,
    val attachment: ChatAttachment? = null,
)

data class ChatAttachment(
    val type: String,
    val productId: String = "",
    val productName: String = "",
    val price: Long = 0,
    val photoPath: String? = null,
    val activityId: String = "",
    val activityName: String = "",
    val activityEmoji: String = "",
    val activityDescription: String = "",
) {
    fun preview(): String = when (type) {
        "activity" -> listOf(activityEmoji, activityName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "🎯" }
        else -> productName.ifBlank { "📷" }
    }
}

data class ChatReactionRecord(
    val id: String,
    val chatId: String,
    val messageId: String,
    val userId: String,
    val emoji: String,
    val createdAt: Long,
)

data class ChatFace(
    val userId: String,
    val name: String,
    val photoPath: String?,
)

data class ChatSummary(
    val id: String,
    val type: String,
    val name: String,
    val photos: List<ChatFace>,
    val lastMessageSenderName: String,
    val lastMessageText: String,
    val lastMessageAt: Long,
    val unreadCount: Long,
    val peerId: String? = null,
    val owner: Boolean = false,
    val memberCount: Int = 0,
)

data class ChatPage(
    val items: List<ChatSummary>,
    val nextCursor: String?,
    val unreadCount: Long,
)

data class ReactionCount(
    val emoji: String,
    val count: Int,
    val mine: Boolean = false,
)

data class ReactionDetail(
    val id: String,
    val emoji: String,
    val userId: String,
    val name: String,
    val photoPath: String?,
    val mine: Boolean,
)

data class ChatMessageItem(
    val id: String,
    val chatId: String,
    val userId: String,
    val authorName: String,
    val authorPhotoPath: String?,
    val text: String,
    val photoPaths: List<String>,
    val createdAt: Long,
    val reactions: List<ReactionCount> = emptyList(),
    val attachment: ChatAttachment? = null,
)

data class ChatMessagePage(
    val items: List<ChatMessageItem>,
    val nextCursor: String?,
)

data class ChatMemberView(
    val id: String,
    val name: String,
    val profilePhotoPath: String?,
    val location: String = "",
    val intro: String = "",
    val phone: String? = null,
    val owner: Boolean = false,
    val joinedAt: Long = 0,
)

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

data class PollVote(
    val postId: String,
    val optionIndex: Int,
    val userId: String,
    val votedAt: Long,
)

data class PollOptionView(
    val index: Int,
    val text: String,
    val voteCount: Long = 0,
    val voted: Boolean = false,
    val voters: List<EventAttendee> = emptyList(),
)

data class QuestionAnswerRecord(
    val id: String,
    val postId: String,
    val userId: String,
    val text: String,
    val createdAt: Long,
)

data class QuestionAnswerItem(
    val id: String,
    val postId: String,
    val userId: String,
    val authorName: String,
    val authorPhotoPath: String?,
    val authorLocation: String = "",
    val authorIntro: String = "",
    val text: String,
    val createdAt: Long,
)

data class EventSummary(
    val today: Long,
    val tomorrow: Long,
    val week: Long,
)

data class PlaceSuggestion(
    val name: String,
    val address: String = "",
    val placeId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val recent: Boolean = false,
    val postCount: Long = 0,
    val photoPath: String? = null,
    val googlePhotoName: String? = null,
    val category: String? = null,
    val id: String? = null,
    val custom: Boolean = false,
    val hasActivities: Boolean = false,
    val activityCount: Long = 0,
    val activityEmojis: List<String> = emptyList(),
)

data class UserLocationRecord(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val createdBy: String,
    val createdAt: Long,
)

data class ActivityScheduleSlot(
    val dayOfWeek: Int,
    val startMinutes: Int,
    val endMinutes: Int,
)

data class TranslationBlob(
    val sourceHash: String,
    val status: String,
    val error: String? = null,
    val updatedAt: Long = 0L,
    val locales: Map<String, Map<String, String>> = emptyMap(),
)

data class ActivityRecord(
    val id: String,
    val createdBy: String,
    val emoji: String,
    val name: String,
    val description: String,
    val locationName: String,
    val locationId: String? = null,
    val placeId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val hostUserId: String? = null,
    val schedule: List<ActivityScheduleSlot> = emptyList(),
    val interestedCount: Long = 0,
    val createdAt: Long,
    val translations: TranslationBlob? = null,
)

data class UserPhotoRecord(
    val id: String,
    val userId: String,
    val photoPath: String,
    val createdAt: Long,
    val activityId: String? = null,
    val locationName: String? = null,
    val placeId: String? = null,
    val locationId: String? = null,
)

data class LocationDescriptionRecord(
    val id: String,
    val locationName: String,
    val description: String,
    val placeId: String? = null,
    val locationId: String? = null,
)

fun locationIdentityKey(locationName: String, placeId: String?, locationId: String?): String {
    val customId = locationId?.takeIf { it.isNotBlank() }
    val pid = placeId?.takeIf { it.isNotBlank() }
    return when {
        customId != null -> "location:$customId"
        pid != null -> "place:$pid"
        else -> "name:${locationName.trim().lowercase()}"
    }
}

fun userPhotoMatchesLocation(
    photo: UserPhotoRecord,
    locationName: String,
    placeId: String?,
    locationId: String?,
): Boolean {
    return locationIdentityKey(photo.locationName.orEmpty(), photo.placeId, photo.locationId) ==
        locationIdentityKey(locationName, placeId, locationId)
}

data class ActivityView(
    val activity: ActivityRecord,
    val interested: Boolean,
    val host: EventAttendee? = null,
    val interestedPeople: List<EventAttendee> = emptyList(),
    val photoPaths: List<String> = emptyList(),
)

data class ActivityPage(
    val items: List<ActivityRecord>,
    val nextCursor: String?,
)

data class ActivityLocationFlags(
    val locationIds: Set<String>,
    val placeIds: Set<String>,
    val locationActivities: Map<String, ActivityLocationSummary> = emptyMap(),
    val placeActivities: Map<String, ActivityLocationSummary> = emptyMap(),
)

data class ActivityLocationSummary(
    val count: Long,
    val emojis: List<String>,
)

fun activityMatchesLocation(
    activity: ActivityRecord,
    locationName: String,
    placeId: String?,
    locationId: String?,
): Boolean {
    val customId = locationId?.takeIf { it.isNotBlank() }
    val pid = placeId?.takeIf { it.isNotBlank() }
    return when {
        customId != null -> activity.locationId == customId
        pid != null -> activity.locationId.isNullOrBlank() && activity.placeId == pid
        else -> activity.locationId.isNullOrBlank() &&
            activity.placeId.isNullOrBlank() &&
            activity.locationName.equals(locationName.trim(), ignoreCase = true)
    }
}

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
    val latestPosts: List<CommunityPostPhoto> = emptyList(),
)

data class CommunityPostPhoto(
    val id: String,
    val photoPath: String,
)

data class CommunityMember(
    val id: String,
    val name: String,
    val profilePhotoPath: String?,
    val location: String,
    val intro: String,
    val joinedAt: Long,
    val latestPostId: String? = null,
    val latestPostPhotoPath: String? = null,
)

data class CommunityMemberPage(
    val items: List<CommunityMember>,
    val nextCursor: String?,
)

data class FeatureSuggestionRecord(
    val id: String,
    val userId: String,
    val text: String,
    val createdAt: Long,
    val resolvedAt: Long? = null,
)

data class FeatureVote(
    val suggestionId: String,
    val userId: String,
    val votedAt: Long,
)

data class FeatureSuggestionView(
    val id: String,
    val text: String,
    val voteCount: Long,
    val voted: Boolean,
    val resolved: Boolean,
    val createdAt: Long,
)
