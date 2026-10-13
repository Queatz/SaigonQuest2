package quest.saigon.town

import quest.saigon.http.Jsons

class EventService(
    private val repo: TownRepository,
    private val hub: NotificationHub,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pusher: NotificationPusher = NoNotificationPush,
) {
    fun join(actor: UserRecord, postId: String): FeedItem {
        val post = repo.findPost(postId) ?: throw TownException("not_found")
        if (post.eventStartAt == null) throw TownException("not_found")
        val now = clock()
        if (repo.joinEvent(post.id, actor.id, now)) {
            notifyJoin(post, actor)
        }
        return repo.feedItem(post.id, actor.id) ?: throw TownException("not_found")
    }

    fun leave(actor: UserRecord, postId: String): FeedItem {
        val post = repo.findPost(postId) ?: throw TownException("not_found")
        if (post.eventStartAt == null) throw TownException("not_found")
        if (repo.leaveEvent(post.id, actor.id) && post.userId != actor.id) {
            push(post.userId, NotificationType.EVENT_LEFT, actor, post, actor.name)
        }
        return repo.feedItem(post.id, actor.id) ?: throw TownException("not_found")
    }

    fun tick(now: Long = clock()) {
        val from = now - 15 * 60_000L
        val to = now + 70 * 60_000L
        repo.eventPostsBetween(from, to).forEach { post ->
            val start = post.eventStartAt ?: return@forEach
            val hourDue = !post.eventHourNotified && now in (start - 60 * 60_000L) until start
            val nowDue = !post.eventNowNotified && now in start until (start + 15 * 60_000L)
            if (!hourDue && !nowDue) return@forEach
            val type = if (nowDue) NotificationType.EVENT_STARTING_NOW else NotificationType.EVENT_STARTING_SOON
            val recipients = (repo.attendeeIds(post.id) + post.userId).toSet()
            recipients.forEach { userId ->
                push(userId, type, null, post, eventTitle(post))
            }
            repo.markEventReminder(post.id, hour = hourDue || post.eventHourNotified, nowFlag = nowDue || post.eventNowNotified)
        }
    }

    fun start() {
        Thread({
            while (true) {
                try {
                    tick()
                } catch (_: Exception) {
                }
                try {
                    Thread.sleep(30_000)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }, "event-reminders").apply { isDaemon = true }.start()
    }

    private fun notifyJoin(post: PostRecord, actor: UserRecord) {
        if (post.userId != actor.id) {
            push(post.userId, NotificationType.EVENT_JOINED, actor, post, actor.name)
        }
        repo.attendeeIds(post.id).forEach { userId ->
            if (userId != actor.id && userId != post.userId) {
                push(userId, NotificationType.EVENT_FRIEND_JOINED, actor, post, actor.name)
            }
        }
    }

    private fun push(userId: String, type: String, actor: UserRecord?, post: PostRecord, preview: String) {
        val notification = repo.insertNotification(
            NotificationRecord(
                id = "",
                userId = userId,
                type = type,
                postId = post.id,
                commentId = "",
                actorId = actor?.id.orEmpty(),
                actorName = actor?.name.orEmpty(),
                preview = preview.take(80),
                createdAt = clock(),
            ),
        )
        hub.publish(userId, Jsons.write(CommentService.notificationPayload(notification)))
        pusher.notify(userId, notification)
    }

    companion object {
        fun eventTitle(post: PostRecord): String =
            post.message.trim().ifBlank { post.location }.ifBlank { "Event" }.take(80)
    }
}
