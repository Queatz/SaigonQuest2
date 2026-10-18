package quest.saigon

object CallToAction {
    const val MESSAGE = "message"
    const val ZALO = "zalo"
    const val WEBSITE = "website"

    val destinations = setOf(MESSAGE, ZALO, WEBSITE)

    data class Value(
        val title: String,
        val text: String,
        val destination: String,
        val url: String,
    )

    fun parse(title: String, text: String, destination: String, url: String): Value? {
        val dest = destination.trim().lowercase()
        if (dest !in destinations) return null
        val cleanedTitle = cleanTitle(title) ?: return null
        val cleanedText = cleanText(text) ?: return null
        val cleanedUrl = if (dest == WEBSITE) {
            cleanUrl(url) ?: return null
        } else {
            ""
        }
        return Value(cleanedTitle, cleanedText, dest, cleanedUrl)
    }

    fun cleanTitle(raw: String): String? {
        val text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.length > Limits.CTA_TITLE_MAX) return null
        if (text.any { it.isISOControl() }) return null
        return text
    }

    fun cleanText(raw: String): String? {
        val text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.length !in 1..Limits.CTA_TEXT_MAX) return null
        if (text.any { it.isISOControl() }) return null
        return text
    }

    fun cleanUrl(raw: String): String? {
        val text = raw.trim()
        if (text.length !in 1..Limits.CTA_URL_MAX) return null
        if (text.any { it.isISOControl() || it.isWhitespace() }) return null
        val href = when {
            text.contains("://") -> text
            ':' in text.substringBefore('/') -> return null
            else -> "https://$text"
        }
        val lower = href.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
        return href
    }

    fun httpUrl(raw: String?): String? = cleanUrl(raw.orEmpty())
}
