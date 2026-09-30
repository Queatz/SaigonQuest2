package quest.saigon.ai

object MusicPrompts {
    const val TITLE_SYSTEM = """
        You name songs.
        Reply with only a short English song title, maximum 40 characters.
        Use English even if the idea is in another language.
        No quotes, no markdown, no explanation.
    """

    fun generationPrompt(userPrompt: String): String {
        val idea = userPrompt.trim()
        return "English song with English lyrics, even if the theme is in another language. Theme: $idea"
    }
}
