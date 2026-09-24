package quest.saigon

enum class Language {
    VI,
    EN,
}

fun resolveLanguage(preference: String?, browserLanguage: String?): Language {
    when (preference?.trim()?.lowercase()) {
        "vi" -> return Language.VI
        "en" -> return Language.EN
    }
    val browser = browserLanguage?.trim()?.lowercase().orEmpty()
    return if (browser.startsWith("vi")) Language.VI else Language.EN
}

fun languagePreference(raw: String?): String? =
    when (raw?.trim()?.lowercase()) {
        "system", "" , null -> "system"
        "vi" -> "vi"
        "en" -> "en"
        else -> null
    }
