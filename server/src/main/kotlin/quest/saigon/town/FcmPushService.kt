package quest.saigon.town

import org.slf4j.LoggerFactory
import quest.saigon.AppConfig
import quest.saigon.NotificationCopy
import quest.saigon.http.Jsons
import quest.saigon.resolveLanguage
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.util.Base64
import java.util.concurrent.Executor
import java.util.concurrent.Executors

enum class FcmPushResult {
    SENT,
    UNREGISTERED,
    FAILED,
    SKIPPED,
}

fun interface FcmDelivery {
    fun deliver(token: FcmTokenRecord, title: String, body: String, data: Map<String, String>): FcmPushResult
}

class CompositeNotificationPusher(
    private val pushers: List<NotificationPusher>,
) : NotificationPusher {
    override fun notify(userId: String, notification: NotificationRecord) {
        pushers.forEach { it.notify(userId, notification) }
    }
}

class FcmPushService(
    private val repo: TownRepository,
    private val delivery: FcmDelivery,
    private val executor: Executor = Executor { it.run() },
) : NotificationPusher {
    private val log = LoggerFactory.getLogger(FcmPushService::class.java)

    override fun notify(userId: String, notification: NotificationRecord) {
        executor.execute {
            try {
                send(userId, notification)
            } catch (error: Exception) {
                log.warn("FCM push failed for {}: {}", userId, error.message)
            }
        }
    }

    private fun send(userId: String, notification: NotificationRecord) {
        val user = repo.findUser(userId)
        repo.fcmTokensFor(userId).forEach { record ->
            val language = resolveLanguage(record.language, user?.language)
            val title = NotificationCopy.title(language, notification.type, notification.actorName, notification.preview)
            val body = NotificationCopy.body(notification.preview)
            val url = NotificationCopy.path(notification.type, notification.postId, notification.chatId)
            val data = buildMap {
                put("id", notification.id)
                put("type", notification.type)
                put("postId", notification.postId)
                put("chatId", notification.chatId)
                put("url", url)
                put("title", title)
                put("body", body)
            }
            val result = try {
                delivery.deliver(record, title, body, data)
            } catch (error: Exception) {
                log.warn("FCM delivery failed: {}", error.message)
                FcmPushResult.FAILED
            }
            if (result == FcmPushResult.UNREGISTERED) {
                repo.deleteFcmTokenByValue(record.token)
            }
        }
    }

    companion object {
        fun fromConfig(
            config: AppConfig,
            repo: TownRepository,
            clock: () -> Long = System::currentTimeMillis,
        ): FcmPushService {
            val delivery = HttpFcmDelivery.fromConfig(config, clock)
            val executor = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "fcm-push").apply { isDaemon = true }
            }
            return FcmPushService(repo, delivery, executor)
        }
    }
}

