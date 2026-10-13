package quest.saigon

object NotificationCopy {
    fun title(language: Language, type: String, actorName: String, preview: String): String =
        Copy.text(
            language,
            titleKey(type),
            mapOf(
                "name" to actorName,
                "event" to preview,
            ),
        )

    fun body(preview: String): String = preview

    fun path(postId: String): String = "/p/$postId"

    fun titleKey(type: String): String = when (type) {
        "thread_comment" -> "notificationThreadComment"
        "event_joined" -> "notificationEventJoined"
        "event_left" -> "notificationEventLeft"
        "event_friend_joined" -> "notificationEventFriendJoined"
        "event_starting_soon" -> "notificationEventStartingSoon"
        "event_starting_now" -> "notificationEventStartingNow"
        else -> "notificationPostComment"
    }
}
