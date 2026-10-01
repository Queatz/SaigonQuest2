package quest.saigon.town

import quest.saigon.Limits
import quest.saigon.http.Jsons

class CommentService(
    private val repo: TownRepository,
    private val hub: NotificationHub,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun add(actor: UserRecord, postId: String, text: String, replyToId: String?): CommentItem {
        val post = repo.findPost(postId) ?: throw TownException("not_found")
        val cleaned = cleanComment(text) ?: throw TownException("invalid_comment")
        val parent = replyToId?.takeIf { it.isNotBlank() }?.let { id ->
            val found = repo.findComment(id) ?: throw TownException("not_found")
            if (found.postId != postId) throw TownException("not_found")
            found
        }
        val stored = repo.insertComment(
            CommentRecord(
                id = "",
                postId = post.id,
                userId = actor.id,
                text = cleaned,
                replyToId = parent?.id,
                createdAt = clock(),
            ),
        )
        val item = repo.commentItem(stored.id) ?: throw TownException("not_found")
        notifyAbout(post, actor, item)
        return item
    }

    fun remove(actor: UserRecord, postId: String, commentId: String) {
        val post = repo.findPost(postId) ?: throw TownException("not_found")
        val comment = repo.findComment(commentId) ?: throw TownException("not_found")
        if (comment.postId != post.id) throw TownException("not_found")
        if (actor.id != comment.userId && actor.id != post.userId) throw TownException("not_found")
        repo.deleteComment(comment.id)
    }

    private fun notifyAbout(post: PostRecord, actor: UserRecord, item: CommentItem) {
        val notified = mutableSetOf<String>()
        if (post.userId != actor.id) {
            push(post.userId, NotificationType.POST_COMMENT, actor, item)
            notified += post.userId
        }
        repo.armedWatchers(post.id).forEach { userId ->
            if (userId != actor.id) {
                if (userId !in notified) {
                    push(userId, NotificationType.THREAD_COMMENT, actor, item)
                    notified += userId
                }
                repo.disarmCommentWatch(post.id, userId)
            }
        }
        repo.armCommentWatch(post.id, actor.id)
    }

    private fun push(userId: String, type: String, actor: UserRecord, item: CommentItem) {
        val notification = repo.insertNotification(
            NotificationRecord(
                id = "",
                userId = userId,
                type = type,
                postId = item.postId,
                commentId = item.id,
                actorId = actor.id,
                actorName = actor.name,
                preview = item.text.take(80),
                createdAt = item.createdAt,
            ),
        )
        hub.publish(userId, Jsons.write(notificationPayload(notification)))
    }

    companion object {
        fun cleanComment(raw: String): String? {
            val text = raw.replace("\r\n", "\n").trim()
            if (text.length !in 1..Limits.COMMENT_MAX) return null
            if (text.any { it.isISOControl() && it != '\n' }) return null
            return text
        }

        fun notificationPayload(item: NotificationRecord) = mapOf(
            "id" to item.id,
            "type" to item.type,
            "postId" to item.postId,
            "commentId" to item.commentId,
            "actorName" to item.actorName,
            "preview" to item.preview,
            "createdAt" to item.createdAt,
            "read" to (item.readAt != null),
        )
    }
}
