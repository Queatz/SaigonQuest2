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
import quest.saigon.town.QuestionService
import quest.saigon.town.TownServices
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestionBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun questionsCanBePostedAndAnsweredPrivately() = testApplication {
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

        val created = postQuestion(linh, "Any pho recs?", "Lunch?")
        assertEquals(HttpStatusCode.Created, created.status)
        val body = mapper.readTree(created.bodyAsText())
        val postId = body.path("id").asText()
        assertTrue(body.path("hasQuestion").asBoolean())
        assertEquals("Lunch?", body.path("questionTitle").asText())
        assertEquals(0, body.path("answerCount").asInt())
        assertFalse(body.path("answered").asBoolean())

        val untitled = postQuestion(linh, "Just asking", "")
        assertEquals(HttpStatusCode.Created, untitled.status)
        val untitledBody = mapper.readTree(untitled.bodyAsText())
        assertTrue(untitledBody.path("hasQuestion").asBoolean())
        assertEquals("", untitledBody.path("questionTitle").asText())

        val answered = minh.post("/api/posts/$postId/answers") {
            contentType(ContentType.Application.Json)
            setBody("""{"text":"Try Pho Hoa"}""")
        }
        assertEquals(HttpStatusCode.OK, answered.status)
        val answeredBody = mapper.readTree(answered.bodyAsText())
        assertTrue(answeredBody.path("answered").asBoolean())
        assertEquals(0, answeredBody.path("answerCount").asInt())

        val authorView = mapper.readTree(linh.get("/api/posts/$postId").bodyAsText())
        assertEquals(1, authorView.path("answerCount").asInt())
        assertFalse(authorView.path("answered").asBoolean())

        val notes = mapper.readTree(linh.get("/api/notifications").bodyAsText())
        assertEquals(NotificationType.QUESTION_ANSWERED, notes.path("notifications").path(0).path("type").asText())
        assertEquals("Minh", notes.path("notifications").path(0).path("actorName").asText())
        assertEquals("Try Pho Hoa", notes.path("notifications").path(0).path("preview").asText())

        val hidden = minh.get("/api/posts/$postId/answers")
        assertEquals(HttpStatusCode.NotFound, hidden.status)

        val listed = mapper.readTree(linh.get("/api/posts/$postId/answers").bodyAsText())
        assertEquals(1, listed.path("answers").size())
        assertEquals("Minh", listed.path("answers").path(0).path("name").asText())
        assertEquals("Try Pho Hoa", listed.path("answers").path(0).path("text").asText())

        val empty = minh.post("/api/posts/$postId/answers") {
            contentType(ContentType.Application.Json)
            setBody("""{"text":"   "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, empty.status)

        val again = minh.post("/api/posts/$postId/answers") {
            contentType(ContentType.Application.Json)
            setBody("""{"text":"Another"}""")
        }
        assertEquals(HttpStatusCode.OK, again.status)
        val stillOne = mapper.readTree(linh.get("/api/posts/$postId/answers").bodyAsText())
        assertEquals(1, stillOne.path("answers").size())
        assertEquals("Try Pho Hoa", stillOne.path("answers").path(0).path("text").asText())
    }

    private suspend fun postQuestion(
        client: io.ktor.client.HttpClient,
        message: String,
        title: String,
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
                    append("askQuestion", "true")
                    if (title.isNotEmpty()) append("questionTitle", title)
                },
            ),
        )
    }

    private fun services(clock: () -> Long): TownServices {
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-question", "").apply {
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
            questions = QuestionService(repo, hub, clock),
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
