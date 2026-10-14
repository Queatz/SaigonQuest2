package quest.saigon

object PollNames {
    fun clean(raw: List<String>): List<String>? {
        val options = linkedSetOf<String>()
        for (item in raw) {
            val text = item.trim().replace(Regex("\\s+"), " ")
            if (text.isEmpty()) continue
            if (text.length > Limits.POLL_OPTION_MAX) return null
            if (text.any { it.isISOControl() }) return null
            options += text
        }
        if (options.size !in Limits.POLL_OPTIONS_MIN..Limits.POLL_OPTIONS_MAX) return null
        return options.toList()
    }
}
