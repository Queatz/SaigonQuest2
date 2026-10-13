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
    val qwenMusicModel: String = "fun-music-v1",
    val qwenWorkspaceId: String? = null,
    val cookieSecure: Boolean = false,
    val trustProxy: Boolean = false,
    val spaDir: File? = null,
    val bindHost: String = "0.0.0.0",
    val corsAllowedHosts: List<CorsAllowedHost> = defaultCorsHosts(),
    val cookieSameSite: String = "Lax",
    val googleMapsApiKey: String? = null,
    val vapidPublicKey: String? = null,
    val vapidPrivateKey: String? = null,
    val vapidSubject: String = "mailto:hello@saigon.town",
) {
    val qwenConfigured: Boolean get() = !qwenApiKey.isNullOrBlank()
    val mapsConfigured: Boolean get() = !googleMapsApiKey.isNullOrBlank()
    val webPushConfigured: Boolean get() = !vapidPublicKey.isNullOrBlank() && !vapidPrivateKey.isNullOrBlank()

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
                qwenMusicModel = value("QWEN_MUSIC_MODEL") ?: "fun-music-v1",
                qwenWorkspaceId = value("QWEN_WORKSPACE_ID"),
                cookieSecure = value("COOKIE_SECURE") == "true",
                trustProxy = value("TRUST_PROXY") == "true",
                spaDir = spa,
                bindHost = value("BIND_HOST") ?: "0.0.0.0",
                corsAllowedHosts = parseCorsAllowedHosts(value("CORS_ALLOWED_HOSTS")),
                cookieSameSite = normalizeSameSite(value("COOKIE_SAMESITE")),
                googleMapsApiKey = value("GOOGLE_MAPS_API_KEY"),
                vapidPublicKey = value("VAPID_PUBLIC_KEY"),
                vapidPrivateKey = value("VAPID_PRIVATE_KEY"),
                vapidSubject = value("VAPID_SUBJECT") ?: "mailto:hello@saigon.town",
            )
        }
    }
}

data class CorsAllowedHost(
    val host: String,
    val schemes: List<String> = listOf("http", "https"),
)

fun defaultCorsHosts(): List<CorsAllowedHost> = listOf(
    CorsAllowedHost("localhost:3000"),
    CorsAllowedHost("127.0.0.1:3000"),
)

fun parseCorsAllowedHosts(raw: String?): List<CorsAllowedHost> {
    val entries = raw
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: return defaultCorsHosts()
    if (entries.isEmpty()) return defaultCorsHosts()
    return entries.map(::parseCorsHost)
}

fun parseCorsHost(entry: String): CorsAllowedHost {
    val trimmed = entry.trim().trimEnd('/')
    require(trimmed.isNotEmpty()) { "Empty CORS host" }
    val schemeSep = trimmed.indexOf("://")
    val schemes: List<String>
    val hostPort = if (schemeSep >= 0) {
        val scheme = trimmed.substring(0, schemeSep).lowercase()
        require(scheme == "http" || scheme == "https") { "Unsupported CORS scheme in $entry" }
        schemes = listOf(scheme)
        trimmed.substring(schemeSep + 3)
    } else {
        schemes = listOf("http", "https")
        trimmed
    }
    val host = hostPort.substringBefore('/').lowercase()
    require(host.isNotEmpty() && ' ' !in host) { "Invalid CORS host: $entry" }
    val normalized = when {
        schemes == listOf("https") && host.endsWith(":443") -> host.removeSuffix(":443")
        schemes == listOf("http") && host.endsWith(":80") -> host.removeSuffix(":80")
        else -> host
    }
    return CorsAllowedHost(normalized, schemes)
}

fun normalizeSameSite(raw: String?): String = when (raw?.trim()?.lowercase()) {
    null, "" -> "Lax"
    "lax" -> "Lax"
    "strict" -> "Strict"
    "none" -> "None"
    else -> "Lax"
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
