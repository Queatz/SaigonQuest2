package quest.saigon.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class StatsDayDto(
    val day: String,
    val visitorVisits: Long = 0L,
    val userVisits: Long = 0L,
    val uniqueUserVisits: Long = 0L,
    val posts: Long = 0L,
    val newUsers: Long = 0L,
    val replyClicks: Long = 0L,
    val shareClicks: Long = 0L,
    val postViews: Long = 0L,
)

@Serializable
data class StatsResponse(
    val timezone: String = "UTC+7",
    val totalUsers: Long = 0L,
    val totalPosts: Long = 0L,
    val days: List<StatsDayDto> = emptyList(),
)

@Serializable
data class FeatureSuggestionDto(
    val id: String,
    val text: String = "",
    val voteCount: Long = 0L,
    val voted: Boolean = false,
    val resolved: Boolean = false,
    val createdAt: Long = 0L,
)

@Serializable
data class FeaturesResponse(
    val features: List<FeatureSuggestionDto> = emptyList(),
)

@Serializable
data class CreateFeatureRequest(
    val text: String,
)

@Serializable
data class AdminMeResponse(
    val admin: Boolean = false,
)

@Serializable
data class AdminUserDto(
    val id: String,
    val name: String = "",
    val intro: String = "",
    val photoUrl: String? = null,
    val createdAt: Long = 0L,
    val postCount: Long = 0L,
    val paused: Boolean = false,
)

@Serializable
data class AdminUsersPageDto(
    val users: List<AdminUserDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class PauseUserResponse(
    val id: String,
    val paused: Boolean,
)

@Serializable
data class AdminPostDto(
    val id: String,
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String? = null,
    val message: String = "",
    val location: String = "",
    val photoUrl: String = "",
    val avatarUrl: String? = null,
    val createdAt: Long = 0L,
    val views: Long = 0L,
    val replyClicks: Long = 0L,
    val shareClicks: Long = 0L,
    val musicName: String? = null,
)

@Serializable
data class AdminPostsPageDto(
    val posts: List<AdminPostDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class AdminCommentsPageDto(
    val comments: List<CommentCardDto> = emptyList(),
    val nextCursor: String? = null,
)
