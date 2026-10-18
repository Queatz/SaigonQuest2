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
            ),
        )

    fun body(preview: String): String = preview

    fun path(postId: String): String = "/p/$postId"

    fun path(type: String, postId: String, chatId: String = ""): String = when (type) {
        "chat_removed", "chat_deleted" -> "/messages"
        "chat_message", "chat_added" -> if (chatId.isBlank()) "/messages" else "/messages/$chatId"
        else -> path(postId)
    }

    fun titleKey(type: String): String = when (type) {
        "thread_comment" -> "notificationThreadComment"
        "event_joined" -> "notificationEventJoined"
        "event_left" -> "notificationEventLeft"
        "event_friend_joined" -> "notificationEventFriendJoined"
        "event_starting_soon" -> "notificationEventStartingSoon"
        "event_starting_now" -> "notificationEventStartingNow"
        "poll_voted" -> "notificationPollVoted"
        "question_answered" -> "notificationQuestionAnswered"
        "chat_message" -> "notificationChatMessage"
        "chat_added" -> "notificationChatAdded"
        "chat_removed" -> "notificationChatRemoved"
        "chat_deleted" -> "notificationChatDeleted"
        else -> "notificationPostComment"
    }
}
