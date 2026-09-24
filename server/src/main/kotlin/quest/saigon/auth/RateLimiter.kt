package quest.saigon.auth

class RateLimiter(
    private val limit: Int,
    private val windowMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val hits = mutableMapOf<String, ArrayDeque<Long>>()

    fun allow(key: String): Boolean {
        val now = clock()
        synchronized(hits) {
            val window = hits.getOrPut(key) { ArrayDeque() }
            val cutoff = now - windowMs
            while (window.isNotEmpty() && window.first() <= cutoff) {
                window.removeFirst()
            }
            if (window.size >= limit) return false
            window.addLast(now)
            return true
        }
    }
}
