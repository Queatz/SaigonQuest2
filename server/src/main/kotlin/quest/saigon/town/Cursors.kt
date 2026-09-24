package quest.saigon.town

object Cursors {
    fun encode(createdAt: Long, id: String): String = "$createdAt:$id"

    fun parse(raw: String?): Pair<Long, String>? {
        if (raw.isNullOrBlank()) return null
        val separator = raw.indexOf(':')
        if (separator <= 0 || separator == raw.lastIndex) return null
        val createdAt = raw.substring(0, separator).toLongOrNull() ?: return null
        val id = raw.substring(separator + 1)
        if (id.length !in 1..128 || id.any { !it.isLetterOrDigit() && it != '_' && it != '-' }) return null
        return createdAt to id
    }
}
