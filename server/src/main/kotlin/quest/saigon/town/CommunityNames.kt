package quest.saigon.town

import java.text.BreakIterator
import java.text.Normalizer

object CommunityNames {
    const val NAME_MAX = 32

    fun clean(raw: String): String? {
        val name = raw.trim().replace(Regex("\\s+"), " ")
        if (name.length !in 1..NAME_MAX) return null
        if (name.any { it.isISOControl() }) return null
        return name
    }

    fun cleanEmoji(raw: String): String {
        val grapheme = firstGrapheme(raw.trim())
        if (grapheme.isEmpty() || grapheme.any { it.isLetterOrDigit() }) return "✨"
        return grapheme
    }

    fun slug(name: String): String {
        val folded = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
        val slug = folded.replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return slug.ifBlank { "community" }.take(48)
    }

    fun label(emoji: String, name: String): String = "${cleanEmoji(emoji)} $name"

    private fun firstGrapheme(value: String): String {
        if (value.isEmpty()) return ""
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(value)
        val end = iterator.next()
        if (end == BreakIterator.DONE || end <= 0) return value.take(2)
        return value.substring(0, end)
    }
}

internal fun membershipKey(communityId: String, userId: String) = "${communityId}_$userId"
