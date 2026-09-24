package quest.saigon

data class HealthStatus(
    val connected: Boolean,
    val database: String,
    val version: String? = null,
    val error: String? = null,
) {
    fun toJson(): String {
        val errorJson = error?.let { """"${it.jsonEscape()}"""" } ?: "null"
        val versionJson = version?.let { """"${it.jsonEscape()}"""" } ?: "null"
        val status = if (connected) "ok" else "degraded"
        return """{"status":"$status","arango":{"connected":$connected,"database":"${database.jsonEscape()}","version":$versionJson,"error":$errorJson}}"""
    }
}

fun interface DatabaseHealth {
    fun check(): HealthStatus
}

private fun String.jsonEscape(): String =
    buildString(length) {
        for (char in this@jsonEscape) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
