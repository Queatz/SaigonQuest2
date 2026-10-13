package quest.saigon.town

import org.slf4j.LoggerFactory
import quest.saigon.AppConfig
import quest.saigon.NotificationCopy
import quest.saigon.http.Jsons
import quest.saigon.push.VapidKeys
import quest.saigon.push.WebPush
import quest.saigon.push.WebPushResult
import quest.saigon.resolveLanguage
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.Executors

fun interface NotificationPusher {
    fun notify(userId: String, notification: NotificationRecord)
}

object NoNotificationPush : NotificationPusher {
    override fun notify(userId: String, notification: NotificationRecord) {}
}

fun interface PushDelivery {
    fun deliver(subscription: PushSubscriptionRecord, payloadJson: String): WebPushResult
}

class WebPushService(
    private val repo: TownRepository,
    private val keys: VapidKeys?,
    clock: () -> Long = System::currentTimeMillis,
    private val executor: Executor = Executor { it.run() },
    delivery: PushDelivery? = null,
) : NotificationPusher {
    private val log = LoggerFactory.getLogger(WebPushService::class.java)
    private val delivery: PushDelivery = delivery ?: HttpPushDelivery(keys, clock)

    override fun notify(userId: String, notification: NotificationRecord) {
        executor.execute {
            try {
                send(userId, notification)
            } catch (error: Exception) {
                log.warn("Web push failed for {}: {}", userId, error.message)
            }
        }
    }

    private fun send(userId: String, notification: NotificationRecord) {
        val user = repo.findUser(userId)
        repo.pushSubscriptionsFor(userId).forEach { subscription ->
            val language = resolveLanguage(subscription.language, user?.language)
            val payload = Jsons.write(
                mapOf(
                    "title" to NotificationCopy.title(language, notification.type, notification.actorName, notification.preview),
                    "body" to NotificationCopy.body(notification.preview),
                    "url" to NotificationCopy.path(notification.postId),
                    "id" to notification.id,
                    "postId" to notification.postId,
                    "type" to notification.type,
                ),
            )
            val result = try {
                delivery.deliver(subscription, payload)
            } catch (error: Exception) {
                log.warn("Web push delivery failed: {}", error.message)
                WebPushResult.FAILED
            }
            if (result == WebPushResult.GONE) {
                repo.deletePushSubscriptionByEndpoint(subscription.endpoint)
            }
        }
    }

    companion object {
        fun fromConfig(config: AppConfig, repo: TownRepository, clock: () -> Long = System::currentTimeMillis): WebPushService {
            val keys = VapidKeys.parse(config.vapidPublicKey, config.vapidPrivateKey, config.vapidSubject)
            val executor = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "web-push").apply { isDaemon = true }
            }
            return WebPushService(repo, keys, clock, executor)
        }
    }
}

class HttpPushDelivery(
    private val keys: VapidKeys?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val client: HttpClient = httpClient,
) : PushDelivery {
    override fun deliver(subscription: PushSubscriptionRecord, payloadJson: String): WebPushResult {
        val vapid = keys ?: return WebPushResult.SKIPPED
        val request = WebPush.request(
            keys = vapid,
            endpoint = subscription.endpoint,
            p256dh = subscription.p256dh,
            auth = subscription.auth,
            payload = payloadJson,
            nowMillis = clock(),
        )
        val http = HttpRequest.newBuilder(URI.create(request.endpoint))
            .timeout(Duration.ofSeconds(15))
            .POST(HttpRequest.BodyPublishers.ofByteArray(request.body))
        request.headers.forEach { (name, value) -> http.header(name, value) }
        val status = client.send(http.build(), HttpResponse.BodyHandlers.discarding()).statusCode()
        return when (status) {
            404, 410 -> WebPushResult.GONE
            in 200..299 -> WebPushResult.SENT
            else -> WebPushResult.FAILED
        }
    }

    companion object {
        private val httpClient: HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }
}
