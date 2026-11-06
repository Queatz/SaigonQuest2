package quest.saigon.ai

import com.fasterxml.jackson.databind.ObjectMapper

object TranslationPrompts {
    const val SYSTEM = """
        Translate every provided text field into each requested locale.
        Preserve the meaning, tone, names, and other proper nouns; do not add facts.
        If a field is already in a requested language, lightly clean it up without changing its meaning.
        Reply with JSON only. Use each requested locale as a top-level key and include every provided field as a text value.
        Keep fields blank when their source text is blank. Do not explain.
    """

    private val mapper = ObjectMapper()

    fun system(fields: Map<String, String>, locales: List<String>): String {
        val shape = locales.distinct().associateWith { locale -> fields.keys.associateWith { "..." } }
        return "$SYSTEM\nRequired JSON shape: ${mapper.writeValueAsString(shape)}"
    }

    fun user(fields: Map<String, String>, locales: List<String>): String =
        mapper.writeValueAsString(mapOf("locales" to locales, "fields" to fields))

    fun parse(
        raw: String?,
        fields: Map<String, String>,
        locales: List<String>,
    ): Map<String, Map<String, String>> {
        val text = raw?.trim().orEmpty()
        require(text.isNotEmpty()) { "translation_empty" }
        val root = mapper.readTree(text)
        require(root.isObject) { "translation_invalid_json" }
        return locales.distinct().associateWith { locale ->
            val translated = root.path(locale)
            require(translated.isObject) { "translation_missing_locale:$locale" }
            fields.keys.associateWith { field ->
                val value = translated.path(field)
                if (fields[field].isNullOrBlank()) {
                    require(value.isMissingNode || value.isNull || value.isTextual) { "translation_invalid_field:$locale:$field" }
                    ""
                } else {
                    require(value.isTextual) { "translation_missing_field:$locale:$field" }
                    value.asText().trim().takeIf { it.isNotEmpty() }
                        ?: throw IllegalArgumentException("translation_blank_field:$locale:$field")
                }
            }
        }
    }
}