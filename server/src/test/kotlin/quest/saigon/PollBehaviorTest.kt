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
import quest.saigon.media.ImageOps
import quest.saigon.media.UploadStore
import quest.saigon.town.AvatarService
import quest.saigon.town.InMemoryTownRepository
import quest.saigon.town.MusicService
import quest.saigon.town.NotificationType
import quest.saigon.town.PollService
import quest.saigon.town.TownServices
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PollBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun pollsCanBePostedVotedAndUnvoted() = testApplication {
        val now = 1_700_000_000_000L
        val services = services({ now })
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val linh = createClient { install(HttpCookies) }
        val minh = createClient { install(HttpCookies) }
        linh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905550101","password":"secretpass"}""")
        }
        minh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Minh","phone":"0905550102","password":"secretpass"}""")
        }

        val created = postPoll(linh, "Lunch?", listOf("Pho", "Banh mi", "Com tam"))
        assertEquals(HttpStatusCode.Created, created.status)
        val body = mapper.readTree(created.bodyAsText())
        val postId = body.path("id").asText()
        assertEquals(3, body.path("pollOptions").size())
        assertEquals("Pho", body.path("pollOptions").path(0).path("text").asText())
        assertEquals(0, body.path("pollOptions").path(0).path("voteCount").asInt())

        val voted = minh.post("/api/posts/$postId/poll/0")
        assertEquals(HttpStatusCode.OK, voted.status)
        val votedBody = mapper.readTree(voted.bodyAsText())
        assertTrue(votedBody.path("pollOptions").path(0).path("voted").asBoolean())
        assertEquals(1, votedBody.path("pollOptions").path(0).path("voteCount").asInt())
        assertEquals(0, votedBody.path("pollOptions").path(1).path("voteCount").asInt())

        val notes = mapper.readTree(linh.get("/api/notifications").bodyAsText())
        assertEquals(NotificationType.POLL_VOTED, notes.path("notifications").path(0).path("type").asText())
        assertEquals("Minh", notes.path("notifications").path(0).path("actorName").asText())
        assertEquals("Lunch?", notes.path("notifications").path(0).path("preview").asText())

        val switched = minh.post("/api/posts/$postId/poll/1")
        val switchedBody = mapper.readTree(switched.bodyAsText())
        assertFalse(switchedBody.path("pollOptions").path(0).path("voted").asBoolean())
        assertTrue(switchedBody.path("pollOptions").path(1).path("voted").asBoolean())
        assertEquals(0, switchedBody.path("pollOptions").path(0).path("voteCount").asInt())
        assertEquals(1, switchedBody.path("pollOptions").path(1).path("voteCount").asInt())

        val unvoted = minh.post("/api/posts/$postId/poll/1")
        val unvotedBody = mapper.readTree(unvoted.bodyAsText())
        assertFalse(unvotedBody.path("pollOptions").path(1).path("voted").asBoolean())
        assertEquals(0, unvotedBody.path("pollOptions").path(1).path("voteCount").asInt())

        val voters = mapper.readTree(linh.get("/api/posts/$postId/voters").bodyAsText())
        assertEquals(3, voters.path("options").size())

        val tooFew = postPoll(linh, "Nope", listOf("Only"))
        assertEquals(HttpStatusCode.BadRequest, tooFew.status)

        val missing = minh.post("/api/posts/$postId/poll/9")
        assertEquals(HttpStatusCode.BadRequest, missing.status)
    }

    private suspend fun postPoll(
        client: io.ktor.client.HttpClient,
        message: String,
        options: List<String>,
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
                    append("location", "Saigon")
                    options.forEachIndexed { index, option ->
                        append("pollOption$index", option)
                    }
                },
            ),
        )
    }

    private fun services(clock: () -> Long): TownServices {
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-poll", "").apply {
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
            config = AppConfig(uploadDir = uploads.root),
            clock = clock,
            hub = hub,
            contactLimiter = RateLimiter(1, 60_000, clock),
            polls = PollService(repo, hub, clock),
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
