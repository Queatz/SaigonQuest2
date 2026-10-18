package quest.saigon.town

import quest.saigon.Limits
import quest.saigon.http.Jsons

class QuestionService(
    private val repo: TownRepository,
    private val hub: NotificationHub,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pusher: NotificationPusher = NoNotificationPush,
) {
    fun answer(actor: UserRecord, postId: String, text: String): FeedItem {
        val post = repo.findPost(postId) ?: throw TownException("not_found")
        if (!post.hasQuestion) throw TownException("not_found")
        val cleaned = cleanAnswer(text) ?: throw TownException("invalid_answer")
        if (repo.findQuestionAnswer(post.id, actor.id) == null) {
            repo.insertQuestionAnswer(
                QuestionAnswerRecord(
                    id = "",
                    postId = post.id,
                    userId = actor.id,
                    text = cleaned,
                    createdAt = clock(),
                ),
            )
            notifyAnswer(post, actor, cleaned)
        }
        return repo.feedItem(post.id, actor.id) ?: throw TownException("not_found")
    }

    fun answers(actor: UserRecord, postId: String): List<QuestionAnswerItem> {
        val post = repo.findPost(postId) ?: throw TownException("not_found")
        if (!post.hasQuestion || post.userId != actor.id) throw TownException("not_found")
        return repo.questionAnswers(post.id)
    }

    private fun notifyAnswer(post: PostRecord, actor: UserRecord, preview: String) {
        if (post.userId == actor.id) return
        val notification = repo.insertNotification(
            NotificationRecord(
                id = "",
                userId = post.userId,
                type = NotificationType.QUESTION_ANSWERED,
                postId = post.id,
                commentId = "",
                actorId = actor.id,
                actorName = actor.name,
                preview = preview.take(80),
                createdAt = clock(),
            ),
        )
        hub.publish(post.userId, Jsons.write(CommentService.notificationPayload(notification)))
        pusher.notify(post.userId, notification)
    }

    companion object {
        fun cleanTitle(raw: String): String? {
            val text = raw.trim().replace(Regex("\\s+"), " ")
            if (text.length > Limits.QUESTION_TITLE_MAX) return null
            if (text.any { it.isISOControl() }) return null
            return text
        }

        fun cleanAnswer(raw: String): String? {
            val text = raw.replace("\r\n", "\n").trim()
            if (text.length !in 1..Limits.QUESTION_ANSWER_MAX) return null
            if (text.any { it.isISOControl() && it != '\n' }) return null
            return text
        }
    }
}
