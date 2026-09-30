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
    val avatarId: String,
    val photoPath: String,
    val message: String,
    val location: String,
    val createdAt: Long,
    val views: Long = 0,
    val replyClicks: Long = 0,
    val shareClicks: Long = 0,
    val musicId: String? = null,
)

data class FeedItem(
    val id: String,
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
