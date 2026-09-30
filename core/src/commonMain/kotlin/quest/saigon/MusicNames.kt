package quest.saigon

object MusicNames {
    fun fromPrompt(prompt: String, max: Int = Limits.MUSIC_NAME_MAX): String {
        val line = prompt.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "Music"
        return clip(line, max)
    }

    fun fromLyrics(lyrics: String?, max: Int = Limits.MUSIC_NAME_MAX): String? {
        if (lyrics.isNullOrBlank()) return null
        val line = lyrics.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("[") }
            ?: return null
        return clip(line, max)
    }

    fun cleanTitle(raw: String?, max: Int = Limits.MUSIC_NAME_MAX): String? {
        val title = raw
            ?.trim()
            ?.trim('"', '\'', '“', '”', '‘', '’')
            ?.replace('\n', ' ')
            ?.replace('\r', ' ')
            ?.trim()
        if (title.isNullOrBlank()) return null
        return clip(title, max)
    }

    private fun clip(value: String, max: Int): String {
        val collapsed = value.replace(Regex("\\s+"), " ").trim()
        if (collapsed.length <= max) return collapsed.ifBlank { "Music" }
        return collapsed.take(max).trimEnd().trimEnd(',', '.', ';', ':', '-', '—', ' ').ifBlank { "Music" }
    }
}