class HttpFcmDelivery(
    private val projectId: String?,
    private val clientEmail: String?,
    private val privateKey: PrivateKey?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val client: HttpClient = httpClient,
) : FcmDelivery {
    private val log = LoggerFactory.getLogger(HttpFcmDelivery::class.java)
    private val tokenLock = Any()
    private var cachedAccessToken: String? = null
    private var cachedExpiresAt: Long = 0L

    override fun deliver(token: FcmTokenRecord, title: String, body: String, data: Map<String, String>): FcmPushResult {
        val project = projectId?.takeIf { it.isNotBlank() } ?: return FcmPushResult.SKIPPED
        val email = clientEmail?.takeIf { it.isNotBlank() } ?: return FcmPushResult.SKIPPED
        val key = privateKey ?: return FcmPushResult.SKIPPED

        val accessToken = accessToken(email, key) ?: return FcmPushResult.FAILED
        val payload = Jsons.write(
            mapOf(
                "message" to mapOf(
                    "token" to token.token,
                    "notification" to mapOf(
                        "title" to title,
                        "body" to body,
                    ),
                    "data" to data,
                    "android" to mapOf(
                        "priority" to "HIGH",
                        "notification" to mapOf(
                            "channel_id" to "saigon_town_updates",
                        ),
                    ),
                ),
            ),
        )
        val request = HttpRequest.newBuilder(URI.create("https://fcm.googleapis.com/v1/projects/$project/messages:send"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return when (response.statusCode()) {
            in 200..299 -> FcmPushResult.SENT
            404 -> FcmPushResult.UNREGISTERED
            400, 403, 410 -> {
                if (response.body().contains("UNREGISTERED")) {
                    FcmPushResult.UNREGISTERED
                } else {
                    log.warn("FCM delivery returned HTTP {}: {}", response.statusCode(), response.body())
                    FcmPushResult.FAILED
                }
            }
            else -> {
                log.warn("FCM delivery returned HTTP {}: {}", response.statusCode(), response.body())
                FcmPushResult.FAILED
            }
        }
    }

    private fun accessToken(clientEmail: String, privateKey: PrivateKey): String? {
        val now = clock()
        synchronized(tokenLock) {
            val current = cachedAccessToken
            if (current != null && now < cachedExpiresAt - 60_000L) {
                return current
            }
            val iat = now / 1000L
            val exp = iat + 3600L
            val header = base64Url("""{"alg":"RS256","typ":"JWT"}""")
            val claims = base64Url(
                Jsons.write(
                    mapOf(
                        "iss" to clientEmail,
                        "scope" to "https://www.googleapis.com/auth/firebase.messaging",
                        "aud" to TOKEN_URI,
                        "iat" to iat,
                        "exp" to exp,
                    ),
                ),
            )
            val signingInput = "$header.$claims"
            val signature = Signature.getInstance("SHA256withRSA").run {
                initSign(privateKey)
                update(signingInput.toByteArray(StandardCharsets.UTF_8))
                Base64.getUrlEncoder().withoutPadding().encodeToString(sign())
            }
            val assertion = "$signingInput.$signature"
            val form = "grant_type=${urlEncode("urn:ietf:params:oauth:grant-type:jwt-bearer")}&assertion=${urlEncode(assertion)}"
            val request = HttpRequest.newBuilder(URI.create(TOKEN_URI))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                log.warn("FCM OAuth token exchange failed with status {}", response.statusCode())
                return null
            }
            val parsed = Jsons.parse(response.body())
            val token = Jsons.text(parsed, "access_token")
            if (token.isBlank()) return null
            val expiresIn = parsed.path("expires_in").asLong(3600L).coerceAtLeast(120L)
            cachedAccessToken = token
            cachedExpiresAt = now + expiresIn * 1000L
            return token
        }
    }

    companion object {
        private const val TOKEN_URI = "https://oauth2.googleapis.com/token"
        private val httpClient: HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

        fun fromConfig(config: AppConfig, clock: () -> Long = System::currentTimeMillis): HttpFcmDelivery {
            val log = LoggerFactory.getLogger(HttpFcmDelivery::class.java)
            val key = parsePrivateKey(config.fcmPrivateKey)
            if (config.fcmConfigured && key != null) {
                log.info("FCM push enabled for project {}", config.fcmProjectId)
            } else if (config.fcmConfigured) {
                log.warn("FCM push private key could not be parsed for project {}; FCM delivery disabled", config.fcmProjectId)
            } else {
                log.info("FCM push disabled (credentials not configured)")
            }
            return HttpFcmDelivery(
                projectId = config.fcmProjectId,
                clientEmail = config.fcmClientEmail,
                privateKey = key,
                clock = clock,
            )
        }

        private fun parsePrivateKey(pem: String?): PrivateKey? {
            if (pem.isNullOrBlank()) return null
            return try {
                val clean = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace(Regex("\\s+"), "")
                val decoded = Base64.getDecoder().decode(clean)
                KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(decoded))
            } catch (_: Exception) {
                null
            }
        }

        private fun base64Url(text: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(StandardCharsets.UTF_8))

        private fun urlEncode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8)
    }
}
