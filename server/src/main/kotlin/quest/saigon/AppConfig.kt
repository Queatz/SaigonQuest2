package quest.saigon

import java.io.File

data class AppConfig(
    val port: Int = 8080,
    val arango: ArangoConfig = ArangoConfig(),
    val uploadDir: File = File("uploads"),
    val qwenApiKey: String? = null,
    val qwenBaseUrl: String = "https://dashscope-intl.aliyuncs.com",
    val qwenTextModel: String = "qwen3.8-flash",
    val qwenImageModel: String = "qwen-image-3.0",
    val qwenSegmentModel: String = "image-instance-segmentation",
    val qwenSegmentBaseUrl: String = "https://dashscope.aliyuncs.com",
    val qwenWorkspaceId: String? = null,
    val cookieSecure: Boolean = false,
    val trustProxy: Boolean = false,
    val spaDir: File? = null,
) {
    val qwenConfigured: Boolean get() = !qwenApiKey.isNullOrBlank()

    companion object {
        fun load(env: (String) -> String? = System::getenv, envFile: File = File(".env")): AppConfig {
            val fileValues = EnvFile.read(envFile)
            fun value(name: String): String? = env(name)?.takeIf { it.isNotBlank() } ?: fileValues[name]
            val spa = value("SPA_DIR")?.let(::File)?.takeIf { it.isDirectory }
            return AppConfig(
                port = value("PORT")?.toIntOrNull() ?: 8080,
                arango = ArangoConfig.fromEnv { name -> value(name) },
                uploadDir = File(value("UPLOAD_DIR") ?: "uploads"),
                qwenApiKey = value("QWEN_API_KEY") ?: value("DASHSCOPE_API_KEY"),
                qwenBaseUrl = value("QWEN_BASE_URL") ?: "https://dashscope-intl.aliyuncs.com",
                qwenTextModel = value("QWEN_TEXT_MODEL") ?: "qwen3.8-flash",
                qwenImageModel = value("QWEN_IMAGE_MODEL") ?: "qwen-image-3.0",
                qwenSegmentModel = value("QWEN_SEGMENT_MODEL") ?: "image-instance-segmentation",
                qwenSegmentBaseUrl = value("QWEN_SEGMENT_BASE_URL") ?: "https://dashscope.aliyuncs.com",
                qwenWorkspaceId = value("QWEN_WORKSPACE_ID"),
                cookieSecure = value("COOKIE_SECURE") == "true",
                trustProxy = value("TRUST_PROXY") == "true",
                spaDir = spa,
            )
        }
    }
}

object EnvFile {
    fun read(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        val values = linkedMapOf<String, String>()
        file.readLines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val separator = line.indexOf('=')
            if (separator <= 0) return@forEach
            val key = line.substring(0, separator).trim()
            var value = line.substring(separator + 1).trim()
            if (value.length >= 2 && ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\'')))) {
                value = value.substring(1, value.length - 1)
            }
            values[key] = value
        }
        return values
    }
}
