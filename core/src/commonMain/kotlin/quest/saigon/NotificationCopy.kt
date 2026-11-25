package quest.saigon

object NotificationCopy {
    fun title(language: Language, type: String, actorName: String, preview: String): String =
        Copy.text(
            language,
            titleKey(type),
            mapOf(
                "name" to actorName,
                "event" to preview,
                "poll" to preview,
                "want" to preview,
            ),
        )

    fun body(preview: String): String = preview

    fun path(postId: String): String = "/p/$postId"

    fun path(type: String, postId: String, chatId: String = "", actorId: String = ""): String = when (type) {
        "product_order" -> "/profile/orders"
        "want_joined" -> actorId.takeIf { it.isNotBlank() }?.let { "/u/$it" } ?: "/profile"
        "chat_removed", "chat_deleted" -> "/messages"
        "chat_message", "chat_added" -> if (chatId.isBlank()) "/messages" else "/messages/$chatId"
        else -> path(postId)
    }

    fun titleKey(type: String): String = when (type) {
        "want_joined" -> "notificationWantJoined"
        "comment_reply" -> "notificationCommentReply"
        "thread_comment" -> "notificationThreadComment"
        "event_joined" -> "notificationEventJoined"
        "event_left" -> "notificationEventLeft"
        "event_friend_joined" -> "notificationEventFriendJoined"
        "event_starting_soon" -> "notificationEventStartingSoon"
        "event_starting_now" -> "notificationEventStartingNow"
        "poll_voted" -> "notificationPollVoted"
        "question_answered" -> "notificationQuestionAnswered"
        "product_order" -> "notificationProductOrder"
        "chat_message" -> "notificationChatMessage"
        "chat_added" -> "notificationChatAdded"
        "chat_removed" -> "notificationChatRemoved"
        "chat_deleted" -> "notificationChatDeleted"
        else -> "notificationPostComment"
    }
}
