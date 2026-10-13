package quest.saigon

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import quest.saigon.ai.MusicRender
import quest.saigon.auth.RateLimiter
import quest.saigon.media.UploadStore
import quest.saigon.push.VapidKeys
import quest.saigon.push.WebPush
import quest.saigon.push.WebPushResult
import quest.saigon.push.encodeBase64Url
import quest.saigon.town.AvatarService
import quest.saigon.town.EventService
import quest.saigon.town.InMemoryTownRepository
import quest.saigon.town.MusicService
import quest.saigon.town.NotificationHub
import quest.saigon.town.NotificationType
import quest.saigon.town.PostRecord
import quest.saigon.town.PushDelivery
import quest.saigon.town.PushSubscriptionRecord
import quest.saigon.town.TownServices
import quest.saigon.town.WebPushService
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebPushBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun vapidJwtAndEncryptedPayloadAreWellFormed() {
        val vapid = VapidKeys.generate()
        val ua = VapidKeys.generate()
        val auth = encodeBase64Url(ByteArray(16) { 7 })
        val request = WebPush.request(
            keys = vapid,
            endpoint = "https://fcm.googleapis.com/fcm/send/abc",
            p256dh = ua.publicKey,
            auth = auth,
            payload = """{"title":"Minh commented on your post","body":"Hi","url":"/p/abc"}""",
            nowMillis = 1_700_000_000_000L,
        )
        assertEquals("https://fcm.googleapis.com", WebPush.audience(request.endpoint))
        assertEquals(65, request.body[20].toInt() and 0xFF)
        assertTrue(request.body.size > 86)
        assertTrue(request.headers.getValue("Authorization").startsWith("vapid t="))
        assertEquals("aes128gcm", request.headers.getValue("Content-Encoding"))
        assertEquals(3, request.headers.getValue("Authorization").substringAfter("t=").substringBefore(",").split('.').size)
    }

    @Test
    fun pushRoutesNeedASessionAndVapidKeys() = testApplication {
        val services = services({ 1L }, vapid = null)
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val guest = createClient { }
        assertEquals(HttpStatusCode.Unauthorized, guest.get("/api/push/vapid").status)
        val user = createClient { install(HttpCookies) }
        user.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0901110091","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, user.get("/api/push/vapid").status)
        assertEquals(
            HttpStatusCode.ServiceUnavailable,
            user.post("/api/push/subscribe") {
                contentType(ContentType.Application.Json)
                setBody("""{"endpoint":"https://push.example/1","keys":{"p256dh":"ABCDEFGH","auth":"12345678"}}""")
            }.status,
        )
    }

    @Test
    fun commentsAndEventsPushExactInboxTextAndPostUrl() = testApplication {
        var now = 1_700_000_000_000L
        val vapid = VapidKeys.generate()
        val delivered = mutableListOf<String>()
        val delivery = PushDelivery { _, payload ->
            delivered += payload
            WebPushResult.SENT
        }
        val services = services({ now }, vapid, delivery)
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val owner = createClient { install(HttpCookies) }
        val friend = createClient { install(HttpCookies) }
        owner.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0901110092","password":"secretpass"}""")
        }
        friend.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Minh","phone":"0901110093","password":"secretpass"}""")
        }
        val vapidBody = mapper.readTree(owner.get("/api/push/vapid").bodyAsText())
        assertEquals(vapid.publicKey, vapidBody.path("publicKey").asText())
        val subscribed = owner.post("/api/push/subscribe") {
            contentType(ContentType.Application.Json)
            setBody("""{"endpoint":"https://push.example/owner","keys":{"p256dh":"ABCDEFGHIJK","auth":"1234567890"},"language":"en"}""")
        }
        assertEquals(HttpStatusCode.OK, subscribed.status)

        val post = services.repo.insertPost(
            PostRecord("", mapper.readTree(owner.get("/api/me").bodyAsText()).path("id").asText(), null, "posts/x.jpg", "Coffee at 8?", "D1", now, eventStartAt = now + 2 * 60 * 60_000L),
        )
        now += 1
        friend.post("/api/posts/${post.id}/comments") {
            contentType(ContentType.Application.Json)
            setBody("""{"text":"I'm down"}""")
        }
        val commentPush = mapper.readTree(delivered.last())
        assertEquals(
            NotificationCopy.title(Language.EN, NotificationType.POST_COMMENT, "Minh", "I'm down"),
            commentPush.path("title").asText(),
        )
        assertEquals("I'm down", commentPush.path("body").asText())
        assertEquals(NotificationCopy.path(post.id), commentPush.path("url").asText())
        assertEquals(NotificationType.POST_COMMENT, commentPush.path("type").asText())

        delivered.clear()
        friend.post("/api/posts/${post.id}/join")
        val joinPush = mapper.readTree(delivered.last())
        assertEquals(
            NotificationCopy.title(Language.EN, NotificationType.EVENT_JOINED, "Minh", "Minh"),
            joinPush.path("title").asText(),
        )
        assertEquals(NotificationCopy.path(post.id), joinPush.path("url").asText())

        delivered.clear()
        now = post.eventStartAt!! - 60 * 60_000L
        services.events.tick(now)
        val soon = delivered.map { mapper.readTree(it) }.first { it.path("type").asText() == NotificationType.EVENT_STARTING_SOON }
        val eventName = EventService.eventTitle(post)
        assertEquals(
            NotificationCopy.title(Language.EN, NotificationType.EVENT_STARTING_SOON, "", eventName),
            soon.path("title").asText(),
        )
        assertEquals(eventName, soon.path("body").asText())
        assertEquals(NotificationCopy.path(post.id), soon.path("url").asText())
    }

    @Test
    fun goneSubscriptionsAreDropped() {
        val repo = InMemoryTownRepository()
        val user = repo.createUser("0901110094", "hashhashhash", "An", 1L)
        repo.upsertPushSubscription(
            PushSubscriptionRecord(
                endpoint = "https://push.example/gone",
                userId = user.id,
                p256dh = "ABCDEFGHIJK",
                auth = "1234567890",
                language = "en",
                createdAt = 1L,
            ),
        )
        val pusher = WebPushService(
            repo = repo,
            keys = VapidKeys.generate(),
            delivery = PushDelivery { _, _ -> WebPushResult.GONE },
        )
        pusher.notify(
            user.id,
            quest.saigon.town.NotificationRecord(
                id = "n1",
                userId = user.id,
                type = NotificationType.POST_COMMENT,
                postId = "p1",
                commentId = "c1",
                actorId = "u2",
                actorName = "Minh",
                preview = "Hi",
                createdAt = 1L,
            ),
        )
        assertEquals(emptyList(), repo.pushSubscriptionsFor(user.id))
    }

    @Test
    fun unsubscribeRemovesOnlyThatEndpoint() = testApplication {
        val vapid = VapidKeys.generate()
        val services = services({ 1L }, vapid)
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val user = createClient { install(HttpCookies) }
        user.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0901110095","password":"secretpass"}""")
        }
        user.post("/api/push/subscribe") {
            contentType(ContentType.Application.Json)
            setBody("""{"endpoint":"https://push.example/a","keys":{"p256dh":"ABCDEFGHIJK","auth":"1234567890"}}""")
        }
        user.post("/api/push/subscribe") {
            contentType(ContentType.Application.Json)
            setBody("""{"endpoint":"https://push.example/b","keys":{"p256dh":"ABCDEFGHIJK","auth":"1234567890"}}""")
        }
        val removed = user.delete("/api/push/subscribe") {
            contentType(ContentType.Application.Json)
            setBody("""{"endpoint":"https://push.example/a"}""")
        }
        assertEquals(HttpStatusCode.OK, removed.status)
        val userId = mapper.readTree(user.get("/api/me").bodyAsText()).path("id").asText()
        val remaining = services.repo.pushSubscriptionsFor(userId)
        assertEquals(listOf("https://push.example/b"), remaining.map { it.endpoint })
    }

    private fun services(clock: () -> Long, vapid: VapidKeys?, delivery: PushDelivery? = null): TownServices {
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-push", "").apply {
            delete()
            mkdirs()
        })
        val hub = NotificationHub()
        val pusher = WebPushService(repo, vapid, clock, delivery = delivery)
        return TownServices(
            repo = repo,
            uploads = uploads,
            avatars = AvatarService(
                repo = repo,
                uploads = uploads,
                renderer = { _, _ -> byteArrayOf(1) },
                configured = false,
                executor = { it.run() },
                clock = clock,
            ),
            music = MusicService(
                repo = repo,
                uploads = uploads,
                renderer = { _, _, _ -> MusicRender(byteArrayOf(1, 2, 3, 4), "Song") },
                configured = false,
                executor = { it.run() },
                clock = clock,
            ),
            config = AppConfig(
                uploadDir = uploads.root,
                vapidPublicKey = vapid?.publicKey,
                vapidPrivateKey = vapid?.privateKey,
            ),
            clock = clock,
            hub = hub,
            pusher = pusher,
            contactLimiter = RateLimiter(1, 60_000, clock),
            events = EventService(repo, hub, clock, pusher),
        )
    }
}
