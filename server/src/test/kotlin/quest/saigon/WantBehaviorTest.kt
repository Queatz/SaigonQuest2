package quest.saigon

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.plugins.cookies.HttpCookies
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
import quest.saigon.town.AvatarService
import quest.saigon.town.InMemoryTownRepository
import quest.saigon.town.MusicService
import quest.saigon.town.TownServices
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WantBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun wantsAreSharedJoinableAndNotifyExistingMembersOnly() = testApplication {
        var timestamp = 1_700_000_000_000L
        val services = services { timestamp++ }
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val linh = createClient { install(HttpCookies) }
        val minh = createClient { install(HttpCookies) }
        val an = createClient { install(HttpCookies) }
        register(linh, "Linh", "0905550001")
        register(minh, "Minh", "0905550002")
        register(an, "An", "0905550003")
        val linhId = mapper.readTree(linh.get("/api/me").bodyAsText()).path("id").asText()
        val minhId = mapper.readTree(minh.get("/api/me").bodyAsText()).path("id").asText()

        val created = linh.post("/api/wants") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Go hiking"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val want = mapper.readTree(created.bodyAsText())
        val wantId = want.path("id").asText()
        assertEquals("Go hiking", want.path("name").asText())
        assertTrue(want.path("joined").asBoolean())
        assertEquals(1, want.path("memberCount").asInt())
        assertEquals(0, mapper.readTree(linh.get("/api/notifications").bodyAsText()).path("notifications").size())

        val joined = minh.post("/api/wants") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"go hiking"}""")
        }
        assertEquals(HttpStatusCode.OK, joined.status)
        assertEquals(wantId, mapper.readTree(joined.bodyAsText()).path("id").asText())
        assertEquals(2, mapper.readTree(joined.bodyAsText()).path("memberCount").asInt())

        val notifications = mapper.readTree(linh.get("/api/notifications").bodyAsText()).path("notifications")
        assertEquals(1, notifications.size())
        assertEquals("want_joined", notifications.path(0).path("type").asText())
        assertEquals(minhId, notifications.path(0).path("actorId").asText())
        assertEquals("Minh", notifications.path(0).path("actorName").asText())
        assertEquals("Go hiking", notifications.path(0).path("preview").asText())
        assertEquals(0, mapper.readTree(minh.get("/api/notifications").bodyAsText()).path("notifications").size())

        minh.post("/api/wants/$wantId/join")
        assertEquals(1, mapper.readTree(linh.get("/api/notifications").bodyAsText()).path("notifications").size())

        val mine = mapper.readTree(linh.get("/api/me/wants").bodyAsText()).path("wants")
        assertEquals(wantId, mine.path(0).path("id").asText())
        val onProfile = mapper.readTree(an.get("/api/users/$linhId/wants").bodyAsText()).path("wants")
        assertEquals(wantId, onProfile.path(0).path("id").asText())
        assertFalse(onProfile.path(0).path("joined").asBoolean())

        val left = linh.post("/api/wants/$wantId/leave")
        assertEquals(HttpStatusCode.OK, left.status)
        assertFalse(mapper.readTree(left.bodyAsText()).path("joined").asBoolean())
        assertEquals(0, mapper.readTree(linh.get("/api/me/wants").bodyAsText()).path("wants").size())
        assertEquals(0, mapper.readTree(an.get("/api/users/$linhId/wants").bodyAsText()).path("wants").size())
        assertEquals(1, mapper.readTree(an.get("/api/users/$minhId/wants").bodyAsText()).path("wants").size())

        val invalid = an.post("/api/wants") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"  "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)

        val tooLong = an.post("/api/wants") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"${"x".repeat(81)}"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, tooLong.status)
    }

    private suspend fun register(client: io.ktor.client.HttpClient, name: String, phone: String) {
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"$name","phone":"$phone","password":"secretpass"}""")
        }
    }

    private fun services(clock: () -> Long = { 1_700_000_000_000L }): TownServices {
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-want", "").apply {
            delete()
            mkdirs()
        })
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
            config = AppConfig(uploadDir = uploads.root),
            clock = clock,
            contactLimiter = RateLimiter(1, 60_000, clock),
        )
    }
}