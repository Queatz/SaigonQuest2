package quest.saigon.ai

import quest.saigon.town.CommunityNames
import quest.saigon.town.CommunityRecord

data class CommunitySuggestion(val emoji: String, val name: String)

fun interface CommunitySuggester {
    fun suggest(message: String, existing: List<CommunityRecord>): CommunitySuggestion
}

object CommunityPrompts {
    const val SYSTEM = """
        You name one small community for a Saigon social post.
        Reply with JSON only: {"emoji":"🌱","name":"Vegans"}
        name is 1-3 words, Title Case, with no emoji inside the name.
        emoji is a single emoji that fits the topic.
        If the post clearly fits one of the existing communities, reuse that exact name.
        Do not explain.
    """

    fun user(message: String, existing: List<CommunityRecord>): String {
        val names = existing.take(30).joinToString(", ") { "${it.emoji} ${it.name}" }.ifBlank { "none" }
        return "Existing communities: $names\nPost:\n$message"
    }

    fun parse(raw: String?): CommunitySuggestion? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val emoji = field(text, "emoji")
        val name = field(text, "name")?.let(CommunityNames::clean) ?: return null
        return CommunitySuggestion(CommunityNames.cleanEmoji(emoji.orEmpty()), name)
    }

    fun fallback(message: String, existing: List<CommunityRecord>): CommunitySuggestion {
        val lower = message.lowercase()
        existing.firstOrNull { record ->
            val name = record.name.lowercase()
            name.length >= 3 && lower.contains(name)
        }?.let { return CommunitySuggestion(it.emoji, it.name) }
        rules.firstOrNull { (keys, _) -> keys.any { lower.contains(it) } }?.second?.let { return it }
        return CommunitySuggestion("✨", titleFrom(message))
    }

    private val rules = listOf(
        listOf("vegan", "vegans", "plant-based", "plant based", "ăn chay", "an chay") to CommunitySuggestion("🌱", "Vegans"),
        listOf("coffee", "cafe", "cà phê", "ca phe", "caphe") to CommunitySuggestion("☕", "Coffee"),
        listOf("music", "nhạc", "nhac", "song") to CommunitySuggestion("🎵", "Music"),
        listOf("bike", "motorbike", "xe máy", "xe may") to CommunitySuggestion("🏍️", "Riders"),
        listOf("food", "phở", "pho", "bánh", "banh", "eat") to CommunitySuggestion("🍜", "Food"),
        listOf("photo", "ảnh", "camera") to CommunitySuggestion("📷", "Photos"),
        listOf("cat", "mèo", "meo", "dog", "chó", "pet") to CommunitySuggestion("🐾", "Pets"),
    )

    private val stop = setOf(
        "the", "and", "for", "with", "this", "that", "just", "from", "have", "what", "when",
        "your", "you", "are", "was", "were", "một", "mot", "của", "cua", "và", "va", "cho",
        "này", "nay", "đang", "dang", "today", "lunch", "with",
    )

    private fun titleFrom(message: String): String {
        val words = message.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 && it !in stop }
            .take(2)
        if (words.isEmpty()) return "Town"
        return words.joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }.take(CommunityNames.NAME_MAX)
    }

    private fun field(text: String, name: String): String? {
        val match = Regex("\"$name\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").find(text) ?: return null
        return match.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\n", " ")
            .replace("\\\\", "\\")
            .trim()
    }
}
