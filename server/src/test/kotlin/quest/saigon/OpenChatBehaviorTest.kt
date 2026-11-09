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
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import quest.saigon.ai.MusicRender
import quest.saigon.auth.RateLimiter
import quest.saigon.media.ImageOps
import quest.saigon.media.UploadStore
import quest.saigon.town.AvatarService
import quest.saigon.town.InMemoryTownRepository
import quest.saigon.town.MusicService
import quest.saigon.town.TownServices
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenChatBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun postCanAttachAndOpenAGroupChat() = testApplication {
        var now = 1_700_000_000_000L
        val services = services { now }
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val linh = createClient { install(HttpCookies) }
        val minh = createClient { install(HttpCookies) }
        linh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905550101","password":"secretpass"}""")
        }
        val minhUser = mapper.readTree(minh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Minh","phone":"0905550102","password":"secretpass"}""")
        }.bodyAsText()).path("id").asText()
        val group = mapper.readTree(
            linh.post("/api/chats") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Dinner"}""")
            }.bodyAsText(),
        )
        val chatId = group.path("id").asText()
        val created = postWithOpenChat(linh, chatId, hours = 2, remaining = 1)
        assertEquals(201, created.status.value)
        val post = mapper.readTree(created.bodyAsText())
        assertEquals(chatId, post.path("openChatId").asText())
        assertEquals("Dinner", post.path("openChatName").asText())
        assertEquals(now + 2 * 60 * 60 * 1000, post.path("openChatExpiresAt").asLong())
        assertEquals(1, post.path("openChatRemaining").asInt())

        val postId = post.path("id").asText()
        val authorJoin = linh.post("/api/posts/$postId/open-chat")
        assertEquals(200, authorJoin.status.value)
        assertEquals(chatId, mapper.readTree(authorJoin.bodyAsText()).path("id").asText())

        val viewerJoin = minh.post("/api/posts/$postId/open-chat")
        assertEquals(200, viewerJoin.status.value)
        val updated = mapper.readTree(linh.get("/api/posts/$postId").bodyAsText())
        assertEquals(0, updated.path("openChatRemaining").asInt())
        assertTrue(updated.path("openChatJoined").asBoolean())

        val third = createClient { install(HttpCookies) }
        third.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"An","phone":"0905550103","password":"secretpass"}""")
        }
        assertEquals(400, third.post("/api/posts/$postId/open-chat").status.value)

        val skipped = mapper.readTree(postWithOpenChat(linh, chatId = null, hours = 2, remaining = null).bodyAsText())
        assertTrue(skipped.path("openChatId").isMissingNode || skipped.path("openChatId").isNull)

        val createdGroupPostResponse = postWithOpenChat(
            linh,
            chatId = null,
            hours = null,
            remaining = null,
            createNew = true,
            name = "Weekend plans",
        )
        assertEquals(201, createdGroupPostResponse.status.value)
        val createdGroupPost = mapper.readTree(createdGroupPostResponse.bodyAsText())
        assertEquals("Weekend plans", createdGroupPost.path("openChatName").asText())
        assertEquals(now + 24 * 60 * 60 * 1000, createdGroupPost.path("openChatExpiresAt").asLong())
        assertTrue(createdGroupPost.path("openChatRemaining").isNull)

        assertEquals(400, postWithOpenChat(linh, chatId, hours = 25, remaining = null).status.value)
        assertEquals(400, postWithOpenChat(linh, chatId, hours = 24, remaining = 11).status.value)

        val direct = mapper.readTree(
            linh.post("/api/chats") {
                contentType(ContentType.Application.Json)
                setBody("""{"userId":"$minhUser","text":"Hello"}""")
            }.bodyAsText(),
        )
        assertTrue(direct.path("id").asText().isNotBlank())
        assertEquals("direct", direct.path("type").asText())
        assertEquals(400, postWithOpenChat(linh, direct.path("id").asText(), 2, null).status.value)

        val privateGroup = mapper.readTree(
            minh.post("/api/chats") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Private"}""")
            }.bodyAsText(),
        )
        assertEquals(400, postWithOpenChat(linh, privateGroup.path("id").asText(), 2, null).status.value)

        val expiring = mapper.readTree(
            postWithOpenChat(linh, chatId, hours = 1, remaining = null).bodyAsText(),
        )
        now += 60 * 60 * 1000 + 1
        assertEquals(400, third.post("/api/posts/${expiring.path("id").asText()}/open-chat").status.value)
    }

    private suspend fun postWithOpenChat(
        client: io.ktor.client.HttpClient,
        chatId: String?,
        hours: Int?,
        remaining: Int?,
        createNew: Boolean = false,
        name: String = "",
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
                    append("message", "Hello town")
                    append("location", "Saigon")
                    append("addOpenChat", "true")
                    if (createNew) {
                        append("openChatNew", "true")
                        if (name.isNotBlank()) append("openChatName", name)
                    } else {
                        chatId?.let { append("openChatId", it) }
                    }
                    hours?.let { append("openChatHours", it.toString()) }
                    remaining?.let { append("openChatRemaining", it.toString()) }
                },
            ),
        )
    }

    private fun services(clock: () -> Long): TownServices {
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-open-chat", "").apply {
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

    private fun solid(width: Int, height: Int, color: Color): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = color
        graphics.fillRect(0, 0, width, height)
        graphics.dispose()
        return image
    }
}