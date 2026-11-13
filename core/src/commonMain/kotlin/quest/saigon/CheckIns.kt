package quest.saigon

object CheckIns {
    val durationOptionsMinutes = listOf(30, 60, 90, 120, 180, 240, 300, 360)

    fun endAt(now: Long, durationMinutes: Int): Long? {
        if (durationMinutes !in durationOptionsMinutes) return null
        val requestedEnd = now + durationMinutes * 60_000L
        val quantum = Limits.CHECK_IN_END_QUANTUM_MILLIS
        return ((requestedEnd + quantum - 1) / quantum) * quantum
    }
}