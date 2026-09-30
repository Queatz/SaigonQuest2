package quest.saigon.ai

import com.fasterxml.jackson.databind.ObjectMapper
import quest.saigon.AppConfig
import quest.saigon.MusicNames
import quest.saigon.media.ImageOps
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

class QwenException(message: String) : RuntimeException(message)

class QwenClient(
    private val apiKey: String?,
    private val baseUrl: String,
    private val textModel: String,
    private val imageModel: String,
    private val musicModel: String,
    private val workspaceId: String?,
    private val http: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val images: ImageOps = ImageOps(),
) {
    private val mapper = ObjectMapper()

    fun expandPrompt(userPrompt: String): String? {
        val body = mapper.writeValueAsString(
            mapOf(
                "model" to textModel,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to AvatarPrompts.EXPAND_SYSTEM.trim()),
                    mapOf("role" to "user", "content" to userPrompt),
                ),
                "temperature" to 0.4,
                "enable_thinking" to false,
            ),
        )
        val response = postJson(
            "${root(baseUrl)}/compatible-mode/v1/chat/completions",
            body,
            async = false,
            timeoutSeconds = 120,
        )
        if (response.status !in 200..299) {
            throw QwenException("text_${response.status}")
        }
        return QwenResponses.chatText(QwenResponses.parse(response.body))
    }

    fun editImage(source: ByteArray, instruction: String, mime: String): ByteArray {
        val prepared = images.shrinkForUpload(source)
        val dataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(prepared)
        val body = mapper.writeValueAsString(
            mapOf(
                "model" to imageModel,
                "input" to mapOf(
                    "messages" to listOf(
                        mapOf(
                            "role" to "user",
                            "content" to listOf(
                                mapOf("image" to dataUrl),
                                mapOf("text" to instruction),
                            ),
                        ),
                    ),
                ),
                "parameters" to mapOf(
                    "n" to 1,
                    "watermark" to false,
                    "prompt_extend" to false,
                    "size" to "768*1152",
                    "negative_prompt" to "scenery, text, watermark, logo, extra limbs, props, white background, sticker, outline",
                ),
            ),
        )
        val api = root(baseUrl)
        val sync = postJson(
            "$api/api/v1/services/aigc/multimodal-generation/generation",
            body,
            async = false,
            timeoutSeconds = GENERATION_TIMEOUT_SECONDS,
        )
        val immediate = imageFrom(sync)
        if (immediate != null) return download(immediate)
        val async = if (sync.status in 200..299 && QwenResponses.taskId(QwenResponses.parse(sync.body)) != null) {
            sync
        } else if (shouldRetryAsync(sync)) {
            postJson(
                "$api/api/v1/services/aigc/image-generation/generation",
                body,
                async = true,
                timeoutSeconds = 120,
            )
        } else {
            throw QwenException(failure(sync))
        }
        return download(poll(api, async.body))
    }

    fun musicTitle(prompt: String): String? {
        val body = mapper.writeValueAsString(
            mapOf(
                "model" to textModel,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to MusicPrompts.TITLE_SYSTEM.trim()),
                    mapOf("role" to "user", "content" to prompt),
                ),
                "temperature" to 0.7,
                "enable_thinking" to false,
            ),
        )
        val response = postJson(
            "${root(baseUrl)}/compatible-mode/v1/chat/completions",
            body,
            async = false,
            timeoutSeconds = 30,
        )
        if (response.status !in 200..299) return null
        return MusicNames.cleanTitle(QwenResponses.chatText(QwenResponses.parse(response.body)))
    }

    fun generateMusic(prompt: String, gender: String?, instrumental: Boolean): GeneratedMusic {
        val input = linkedMapOf<String, Any>(
            "prompt" to prompt,
            "format" to "mp3",
        )
        if (instrumental) {
            input["is_instrumental"] = true
        } else if (!gender.isNullOrBlank()) {
            input["gender"] = gender
        }
        val body = mapper.writeValueAsString(
            mapOf(
                "model" to musicModel,
                "input" to input,
            ),
        )
        val api = root(baseUrl)
        val response = postJson(
            "$api/api/v1/services/audio/music/generation",
            body,
            async = false,
            timeoutSeconds = GENERATION_TIMEOUT_SECONDS,
        )
        if (response.status !in 200..299) throw QwenException(failure(response))
        val parsed = QwenResponses.parse(response.body)
        val url = QwenResponses.audioUrl(parsed)
        if (url != null) return GeneratedMusic(download(url), QwenResponses.lyrics(parsed))
        if (QwenResponses.taskId(parsed) == null) {
            throw QwenException(QwenResponses.errorMessage(parsed) ?: "no_audio")
        }
        val polled = pollAudio(api, response.body)
        return GeneratedMusic(download(polled.first), polled.second)
    }

    private fun imageFrom(response: HttpResult): String? {
        if (response.status !in 200..299) return null
        val node = QwenResponses.parse(response.body)
        if (QwenResponses.taskId(node) != null && QwenResponses.imageUrls(node).isEmpty()) return null
        return QwenResponses.imageUrls(node).firstOrNull()
    }

    private fun shouldRetryAsync(response: HttpResult): Boolean {
        val message = response.body.lowercase()
        return response.status == 400 && ("synchronous" in message || "async" in message)
    }

    private fun poll(api: String, submitBody: String): String {
        val node = QwenResponses.parse(submitBody)
        val existing = QwenResponses.imageUrls(node).firstOrNull()
        if (existing != null && QwenResponses.taskStatus(node) == null) return existing
        val taskId = QwenResponses.taskId(node) ?: throw QwenException(QwenResponses.errorMessage(node) ?: "no_task")
        val deadline = System.currentTimeMillis() + GENERATION_POLL_MILLIS
        while (System.currentTimeMillis() < deadline) {
            sleep(2_000)
            val body = get("$api/api/v1/tasks/$taskId")
            val parsed = QwenResponses.parse(body)
            when (QwenResponses.taskStatus(parsed)) {
                "SUCCEEDED" -> return QwenResponses.imageUrls(parsed).firstOrNull() ?: throw QwenException("no_image")
                "FAILED", "CANCELED", "UNKNOWN" -> throw QwenException(QwenResponses.errorMessage(parsed) ?: "failed")
            }
        }
        throw QwenException("timeout")
    }

    private fun pollAudio(api: String, submitBody: String): Pair<String, String?> {
        val node = QwenResponses.parse(submitBody)
        val existing = QwenResponses.audioUrl(node)
        if (existing != null && QwenResponses.taskStatus(node) == null) {
            return existing to QwenResponses.lyrics(node)
        }
        val taskId = QwenResponses.taskId(node) ?: throw QwenException(QwenResponses.errorMessage(node) ?: "no_task")
        val deadline = System.currentTimeMillis() + GENERATION_POLL_MILLIS
        while (System.currentTimeMillis() < deadline) {
            sleep(2_000)
            val body = get("$api/api/v1/tasks/$taskId")
            val parsed = QwenResponses.parse(body)
            when (QwenResponses.taskStatus(parsed)) {
                "SUCCEEDED" -> {
                    val url = QwenResponses.audioUrl(parsed) ?: throw QwenException("no_audio")
                    return url to QwenResponses.lyrics(parsed)
                }
                "FAILED", "CANCELED", "UNKNOWN" -> throw QwenException(QwenResponses.errorMessage(parsed) ?: "failed")
            }
        }
        throw QwenException("timeout")
    }

    private fun download(url: String): ByteArray {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(120))
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) throw QwenException("download_${response.statusCode()}")
        return response.body()
    }

    private fun postJson(
        url: String,
        body: String,
        async: Boolean,
        timeoutSeconds: Long,
    ): HttpResult {
        val key = apiKey ?: throw QwenException("qwen_not_configured")
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        if (async) builder.header("X-DashScope-Async", "enable")
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        return HttpResult(response.statusCode(), response.body())
    }

    private fun get(url: String): String {
        val key = apiKey ?: throw QwenException("qwen_not_configured")
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer $key")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) throw QwenException("get_${response.statusCode()}")
        return response.body()
    }

    private fun failure(response: HttpResult): String =
        QwenResponses.errorMessage(QwenResponses.parse(response.body)) ?: "http_${response.status}"

    private fun root(url: String): String = apiRoot(url, workspaceId)

    private data class HttpResult(val status: Int, val body: String)

    companion object {
        const val GENERATION_TIMEOUT_SECONDS = 360L
        const val GENERATION_POLL_MILLIS = 360_000L

        fun fromConfig(config: AppConfig): QwenClient = QwenClient(
            apiKey = config.qwenApiKey,
            baseUrl = config.qwenBaseUrl,
            textModel = config.qwenTextModel,
            imageModel = config.qwenImageModel,
            musicModel = config.qwenMusicModel,
            workspaceId = config.qwenWorkspaceId,
        )

        fun apiRoot(baseUrl: String, workspaceId: String?): String {
            var base = baseUrl.trim().trimEnd('/')
            listOf("/compatible-mode/v1", "/compatible-mode", "/api/v1").forEach { suffix ->
                if (base.endsWith(suffix)) base = base.removeSuffix(suffix).trimEnd('/')
            }
            if (base.contains(".maas.aliyuncs.com")) return base
            val id = workspaceId?.trim().orEmpty()
            if (id.isEmpty()) return base
            val region = when {
                base.contains("intl") || base.contains("ap-southeast") -> "ap-southeast-1"
                base.contains("us-east") -> "us-east-1"
                base.contains("eu-central") -> "eu-central-1"
                base.contains("ap-northeast") -> "ap-northeast-1"
                else -> "cn-beijing"
            }
            return "https://$id.$region.maas.aliyuncs.com"
        }
    }
}

data class GeneratedMusic(
    val bytes: ByteArray,
    val lyrics: String?,
)
