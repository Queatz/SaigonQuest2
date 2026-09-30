package quest.saigon.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

object QwenResponses {
    private val mapper = ObjectMapper()

    fun parse(json: String): JsonNode = mapper.readTree(json)

    fun audioUrl(node: JsonNode): String? {
        val direct = node.path("output").path("audio").path("url")
        if (direct.isTextual) {
            val url = direct.asText().trim()
            if (url.startsWith("http")) return url
        }
        return imageUrls(node).firstOrNull { url ->
            url.contains(".mp3", true) || "fun-music" in url || "/audio" in url
        }
    }

    fun lyrics(node: JsonNode): String? =
        text(node.path("output").path("extra_info"), "lyrics")
            ?: text(node.path("extra_info"), "lyrics")
            ?: text(node.path("output"), "lyrics")
            ?: text(node, "lyrics")

    fun imageUrls(node: JsonNode): List<String> {
        val found = mutableListOf<String>()
        walk(node, found)
        return found.distinct()
    }

    fun taskId(node: JsonNode): String? {
        val direct = text(node, "task_id")
        if (!direct.isNullOrBlank()) return direct
        return text(node.path("output"), "task_id")
    }

    fun taskStatus(node: JsonNode): String? {
        val direct = text(node, "task_status")
        if (!direct.isNullOrBlank()) return direct
        return text(node.path("output"), "task_status")
    }

    fun errorMessage(node: JsonNode): String? {
        val message = text(node, "message") ?: text(node.path("output"), "message")
        val code = text(node, "code") ?: text(node.path("output"), "code")
        return listOfNotNull(code, message).joinToString(": ").ifBlank { null }
    }

    fun chatText(node: JsonNode): String? {
        val content = node.path("choices").path(0).path("message").path("content")
        return when {
            content.isTextual -> content.asText().trim().ifBlank { null }
            content.isArray -> content.mapNotNull { item ->
                text(item, "text") ?: item.takeIf { it.isTextual }?.asText()
            }.joinToString("\n").trim().ifBlank { null }
            else -> null
        }
    }

    private fun walk(node: JsonNode, found: MutableList<String>) {
        when {
            node.isObject -> {
                val fields = node.fields()
                while (fields.hasNext()) {
                    val field = fields.next()
                    val value = field.value
                    if (value.isTextual && field.key in URL_KEYS && value.asText().startsWith("http")) {
                        found += value.asText()
                    } else {
                        walk(value, found)
                    }
                }
            }
            node.isArray -> node.forEach { walk(it, found) }
        }
    }

    private fun text(node: JsonNode, field: String): String? =
        node.path(field).takeIf { it.isTextual }?.asText()?.trim()?.ifBlank { null }

    private val URL_KEYS = setOf("image", "url", "output_image_url", "output_vis_image_url")
}
