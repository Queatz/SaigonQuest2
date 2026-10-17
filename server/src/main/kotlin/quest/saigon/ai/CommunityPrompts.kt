package quest.saigon.ai

import quest.saigon.town.CommunityNames
import quest.saigon.town.CommunityRecord

data class CommunitySuggestion(val emoji: String, val name: String)

fun interface CommunitySuggester {
    fun suggest(message: String, existing: List<CommunityRecord>): CommunitySuggestion
}

fun interface CommunityEmojiSuggester {
    fun suggest(name: String, existing: List<CommunityRecord>): String
}

object CommunityPrompts {
    const val SYSTEM = """
        You name one small community for a Saigon social post.
        Reply with JSON only: {"emoji":"🌱","name":"Vegans"}
        name is 1-3 words, with no emoji inside the name.
        Use the same language as the post: Vietnamese if the post is Vietnamese, English Title Case otherwise.
        emoji is a single emoji that fits the topic.
        If the post clearly fits one of the existing communities, reuse that exact name.
        Do not explain.
    """

    const val EMOJI_SYSTEM = """
        You pick one emoji for a new Saigon community name.
        Reply with JSON only: {"emoji":"🌱"}
        emoji is a single emoji that fits the name.
        If the name matches an existing community, reuse that emoji.
        Do not explain.
    """

    fun user(message: String, existing: List<CommunityRecord>): String {
        val names = existing.take(30).joinToString(", ") { "${it.emoji} ${it.name}" }.ifBlank { "none" }
        return "Existing communities: $names\nPost:\n$message"
    }

    fun emojiUser(name: String, existing: List<CommunityRecord>): String {
        val names = existing.take(30).joinToString(", ") { "${it.emoji} ${it.name}" }.ifBlank { "none" }
        return "Existing communities: $names\nCommunity name:\n$name"
    }

    fun parse(raw: String?): CommunitySuggestion? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val emoji = field(text, "emoji")
        val name = field(text, "name")?.let(CommunityNames::clean) ?: return null
        return CommunitySuggestion(CommunityNames.cleanEmoji(emoji.orEmpty()), name)
    }

    fun parseEmoji(raw: String?): String? {
        val emoji = field(raw?.trim().orEmpty(), "emoji") ?: return null
        return CommunityNames.cleanEmoji(emoji)
    }

    fun fallbackEmoji(name: String, existing: List<CommunityRecord>): String =
        fallback(name, existing).emoji

    fun fallback(message: String, existing: List<CommunityRecord>): CommunitySuggestion {
        val lower = message.lowercase()
        existing.firstOrNull { record ->
            val name = record.name.lowercase()
            name.length >= 3 && lower.contains(name)
        }?.let { return CommunitySuggestion(it.emoji, it.name) }
        rules.firstOrNull { rule -> rule.keys.any { lower.contains(it) } }?.let { rule ->
            return if (vietnamese(message) || rule.keys.any { lower.contains(it) && vietnamese(it) }) rule.vi else rule.en
        }
        return CommunitySuggestion("✨", titleFrom(message))
    }

    private data class Named(val keys: List<String>, val en: CommunitySuggestion, val vi: CommunitySuggestion)

    private val rules = listOf(
        Named(
            listOf("vegan", "vegans", "plant-based", "plant based", "ăn chay", "an chay"),
            CommunitySuggestion("🌱", "Vegans"),
            CommunitySuggestion("🌱", "Ăn chay"),
        ),
        Named(
            listOf("coffee", "cafe", "cà phê", "ca phe", "caphe"),
            CommunitySuggestion("☕", "Coffee"),
            CommunitySuggestion("☕", "Cà phê"),
        ),
        Named(
            listOf("music", "nhạc", "nhac", "song"),
            CommunitySuggestion("🎵", "Music"),
            CommunitySuggestion("🎵", "Nhạc"),
        ),
        Named(
            listOf("bike", "motorbike", "xe máy", "xe may"),
            CommunitySuggestion("🏍️", "Riders"),
            CommunitySuggestion("🏍️", "Xe máy"),
        ),
        Named(
            listOf("food", "phở", "pho", "bánh", "banh", "eat"),
            CommunitySuggestion("🍜", "Food"),
            CommunitySuggestion("🍜", "Đồ ăn"),
        ),
        Named(
            listOf("photo", "ảnh", "camera"),
            CommunitySuggestion("📷", "Photos"),
            CommunitySuggestion("📷", "Ảnh"),
        ),
        Named(
            listOf("cat", "mèo", "meo", "dog", "chó", "pet"),
            CommunitySuggestion("🐾", "Pets"),
            CommunitySuggestion("🐾", "Thú cưng"),
        ),
    )

    private fun vietnamese(text: String): Boolean {
        if (text.any { it in vietMarks }) return true
        val lower = text.lowercase()
        return vietHints.any { lower.contains(it) }
    }

    private val vietMarks = "àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđÀÁẠẢÃÂẦẤẬẨẪĂẰẮẶẲẴÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸĐ".toSet()

    private val vietHints = listOf("an chay", "ca phe", "caphe", "xe may", "nhac")

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
