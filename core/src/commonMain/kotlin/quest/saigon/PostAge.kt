package quest.saigon

object PostAge {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val WEEK = 7 * DAY
    private const val MONTH = 30 * DAY

    fun relative(createdAt: Long, now: Long, language: Language): String? {
        if (createdAt <= 0L) return ""
        val delta = (now - createdAt).coerceAtLeast(0L)
        return when {
            delta < HOUR -> Copy.text(language, "minutesAgo", mapOf("count" to (delta / MINUTE).coerceAtLeast(1).toString()))
            delta < DAY -> Copy.text(language, "hoursAgo", mapOf("count" to (delta / HOUR).toString()))
            delta < WEEK -> Copy.text(language, "daysAgo", mapOf("count" to (delta / DAY).toString()))
            delta < MONTH -> Copy.text(language, "weeksAgo", mapOf("count" to (delta / WEEK).toString()))
            else -> null
        }
    }
}
