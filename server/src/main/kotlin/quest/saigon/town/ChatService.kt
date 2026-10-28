package quest.saigon.town

import quest.saigon.Limits
import quest.saigon.http.Jsons

class ChatService(
    private val repo: TownRepository,
    private val hub: NotificationHub,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pusher: NotificationPusher = NoNotificationPush,
) {
    fun list(actor: UserRecord, cursor: String?, limit: Int): ChatPage =
        repo.chatsFor(actor.id, cursor, limit.coerceIn(1, 50))

    fun get(actor: UserRecord, chatId: String): ChatSummary {
        requireMember(actor, chatId)
        return repo.chatSummary(chatId, actor.id) ?: throw TownException("not_found")
    }

    fun findDirect(actor: UserRecord, userId: String): ChatSummary? {
        if (userId.isBlank() || userId == actor.id) return null
        val chat = repo.findDirectChat(actor.id, userId) ?: return null
        if (repo.findChatMember(chat.id, actor.id) == null) return null
        return repo.chatSummary(chat.id, actor.id)
    }

    fun createDirect(actor: UserRecord, userId: String, text: String, attachment: ChatAttachment? = null): ChatSummary {
        if (userId.isBlank() || userId == actor.id) throw TownException("not_found")
        val other = repo.findUser(userId) ?: throw TownException("not_found")
        val existing = repo.findDirectChat(actor.id, other.id)
        val chat = if (existing == null) {
            val now = clock()
            val created = repo.insertChat(
                ChatRecord(
                    id = "",
                    type = ChatType.DIRECT,
                    name = "",
                    createdBy = actor.id,
                    createdAt = now,
                    lastMessageAt = now,
                    pairKey = pairKey(actor.id, other.id),
                ),
            )
            repo.upsertChatMember(ChatMemberRecord(created.id, actor.id, owner = false, joinedAt = now))
            repo.upsertChatMember(ChatMemberRecord(created.id, other.id, owner = false, joinedAt = now))
            created
        } else {
            val now = clock()
            if (repo.findChatMember(existing.id, actor.id) == null) {
                repo.upsertChatMember(ChatMemberRecord(existing.id, actor.id, owner = false, joinedAt = now))
            }
            if (repo.findChatMember(existing.id, other.id) == null) {
                repo.upsertChatMember(ChatMemberRecord(existing.id, other.id, owner = false, joinedAt = now))
            }
            existing
        }
        val cleaned = cleanMessage(text) ?: throw TownException("invalid_chat_message")
        if (cleaned.isEmpty() && attachment == null) throw TownException("invalid_chat_message")
        send(actor, chat.id, cleaned, emptyList(), attachment)
        return repo.chatSummary(chat.id, actor.id) ?: throw TownException("not_found")
    }

    fun createGroup(actor: UserRecord, name: String): ChatSummary {
        val cleaned = cleanName(name) ?: throw TownException("invalid_name")
        val now = clock()
        val chat = repo.insertChat(
            ChatRecord(
                id = "",
                type = ChatType.GROUP,
                name = cleaned,
                createdBy = actor.id,
                createdAt = now,
                lastMessageAt = now,
            ),
        )
        repo.upsertChatMember(ChatMemberRecord(chat.id, actor.id, owner = true, joinedAt = now))
        val summary = repo.chatSummary(chat.id, actor.id) ?: throw TownException("not_found")
        publish(actor.id, event("chat", actor.id, summary = summary))
        return summary
    }

    fun messages(actor: UserRecord, chatId: String, cursor: String?, limit: Int): ChatMessagePage {
        requireMember(actor, chatId)
        return repo.chatMessages(chatId, cursor, limit.coerceIn(1, Limits.CHAT_MESSAGE_PAGE_SIZE), actor.id)
    }

    fun send(
        actor: UserRecord,
        chatId: String,
        text: String,
        photoPaths: List<String>,
        attachment: ChatAttachment? = null,
    ): ChatMessageItem {
        requireMember(actor, chatId)
        val cleaned = cleanMessage(text) ?: throw TownException("invalid_chat_message")
        if (cleaned.isEmpty() && photoPaths.isEmpty() && attachment == null) throw TownException("invalid_chat_message")
        if (photoPaths.size > Limits.PHOTOS_MAX) throw TownException("too_many_photos")
        val chat = repo.findChat(chatId) ?: throw TownException("not_found")
        val now = clock()
        val stored = repo.insertChatMessage(
            ChatMessageRecord(
                id = "",
                chatId = chat.id,
                userId = actor.id,
                text = cleaned,
                photoPaths = photoPaths,
                createdAt = now,
                attachment = attachment,
            ),
        )
        val preview = cleaned.ifBlank { attachment?.productName ?: "📷" }.take(80)
        repo.updateChat(
            chat.copy(
                lastMessageAt = now,
                lastMessageId = stored.id,
                lastMessageText = preview,
                lastMessageSenderId = actor.id,
                lastMessageSenderName = actor.name,
            ),
        )
        repo.incrementChatUnread(chat.id, actor.id)
        val item = repo.chatMessageItem(stored.id, actor.id) ?: throw TownException("not_found")
        repo.chatMemberIds(chat.id).forEach { userId ->
            if (userId != actor.id) {
                notify(userId, NotificationType.CHAT_MESSAGE, actor, chat.id, preview)
            }
            val summary = repo.chatSummary(chat.id, userId) ?: return@forEach
            publish(userId, event("message", userId, summary = summary, message = item))
        }
        return item
    }

    fun deleteMessage(actor: UserRecord, chatId: String, messageId: String): List<String> {
        val member = requireMember(actor, chatId)
        val chat = repo.findChat(chatId) ?: throw TownException("not_found")
        val message = repo.findChatMessage(messageId) ?: throw TownException("not_found")
        if (message.chatId != chat.id) throw TownException("not_found")
        val canDelete = message.userId == actor.id || (chat.type == ChatType.GROUP && member.owner)
        if (!canDelete) throw TownException("not_found")
        repo.deleteChatMessage(message.id)
        if (chat.lastMessageId == message.id) {
            val latest = repo.chatMessages(chat.id, null, 1, actor.id).items.lastOrNull()
            repo.updateChat(
                chat.copy(
                    lastMessageId = latest?.id.orEmpty(),
                    lastMessageText = latest?.text?.ifBlank {
                        latest.attachment?.productName ?: if (latest.photoPaths.isNotEmpty()) "📷" else ""
                    }.orEmpty().take(80),
                    lastMessageSenderId = latest?.userId.orEmpty(),
                    lastMessageSenderName = latest?.authorName.orEmpty(),
                    lastMessageAt = latest?.createdAt ?: chat.createdAt,
                ),
            )
        }
        repo.chatMemberIds(chat.id).forEach { userId ->
            val summary = repo.chatSummary(chat.id, userId)
            publish(
                userId,
                event("message_deleted", userId, summary = summary, extra = mapOf("messageId" to message.id, "chatId" to chat.id)),
            )
        }
        return message.photoPaths
    }

    fun read(actor: UserRecord, chatId: String) {
        requireMember(actor, chatId)
        repo.markChatRead(chatId, actor.id, clock())
        repo.markChatNotificationsRead(actor.id, chatId, clock())
        val summary = repo.chatSummary(chatId, actor.id) ?: return
        publish(actor.id, event("chat", actor.id, summary = summary))
    }

    fun leave(actor: UserRecord, chatId: String): List<String> {
        val chat = repo.findChat(chatId) ?: throw TownException("not_found")
        requireMember(actor, chatId)
        if (chat.type == ChatType.DIRECT) {
            return destroy(chat, actor, notifyMembers = false)
        }
        repo.deleteChatMember(chat.id, actor.id)
        val remaining = repo.chatMemberIds(chat.id)
        if (remaining.isEmpty()) {
            return destroy(chat, actor, notifyMembers = false)
        }
        if (repo.chatMembers(chat.id).none { it.owner }) {
            val next = remaining.first()
            val member = repo.findChatMember(chat.id, next) ?: return emptyList()
            repo.upsertChatMember(member.copy(owner = true))
        }
        remaining.forEach { userId ->
            val summary = repo.chatSummary(chat.id, userId) ?: return@forEach
            publish(userId, event("chat", userId, summary = summary))
        }
        publish(actor.id, event("chat_deleted", actor.id, extra = mapOf("chatId" to chat.id)))
        return emptyList()
    }

    fun deleteGroup(actor: UserRecord, chatId: String): List<String> {
        val chat = repo.findChat(chatId) ?: throw TownException("not_found")
        val member = requireMember(actor, chatId)
        if (chat.type != ChatType.GROUP || !member.owner) throw TownException("not_found")
        return destroy(chat, actor, notifyMembers = true)
    }

    fun renameGroup(actor: UserRecord, chatId: String, name: String): ChatSummary {
        val member = requireMember(actor, chatId)
        val chat = repo.findChat(chatId) ?: throw TownException("not_found")
        if (chat.type != ChatType.GROUP || !member.owner) throw TownException("not_found")
        val cleaned = cleanName(name) ?: throw TownException("invalid_name")
        repo.updateChat(chat.copy(name = cleaned))
        repo.chatMemberIds(chat.id).forEach { memberId ->
            val summary = repo.chatSummary(chat.id, memberId) ?: return@forEach
            publish(memberId, event("chat", memberId, summary = summary))
        }
        return repo.chatSummary(chat.id, actor.id) ?: throw TownException("not_found")
    }

    fun members(actor: UserRecord, chatId: String): List<ChatMemberView> {
        requireMember(actor, chatId)
        return repo.chatMembers(chatId)
    }

    fun addMember(actor: UserRecord, chatId: String, userId: String): ChatSummary {
        requireMember(actor, chatId)
        val chat = repo.findChat(chatId) ?: throw TownException("not_found")
        if (chat.type != ChatType.GROUP) throw TownException("not_found")
        val other = repo.findUser(userId) ?: throw TownException("not_found")
        if (repo.findChatMember(chat.id, other.id) != null) throw TownException("already_member")
        repo.upsertChatMember(
            ChatMemberRecord(chat.id, other.id, owner = false, joinedAt = clock(), unreadCount = 1),
        )
        notify(other.id, NotificationType.CHAT_ADDED, actor, chat.id, chat.name.ifBlank { actor.name })
        (repo.chatMemberIds(chat.id)).forEach { memberId ->
            val summary = repo.chatSummary(chat.id, memberId) ?: return@forEach
            publish(memberId, event("chat", memberId, summary = summary))
        }
        return repo.chatSummary(chat.id, actor.id) ?: throw TownException("not_found")
    }

    fun removeMember(actor: UserRecord, chatId: String, userId: String): ChatSummary {
        val member = requireMember(actor, chatId)
        val chat = repo.findChat(chatId) ?: throw TownException("not_found")
        if (chat.type != ChatType.GROUP || !member.owner) throw TownException("not_found")
        if (userId == actor.id) throw TownException("not_found")
        val target = repo.findChatMember(chat.id, userId) ?: throw TownException("not_found")
        repo.deleteChatMember(chat.id, target.userId)
        notify(target.userId, NotificationType.CHAT_REMOVED, actor, chat.id, chat.name.ifBlank { actor.name })
        publish(target.userId, event("chat_deleted", target.userId, extra = mapOf("chatId" to chat.id)))
        repo.chatMemberIds(chat.id).forEach { memberId ->
            val summary = repo.chatSummary(chat.id, memberId) ?: return@forEach
            publish(memberId, event("chat", memberId, summary = summary))
        }
        return repo.chatSummary(chat.id, actor.id) ?: throw TownException("not_found")
    }

    fun makeOwner(actor: UserRecord, chatId: String, userId: String): ChatSummary {
        val member = requireMember(actor, chatId)
        val chat = repo.findChat(chatId) ?: throw TownException("not_found")
        if (chat.type != ChatType.GROUP || !member.owner) throw TownException("not_found")
        val target = repo.findChatMember(chat.id, userId) ?: throw TownException("not_found")
        repo.upsertChatMember(target.copy(owner = true))
        repo.chatMemberIds(chat.id).forEach { memberId ->
            val summary = repo.chatSummary(chat.id, memberId) ?: return@forEach
            publish(memberId, event("chat", memberId, summary = summary))
        }
        return repo.chatSummary(chat.id, actor.id) ?: throw TownException("not_found")
    }

    fun react(actor: UserRecord, chatId: String, messageId: String, emoji: String): List<ReactionCount> {
        requireMember(actor, chatId)
        val message = repo.findChatMessage(messageId) ?: throw TownException("not_found")
        if (message.chatId != chatId) throw TownException("not_found")
        val cleaned = cleanReaction(emoji) ?: throw TownException("invalid_reaction")
        val existing = repo.findChatReaction(message.id, actor.id, cleaned)
        if (existing == null) {
            repo.insertChatReaction(
                ChatReactionRecord(
                    id = "",
                    chatId = chatId,
                    messageId = message.id,
                    userId = actor.id,
                    emoji = cleaned,
                    createdAt = clock(),
                ),
            )
        }
        return publishReactions(chatId, message.id, actor.id)
    }

    fun unreact(actor: UserRecord, chatId: String, messageId: String, emoji: String): List<ReactionCount> {
        requireMember(actor, chatId)
        val message = repo.findChatMessage(messageId) ?: throw TownException("not_found")
        if (message.chatId != chatId) throw TownException("not_found")
        val cleaned = cleanReaction(emoji) ?: throw TownException("invalid_reaction")
        val existing = repo.findChatReaction(message.id, actor.id, cleaned) ?: return repo.chatReactionCounts(message.id, actor.id)
        repo.deleteChatReaction(existing.id)
        return publishReactions(chatId, message.id, actor.id)
    }

    fun reactionDetails(actor: UserRecord, chatId: String, messageId: String): List<ReactionDetail> {
        requireMember(actor, chatId)
        val message = repo.findChatMessage(messageId) ?: throw TownException("not_found")
        if (message.chatId != chatId) throw TownException("not_found")
        return repo.chatReactionDetails(message.id, actor.id)
    }

    fun defaultReactions(chatId: String): List<String> {
        val top = repo.topChatReactions(chatId, 5)
        return (top + CORE_REACTIONS.filter { it !in top }).take(5)
    }

    fun typing(actor: UserRecord, chatId: String) {
        requireMember(actor, chatId)
        val photo = actor.profilePhotoPath?.let { "/api/users/${actor.id}/photo" }
        repo.chatMemberIds(chatId).forEach { userId ->
            if (userId == actor.id) return@forEach
            publish(
                userId,
                event(
                    "typing",
                    userId,
                    extra = mapOf(
                        "chatId" to chatId,
                        "userId" to actor.id,
                        "name" to actor.name,
                        "photoUrl" to photo,
                    ),
                ),
            )
        }
    }

    fun searchPeople(actor: UserRecord, query: String, exclude: Set<String>, limit: Int): List<EventAttendee> =
        repo.searchPeople(query, exclude + actor.id, limit.coerceIn(1, 30))

    fun searchPeopleToAdd(actor: UserRecord, chatId: String, query: String, limit: Int): List<EventAttendee> {
        requireMember(actor, chatId)
        return searchPeople(actor, query, repo.chatMemberIds(chatId).toSet(), limit)
    }

    fun markListNoticesRead(actor: UserRecord) {
        repo.markChatNoticeTypesRead(
            actor.id,
            listOf(NotificationType.CHAT_REMOVED, NotificationType.CHAT_DELETED),
            clock(),
        )
    }

    private fun destroy(chat: ChatRecord, actor: UserRecord, notifyMembers: Boolean): List<String> {
        val members = repo.chatMemberIds(chat.id)
        val photos = repo.chatPhotoPaths(chat.id)
        repo.deleteChat(chat.id)
        members.forEach { userId ->
            if (notifyMembers && userId != actor.id) {
                notify(userId, NotificationType.CHAT_DELETED, actor, chat.id, chat.name.ifBlank { actor.name })
            }
            publish(userId, event("chat_deleted", userId, extra = mapOf("chatId" to chat.id)))
        }
        return photos
    }

    private fun publishReactions(chatId: String, messageId: String, viewerId: String): List<ReactionCount> {
        repo.chatMemberIds(chatId).forEach { userId ->
            val item = repo.chatMessageItem(messageId, userId)
            val counts = item?.reactions ?: repo.chatReactionCounts(messageId, userId)
            publish(
                userId,
                event(
                    "reaction",
                    userId,
                    message = item,
                    extra = mapOf(
                        "chatId" to chatId,
                        "messageId" to messageId,
                        "reactions" to counts.map(::reactionPayload),
                    ),
                ),
            )
        }
        return repo.chatReactionCounts(messageId, viewerId)
    }

    private fun requireMember(actor: UserRecord, chatId: String): ChatMemberRecord =
        repo.findChatMember(chatId, actor.id) ?: throw TownException("not_found")

    private fun notify(userId: String, type: String, actor: UserRecord, chatId: String, preview: String) {
        val notification = repo.insertNotification(
            NotificationRecord(
                id = "",
                userId = userId,
                type = type,
                postId = "",
                commentId = "",
                actorId = actor.id,
                actorName = actor.name,
                preview = preview.take(80),
                createdAt = clock(),
                chatId = chatId,
            ),
        )
        pusher.notify(userId, notification)
    }

    private fun publish(userId: String, payload: Map<String, Any?>) {
        hub.publish(userId, Jsons.write(payload))
    }

    private fun event(
        type: String,
        userId: String,
        summary: ChatSummary? = null,
        message: ChatMessageItem? = null,
        extra: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> {
        val payload = mutableMapOf<String, Any?>(
            "type" to type,
            "unreadCount" to repo.unreadChatNotificationCount(userId),
        )
        if (summary != null) payload["chat"] = chatPayload(summary)
        if (message != null) payload["message"] = messagePayload(message)
        payload.putAll(extra)
        return payload
    }

    companion object {
        val CORE_REACTIONS = listOf("❤️", "👍", "😂", "😮", "😢")

        fun pairKey(a: String, b: String): String {
            val first = minOf(a, b)
            val second = maxOf(a, b)
            return "$first:$second"
        }

        fun cleanName(raw: String): String? {
            val name = raw.trim().replace(Regex("\\s+"), " ")
            if (name.length > Limits.CHAT_NAME_MAX) return null
            if (name.any { it.isISOControl() }) return null
            return name
        }

        fun cleanMessage(raw: String): String? {
            val text = raw.replace("\r\n", "\n").trim()
            if (text.length > Limits.CHAT_MESSAGE_MAX) return null
            if (text.any { it.isISOControl() && it != '\n' }) return null
            return text
        }

        fun cleanReaction(raw: String): String? {
            val text = raw.trim()
            if (text.isEmpty() || text.length > Limits.CHAT_REACTION_MAX) return null
            if (text.any { it.isISOControl() }) return null
            return text
        }

        fun chatPayload(item: ChatSummary) = mapOf(
            "id" to item.id,
            "type" to item.type,
            "name" to item.name,
            "photos" to item.photos.map { face ->
                mapOf(
                    "userId" to face.userId,
                    "name" to face.name,
                    "photoUrl" to face.photoPath?.let { "/api/users/${face.userId}/photo" },
                )
            },
            "lastMessageSenderName" to item.lastMessageSenderName,
            "lastMessageText" to item.lastMessageText,
            "lastMessageAt" to item.lastMessageAt,
            "unreadCount" to item.unreadCount,
            "peerId" to item.peerId,
            "owner" to item.owner,
            "memberCount" to item.memberCount,
        )

        fun messagePayload(item: ChatMessageItem) = mapOf(
            "id" to item.id,
            "chatId" to item.chatId,
            "userId" to item.userId,
            "authorName" to item.authorName,
            "authorPhotoUrl" to item.authorPhotoPath?.let { "/api/users/${item.userId}/photo" },
            "text" to item.text,
            "photoUrls" to item.photoPaths.map { mediaUrl(item.chatId, it) },
            "attachment" to item.attachment?.let { attachment ->
                mapOf(
                    "type" to attachment.type,
                    "productId" to attachment.productId,
                    "productName" to attachment.productName,
                    "price" to attachment.price,
                    "photoUrl" to attachment.photoPath?.let { "/media/$it" },
                )
            },
            "createdAt" to item.createdAt,
            "reactions" to item.reactions.map(::reactionPayload),
        )

        fun reactionPayload(item: ReactionCount) = mapOf(
            "emoji" to item.emoji,
            "count" to item.count,
            "mine" to item.mine,
        )

        fun reactionDetailPayload(item: ReactionDetail) = mapOf(
            "id" to item.id,
            "emoji" to item.emoji,
            "userId" to item.userId,
            "name" to item.name,
            "photoUrl" to item.photoPath?.let { "/api/users/${item.userId}/photo" },
            "mine" to item.mine,
        )

        fun mediaUrl(chatId: String, path: String): String {
            val name = path.substringAfterLast('/')
            return "/api/chats/$chatId/media/$name"
        }
    }
}
