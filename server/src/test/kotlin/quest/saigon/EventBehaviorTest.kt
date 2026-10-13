package quest.saigon

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import quest.saigon.ai.MusicRender
import quest.saigon.auth.RateLimiter
import quest.saigon.maps.PlaceLookup
import quest.saigon.media.ImageOps
import quest.saigon.media.UploadStore
import quest.saigon.town.AvatarService
import quest.saigon.town.EventService
import quest.saigon.town.InMemoryTownRepository
import quest.saigon.town.MusicService
import quest.saigon.town.NotificationType
import quest.saigon.town.PlaceSuggestion
import quest.saigon.town.SaigonDays
import quest.saigon.town.TownServices
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun eventsCanBePostedJoinedAndSummarized() = testApplication {
        var now = 1_700_000_000_000L
        val services = services({ now })
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val linh = createClient { install(HttpCookies) }
        val minh = createClient { install(HttpCookies) }
        val an = createClient { install(HttpCookies) }
        linh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905550001","password":"secretpass"}""")
        }
        minh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Minh","phone":"0905550002","password":"secretpass"}""")
        }
        an.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"An","phone":"0905550003","password":"secretpass"}""")
        }

        val todayStart = SaigonDays.startMillis(SaigonDays.dayKey(now))
        val todayEvent = todayStart + 18 * 60 * 60 * 1000L
        val tomorrowEvent = todayStart + 34 * 60 * 60 * 1000L

        val created = postEvent(linh, "Sunset at the river", todayEvent, todayEvent + 2 * 60 * 60 * 1000L, 50_000, "ChIJ123abc")
        assertEquals(HttpStatusCode.Created, created.status)
        val body = mapper.readTree(created.bodyAsText())
        val postId = body.path("id").asText()
        assertEquals(todayEvent, body.path("eventStartAt").asLong())
        assertEquals(50_000, body.path("eventPrice").asLong())
        assertEquals("ChIJ123abc", body.path("placeId").asText())
        assertTrue(body.path("joined").asBoolean())
        assertEquals(1, body.path("attendeeCount").asInt())

        postEvent(linh, "Breakfast tomorrow", tomorrowEvent, null, null, null)

        val summary = mapper.readTree(linh.get("/api/events/summary").bodyAsText())
        assertEquals(1, summary.path("today").asInt())
        assertEquals(1, summary.path("tomorrow").asInt())

        val joined = minh.post("/api/posts/$postId/join")
        assertEquals(HttpStatusCode.OK, joined.status)
        assertTrue(mapper.readTree(joined.bodyAsText()).path("joined").asBoolean())
        assertEquals(2, mapper.readTree(joined.bodyAsText()).path("attendeeCount").asInt())

        val linhNotes = mapper.readTree(linh.get("/api/notifications").bodyAsText())
        assertEquals(NotificationType.EVENT_JOINED, linhNotes.path("notifications").path(0).path("type").asText())
        assertEquals("Minh joined your event".replace("Minh", linhNotes.path("notifications").path(0).path("actorName").asText()),
            "${linhNotes.path("notifications").path(0).path("actorName").asText()} joined your event")

        an.post("/api/posts/$postId/join")
        val minhNotes = mapper.readTree(minh.get("/api/notifications").bodyAsText())
        assertEquals(NotificationType.EVENT_FRIEND_JOINED, minhNotes.path("notifications").path(0).path("type").asText())

        val left = minh.post("/api/posts/$postId/leave")
        assertEquals(HttpStatusCode.OK, left.status)
        assertFalse(mapper.readTree(left.bodyAsText()).path("joined").asBoolean())
        val linhAfterLeave = mapper.readTree(linh.get("/api/notifications").bodyAsText()).path("notifications")
        assertTrue((0 until linhAfterLeave.size()).any { linhAfterLeave.path(it).path("type").asText() == NotificationType.EVENT_LEFT })

        val attendees = mapper.readTree(linh.get("/api/posts/$postId/attendees").bodyAsText()).path("attendees")
        assertTrue(attendees.size() >= 1)
        assertTrue(attendees.path(0).path("phone").asText().isNotBlank())
        assertTrue(attendees.path(0).path("zaloUrl").asText().startsWith("https://zalo.me/"))

        val events = mapper.readTree(linh.get("/api/events").bodyAsText())
        assertEquals(2, events.path("events").size())
        assertTrue(events.path("people").size() >= 3)

        val places = mapper.readTree(linh.get("/api/places?q=cafe").bodyAsText())
        assertEquals("Cafe", places.path("places").path(0).path("name").asText())
        val details = mapper.readTree(linh.get("/api/places/details?id=ChIJ123").bodyAsText())
        assertEquals(10.77, details.path("lat").asDouble(), 0.001)

        now = todayEvent - 60 * 60 * 1000L
        services.events.tick(now)
        val soon = mapper.readTree(an.get("/api/notifications").bodyAsText()).path("notifications")
        assertTrue((0 until soon.size()).any { soon.path(it).path("type").asText() == NotificationType.EVENT_STARTING_SOON })

        now = todayEvent
        services.events.tick(now)
        val starting = mapper.readTree(an.get("/api/notifications").bodyAsText()).path("notifications")
        assertTrue((0 until starting.size()).any { starting.path(it).path("type").asText() == NotificationType.EVENT_STARTING_NOW })

        val bad = postEvent(linh, "No start", null, todayEvent, 20_000, null)
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        val pricey = postEvent(linh, "Too much", todayEvent, null, Limits.EVENT_PRICE_MAX.toLong() + 1, null)
        assertEquals(HttpStatusCode.BadRequest, pricey.status)
    }

    private suspend fun postEvent(
        client: io.ktor.client.HttpClient,
        message: String,
        start: Long?,
        end: Long?,
        price: Long?,
        placeId: String?,
    ) = client.post("/api/posts") {
        setBody(
            MultiPartFormDataContent(
                formData {
                    val jpeg = ImageOps().writeJpeg(solid(12, 16, Color(40, 120, 80)), 0.85f)
                    append(
                        "photo",
                        jpeg,
                        Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                        },
                    )
                    append("message", message)
                    append("location", "Saigon River")
                    if (start != null) append("eventStartAt", start.toString())
                    if (end != null) append("eventEndAt", end.toString())
                    if (price != null) append("eventPrice", price.toString())
                    if (!placeId.isNullOrBlank()) {
                        append("placeId", placeId)
                        append("lat", "10.77")
                        append("lng", "106.70")
                    }
                },
            ),
        )
    }

    private fun services(clock: () -> Long): TownServices {
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-event", "").apply {
            delete()
            mkdirs()
        })
        val hub = quest.saigon.town.NotificationHub()
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
                renderer = { _, _, _ -> MusicRender(byteArrayOf(1), "Song") },
                configured = false,
                executor = { it.run() },
                clock = clock,
            ),
            config = AppConfig(uploadDir = uploads.root, googleMapsApiKey = "test-maps"),
            clock = clock,
            hub = hub,
            contactLimiter = RateLimiter(1, 60_000, clock),
            places = object : PlaceLookup {
                override fun search(query: String) = listOf(
                    PlaceSuggestion("Cafe", "D1", "ChIJ123", 10.77, 106.70),
                )
                override fun details(placeId: String) =
                    PlaceSuggestion("Cafe", "D1", placeId, 10.77, 106.70).takeIf { placeId == "ChIJ123" }
            },
            events = EventService(repo, hub, clock),
        )
    }

    private fun solid(width: Int, height: Int, color: Color): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = color
        graphics.fillRect(0, 0, width, height)
        graphics.dispose()
        return image
    }
}
