package quest.saigon.town

import quest.saigon.http.Jsons

class PollService(
    private val repo: TownRepository,
    private val hub: NotificationHub,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pusher: NotificationPusher = NoNotificationPush,
) {
    fun vote(actor: UserRecord, postId: String, optionIndex: Int): FeedItem {
        val post = repo.findPost(postId) ?: throw TownException("not_found")
        if (post.pollOptions.isEmpty()) throw TownException("not_found")
        if (optionIndex !in post.pollOptions.indices) throw TownException("invalid_poll")
        val existing = repo.findPollVote(post.id, actor.id)
        when {
            existing == null -> {
                repo.castPollVote(post.id, actor.id, optionIndex, clock())
                notifyVote(post, actor)
            }
            existing.optionIndex == optionIndex -> repo.removePollVote(post.id, actor.id)
            else -> repo.castPollVote(post.id, actor.id, optionIndex, clock())
        }
        return repo.feedItem(post.id, actor.id) ?: throw TownException("not_found")
    }

    private fun notifyVote(post: PostRecord, actor: UserRecord) {
        if (post.userId == actor.id) return
        val notification = repo.insertNotification(
            NotificationRecord(
                id = "",
                userId = post.userId,
                type = NotificationType.POLL_VOTED,
                postId = post.id,
                commentId = "",
                actorId = actor.id,
                actorName = actor.name,
                preview = pollTitle(post),
                createdAt = clock(),
            ),
        )
        hub.publish(post.userId, Jsons.write(CommentService.notificationPayload(notification)))
        pusher.notify(post.userId, notification)
    }

    companion object {
        fun pollTitle(post: PostRecord): String =
            post.message.trim().ifBlank { post.location }.ifBlank { "Poll" }.take(80)
    }
}
