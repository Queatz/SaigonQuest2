package quest.saigon.http

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

object Jsons {
    private val mapper = ObjectMapper()

    fun parse(text: String): JsonNode = try {
        mapper.readTree(text.ifBlank { "{}" })
    } catch (_: Exception) {
        mapper.createObjectNode()
    }

    fun write(value: Any?): String = mapper.writeValueAsString(value)

    fun text(node: JsonNode, field: String): String = node.path(field).asText("").trim()
}
