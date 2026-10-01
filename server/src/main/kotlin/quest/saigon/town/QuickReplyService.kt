package quest.saigon.town

import org.slf4j.LoggerFactory
import quest.saigon.Limits
import quest.saigon.ai.QuickReplyGenerator
import quest.saigon.ai.QuickReplyPrompts
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class QuickReplyService(
    private val repo: TownRepository,
    private val generator: QuickReplyGenerator = QuickReplyGenerator { QuickReplyPrompts.FALLBACK },
    private val configured: Boolean = false,
    private val executor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "quick-reply").apply { isDaemon = true }
    },
) {
    private val log = LoggerFactory.getLogger(QuickReplyService::class.java)

    fun enqueue(postId: String, message: String) {
        if (!configured) return
        executor.execute {
            val replies = try {
                generator.generate(message)
            } catch (error: Exception) {
                log.warn("Quick replies for {} failed: {}", postId, error.message)
                emptyList()
            }
            val cleaned = replies.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(Limits.QUICK_REPLY_COUNT)
            if (cleaned.isNotEmpty()) repo.updateQuickReplies(postId, cleaned)
        }
    }
}
