package quest.saigon.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatFaceDto(
    val userId: String,
    val name: String = "",
    val photoUrl: String? = null,
)

@Serializable
data class ChatDto(
    val id: String,
    val type: String = "direct",
    val name: String = "",
    val photos: List<ChatFaceDto> = emptyList(),
    val lastMessageSenderName: String = "",
    val lastMessageText: String = "",
    val lastMessageAt: Long = 0L,
    val unreadCount: Int = 0,
    val peerId: String? = null,
    val owner: Boolean = false,
    val memberCount: Int = 0,
    val defaultReactions: List<String> = emptyList(),
    val openChatRemaining: Long? = null,
)

@Serializable
data class ChatsPageDto(
    val chats: List<ChatDto> = emptyList(),
    val nextCursor: String? = null,
    val unreadCount: Long = 0L,
)

@Serializable
data class ChatAttachmentDto(
    val type: String = "",
    val productId: String? = null,
    val productName: String? = null,
    val price: Long? = null,
    val photoUrl: String? = null,
    val activityId: String? = null,
    val activityName: String? = null,
    val activityEmoji: String? = null,
    val activityDescription: String? = null,
)

@Serializable
data class ReactionCountDto(
    val emoji: String,
    val count: Long = 0L,
    val mine: Boolean = false,
)

@Serializable
data class ReactionDetailDto(
    val id: String,
    val emoji: String,
    val userId: String,
    val name: String = "",
    val photoUrl: String? = null,
    val mine: Boolean = false,
)

@Serializable
data class ReactionsResponse(
    val reactions: List<ReactionCountDto> = emptyList(),
)

@Serializable
data class ReactionDetailsResponse(
    val reactions: List<ReactionDetailDto> = emptyList(),
)

@Serializable
data class ChatMessageDto(
    val id: String,
    val chatId: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String? = null,
    val text: String = "",
    val photoUrls: List<String> = emptyList(),
    val attachment: ChatAttachmentDto? = null,
    val createdAt: Long = 0L,
    val reactions: List<ReactionCountDto> = emptyList(),
)

@Serializable
data class ChatMessagesPageDto(
    val messages: List<ChatMessageDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class ChatMemberDto(
    val id: String,
    val name: String = "",
    val photoUrl: String? = null,
    val location: String = "",
    val intro: String = "",
    val joinedAt: Long = 0L,
    val owner: Boolean = false,
    val phone: String? = null,
    val zaloUrl: String? = null,
)

@Serializable
data class ChatMembersResponse(
    val members: List<ChatMemberDto> = emptyList(),
)

@Serializable
data class CreateChatRequest(
    val userId: String? = null,
    val text: String? = null,
    val name: String? = null,
)

@Serializable
data class RenameChatRequest(
    val name: String,
)

@Serializable
data class AddChatMemberRequest(
    val userId: String,
)

@Serializable
data class SendChatMessageRequest(
    val text: String,
)

@Serializable
data class ReactMessageRequest(
    val emoji: String,
)

@Serializable
data class UnreadCountResponse(
    val ok: Boolean = true,
    val unreadCount: Long = 0L,
)

@Serializable
data class ChatStreamEventDto(
    val type: String = "",
    val unreadCount: Long? = null,
    val chatId: String? = null,
    val chat: ChatDto? = null,
    val message: ChatMessageDto? = null,
    val messageId: String? = null,
    val reactions: List<ReactionCountDto>? = null,
    val userId: String? = null,
    val userName: String? = null,
)
