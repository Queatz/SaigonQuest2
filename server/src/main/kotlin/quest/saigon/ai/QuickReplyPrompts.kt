package quest.saigon.ai

import quest.saigon.Limits

fun interface QuickReplyGenerator {
    fun generate(message: String): List<String>
}

class QuickReplyPipeline(
    private val qwen: QwenClient,
) : QuickReplyGenerator {
    override fun generate(message: String): List<String> =
        QuickReplyPrompts.parse(qwen.chat(QuickReplyPrompts.SYSTEM, message))
}

object QuickReplyPrompts {
    val FALLBACK = listOf(
        "I'm down 🙋‍♂️",
        "What time? ⌚",
        "Let's ride 🏍️",
        "Which room? 📍",
        "Count me in ✨",
    )

    const val SYSTEM = """
        You write 5 very short, casual quick replies for a Saigon social feed post.
        Replies are things a friend would tap to send, like "I'm down! What time? 🙋‍♂️".
        Mix English and Vietnamese freely when it feels natural.
        Each reply is under 40 characters and may include one emoji.
        Output a JSON array of 5 strings only. No markdown.
    """

    fun parse(raw: String?): List<String> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return FALLBACK
        val fromJson = jsonStrings(text)
        val lines = if (fromJson.isNotEmpty()) {
            fromJson
        } else {
            text.lines().map { it.trim().trimStart('-', '*', '•', '1', '2', '3', '4', '5', '.', ')', ' ').trim() }
        }
        val cleaned = lines.map(::clean).filter { it.isNotBlank() }.distinct().take(Limits.QUICK_REPLY_COUNT)
        return if (cleaned.size >= 3) cleaned else FALLBACK
    }

    private fun jsonStrings(text: String): List<String> {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val body = text.substring(start + 1, end)
        val found = mutableListOf<String>()
        val regex = Regex("\"((?:\\\\.|[^\"])*)\"")
        regex.findAll(body).forEach { match ->
            found += match.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\n", " ")
                .replace("\\\\", "\\")
        }
        return found
    }

    private fun clean(value: String): String =
        value.replace(Regex("\\s+"), " ").trim().trim('"').take(60)
}
