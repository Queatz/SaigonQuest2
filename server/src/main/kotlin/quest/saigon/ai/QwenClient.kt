package quest.saigon.ai

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import quest.saigon.AppConfig
import quest.saigon.media.ImageOps
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64
import java.util.UUID

class QwenException(message: String) : RuntimeException(message)

class QwenClient(
    private val apiKey: String?,
    private val baseUrl: String,
    private val textModel: String,
    private val imageModel: String,
    private val segmentModel: String,
    private val segmentBaseUrl: String,
    private val workspaceId: String?,
    private val http: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val images: ImageOps = ImageOps(),
) {
    private val log = LoggerFactory.getLogger(QwenClient::class.java)
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
            timeoutSeconds = 45,
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
                    "negative_prompt" to "scenery, text, watermark, logo, extra limbs, props, white background, flat cel shading, sticker, outline",
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
                timeoutSeconds = 60,
            )
        } else {
            throw QwenException(failure(sync))
        }
        return download(poll(api, async.body, preferMask = false))
    }

    fun segment(image: ByteArray): ByteArray? {
        if (apiKey.isNullOrBlank()) return null
        return try {
            val ossKey = upload(image)
            val imageRef = ossKey ?: ("data:image/png;base64," + Base64.getEncoder().encodeToString(image))
            val api = root(segmentBaseUrl)
            val body = mapper.writeValueAsString(
                mapOf(
                    "model" to segmentModel,
                    "input" to mapOf("image_url" to imageRef),
                    "parameters" to emptyMap<String, Any>(),
                ),
            )
            val extra = if (ossKey != null) mapOf("X-DashScope-OssResourceResolve" to "enable") else emptyMap()
            val submit = postJson(
                "$api/api/v1/services/aigc/image2image/image-synthesis",
                body,
                async = true,
                timeoutSeconds = 60,
                extraHeaders = extra,
            )
            if (submit.status !in 200..299) {
                log.info("Instance segmentation rejected: {}", failure(submit))
                return null
            }
            download(poll(api, submit.body, preferMask = true))
        } catch (error: Exception) {
            log.info("Instance segmentation unavailable: {}", error.message)
            null
        }
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

    private fun poll(api: String, submitBody: String, preferMask: Boolean): String {
        val node = QwenResponses.parse(submitBody)
        val existing = if (preferMask) QwenResponses.maskUrl(node) else QwenResponses.imageUrls(node).firstOrNull()
        if (existing != null && QwenResponses.taskStatus(node) == null) return existing
        val taskId = QwenResponses.taskId(node) ?: throw QwenException(QwenResponses.errorMessage(node) ?: "no_task")
        val deadline = System.currentTimeMillis() + GENERATION_POLL_MILLIS
        while (System.currentTimeMillis() < deadline) {
            sleep(2_000)
            val body = get("$api/api/v1/tasks/$taskId")
            val parsed = QwenResponses.parse(body)
            when (QwenResponses.taskStatus(parsed)) {
                "SUCCEEDED" -> return if (preferMask) {
                    QwenResponses.maskUrl(parsed) ?: throw QwenException("no_mask")
                } else {
                    QwenResponses.imageUrls(parsed).firstOrNull() ?: throw QwenException("no_image")
                }
                "FAILED", "CANCELED", "UNKNOWN" -> throw QwenException(QwenResponses.errorMessage(parsed) ?: "failed")
            }
        }
        throw QwenException("timeout")
    }

    private fun upload(bytes: ByteArray): String? {
        val model = URLEncoder.encode(segmentModel, StandardCharsets.UTF_8)
        val policyBody = get("${root(segmentBaseUrl)}/api/v1/uploads?action=getPolicy&model=$model")
        val data = QwenResponses.parse(policyBody).path("data")
        val host = data.path("upload_host").asText("")
        val dir = data.path("upload_dir").asText("")
        if (host.isBlank() || dir.isBlank()) return null
        val filename = "mask-${UUID.randomUUID()}.png"
        val key = "$dir/$filename"
        val fields = linkedMapOf(
            "key" to key,
            "policy" to data.path("policy").asText(""),
            "OSSAccessKeyId" to data.path("oss_access_key_id").asText(""),
            "Signature" to data.path("signature").asText(""),
            "x-oss-object-acl" to data.path("x_oss_object_acl").asText("private"),
            "x-oss-forbid-overwrite" to data.path("x_oss_forbid_overwrite").asText("true"),
            "success_action_status" to "200",
        )
        val status = postMultipart(host, fields, filename, bytes)
        if (status !in 200..299) return null
        return "oss://$key"
    }

    private fun download(url: String): ByteArray {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
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
        extraHeaders: Map<String, String> = emptyMap(),
    ): HttpResult {
        val key = apiKey ?: throw QwenException("qwen_not_configured")
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        if (async) builder.header("X-DashScope-Async", "enable")
        extraHeaders.forEach { (name, value) -> builder.header(name, value) }
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

    private fun postMultipart(url: String, fields: Map<String, String>, filename: String, bytes: ByteArray): Int {
        val boundary = "----SaigonQuest${UUID.randomUUID().toString().replace("-", "")}"
        val body = java.io.ByteArrayOutputStream()
        fun write(text: String) = body.write(text.toByteArray(StandardCharsets.UTF_8))
        fields.forEach { (name, value) ->
            write("--$boundary\r\n")
            write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
            write(value)
            write("\r\n")
        }
        write("--$boundary\r\n")
        write("Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n")
        write("Content-Type: image/png\r\n\r\n")
        body.write(bytes)
        write("\r\n--$boundary--\r\n")
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build()
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
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
            segmentModel = config.qwenSegmentModel,
            segmentBaseUrl = config.qwenSegmentBaseUrl,
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
