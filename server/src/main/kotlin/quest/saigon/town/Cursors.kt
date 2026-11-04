package quest.saigon.town

data class ProductOrderCursor(val resolved: Boolean, val createdAt: Long, val id: String)

data class ActivityCursor(val interestedCount: Long, val createdAt: Long, val id: String)

object Cursors {
    fun encode(createdAt: Long, id: String): String = "$createdAt:$id"

    fun encodeProductOrder(resolved: Boolean, createdAt: Long, id: String): String =
        "${if (resolved) 1 else 0}:$createdAt:$id"

    fun parse(raw: String?): Pair<Long, String>? {
        if (raw.isNullOrBlank()) return null
        val separator = raw.indexOf(':')
        if (separator <= 0 || separator == raw.lastIndex) return null
        val createdAt = raw.substring(0, separator).toLongOrNull() ?: return null
        val id = raw.substring(separator + 1)
        if (id.length !in 1..128 || id.any { !it.isLetterOrDigit() && it != '_' && it != '-' }) return null
        return createdAt to id
    }

    fun parseProductOrder(raw: String?): ProductOrderCursor? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(':', limit = 3)
        if (parts.size != 3) return null
        val resolved = when (parts[0]) {
            "0" -> false
            "1" -> true
            else -> return null
        }
        val createdAt = parts[1].toLongOrNull() ?: return null
        val id = parts[2]
        if (id.length !in 1..128 || id.any { !it.isLetterOrDigit() && it != '_' && it != '-' }) return null
        return ProductOrderCursor(resolved, createdAt, id)
    }

    fun encodeActivity(interestedCount: Long, createdAt: Long, id: String): String =
        "$interestedCount:$createdAt:$id"

    fun parseActivity(raw: String?): ActivityCursor? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(':', limit = 3)
        if (parts.size != 3) return null
        val interestedCount = parts[0].toLongOrNull() ?: return null
        val createdAt = parts[1].toLongOrNull() ?: return null
        val id = parts[2]
        if (id.length !in 1..128 || id.any { !it.isLetterOrDigit() && it != '_' && it != '-' }) return null
        return ActivityCursor(interestedCount, createdAt, id)
    }
}
