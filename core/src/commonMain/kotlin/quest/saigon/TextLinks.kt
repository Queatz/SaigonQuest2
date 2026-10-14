package quest.saigon

object TextLinks {
    data class Piece(val text: String, val href: String?)

    private val pattern = Regex(
        """https?://[^\s<>]+|(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,}(?::[0-9]{2,5})?(?:/[^\s<>]*)?""",
        RegexOption.IGNORE_CASE,
    )
    private const val TRAILING = ".,;:!?)]}\"'>"

    fun pieces(text: String): List<Piece> {
        if (text.isEmpty()) return emptyList()
        val out = mutableListOf<Piece>()
        var last = 0
        pattern.findAll(text).forEach { match ->
            val start = match.range.first
            if (start > 0) {
                val prev = text[start - 1]
                if (prev.isLetterOrDigit() || prev == '@' || prev == '/' || prev == '.' || prev == '_') return@forEach
            }
            var raw = match.value
            while (raw.isNotEmpty() && raw.last() in TRAILING) raw = raw.dropLast(1)
            if (raw.isEmpty()) return@forEach
            if (start > last) out += Piece(text.substring(last, start), null)
            out += Piece(raw, hrefFor(raw))
            last = start + raw.length
        }
        if (last < text.length) out += Piece(text.substring(last), null)
        return out
    }

    private fun hrefFor(raw: String): String =
        if (raw.contains("://")) raw else "https://$raw"
}
