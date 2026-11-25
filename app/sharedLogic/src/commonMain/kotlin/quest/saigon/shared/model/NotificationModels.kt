package quest.saigon.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    val id: String,
    val type: String = "",
    val postId: String = "",
    val commentId: String = "",
    val chatId: String = "",
    val actorId: String = "",
    val actorName: String = "",
    val preview: String = "",
    val createdAt: Long = 0L,
    val read: Boolean = false,
)

@Serializable
data class NotificationsPageDto(
    val notifications: List<NotificationDto> = emptyList(),
    val nextCursor: String? = null,
    val unreadCount: Long = 0L,
)

@Serializable
data class ReadAllResponse(
    val ok: Boolean = true,
    val unreadCount: Long = 0L,
)

@Serializable
data class FcmTokenRequest(
    val token: String,
    val language: String = "system",
)
