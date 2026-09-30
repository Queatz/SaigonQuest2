package quest.saigon

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import quest.saigon.ai.MusicPrompts
import quest.saigon.ai.MusicRender
import quest.saigon.auth.RateLimiter
import quest.saigon.media.ImageOps
import quest.saigon.media.UploadStore
import quest.saigon.town.AvatarStatus
import quest.saigon.town.InMemoryTownRepository
import quest.saigon.town.MusicService
import quest.saigon.town.MusicStatus
import quest.saigon.town.PostRecord
import quest.saigon.town.TownServices
import quest.saigon.town.AvatarService
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MusicBehaviorTest {
    @Test
    fun musicPromptGuidesEnglishSongRegardlessOfLanguage() {
        val guided = MusicPrompts.generationPrompt("Sáng sớm trời mưa")
        assertTrue(guided.contains("English lyrics"))
        assertTrue(guided.contains("even if the theme is in another language"))
        assertTrue(guided.contains("Sáng sớm trời mưa"))
        assertTrue(MusicPrompts.TITLE_SYSTEM.contains("English"))
        assertTrue(MusicPrompts.TITLE_SYSTEM.contains("even if the idea is in another language"))
    }

    @Test
    fun failedGenerationRetriesOnceThenSucceeds() {
        val calls = AtomicInteger(0)
        val services = musicServices { _, _, _ ->
            if (calls.incrementAndGet() == 1) error("first boom")
            MusicRender("id3-bytes".toByteArray(), "Morning Rain", "[verse]\nCoffee aroma fills the room")
        }
        val user = services.repo.createUser("84901111111", "hash", "Linh", 1)
        val music = services.music.enqueue(user, "Rainy morning guitar", "female", false)
        val stored = services.repo.findMusic(music.id)!!
        assertEquals(2, calls.get())
        assertEquals(MusicStatus.READY, stored.status)
        assertEquals("Morning Rain", stored.name)
        assertEquals("[verse]\nCoffee aroma fills the room", stored.lyrics)
        assertNotNull(stored.audioPath)
        assertTrue(stored.audioPath.orEmpty().endsWith(".mp3"))
    }

    @Test
    fun failedGenerationRetriesOnceThenShowsFailure() {
        val calls = AtomicInteger(0)
        val services = musicServices { _, _, _ ->
            calls.incrementAndGet()
            error("always boom")
        }
        val user = services.repo.createUser("84901111112", "hash", "Linh", 1)
        val music = services.music.enqueue(user, "Sad piano", null, true)
        val stored = services.repo.findMusic(music.id)!!
        assertEquals(2, calls.get())
        assertEquals(MusicStatus.FAILED, stored.status)
        assertEquals("always boom", stored.error)
        assertTrue(stored.instrumental)
    }

    @Test
    fun postWithMusicAppearsOnTheFeedAndAudioIsPublic() = testApplication {
        val services = musicServices { _, _, _ -> MusicRender("mp3-body".toByteArray(), "Sáng sớm trời mưa") }
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val http = createClient { install(HttpCookies) }
        val created = http.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0901234567","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.OK, created.status)
        val userId = com.fasterxml.jackson.databind.ObjectMapper().readTree(created.bodyAsText()).path("id").asText()
        val avatar = services.repo.insertAvatar(userId, "smiling", 10)
        services.repo.updateAvatar(avatar.id, AvatarStatus.READY, "avatars/ready.png", null)
        val musicPosted = http.post("/api/music") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"Sáng sớm trời mưa","gender":"female"}""")
        }
        assertEquals(HttpStatusCode.Accepted, musicPosted.status)
        val music = com.fasterxml.jackson.databind.ObjectMapper().readTree(musicPosted.bodyAsText())
        assertEquals("Sáng sớm trời mưa", music.path("name").asText())
        assertEquals("ready", music.path("status").asText())
        val jpeg = ImageOps().writeJpeg(solid(8, 16, Color(40, 90, 160)), 0.85f)
        val posted = http.post("/api/posts") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("message", "Coffee in the rain")
                        append("location", "Cát Lái")
                        append("avatarId", avatar.id)
                        append("musicId", music.path("id").asText())
                        append(
                            "photo",
                            jpeg,
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                            },
                        )
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, posted.status)
        val card = com.fasterxml.jackson.databind.ObjectMapper().readTree(posted.bodyAsText())
        assertEquals("Sáng sớm trời mưa", card.path("musicName").asText())
        val musicUrl = card.path("musicUrl").asText()
        assertTrue(musicUrl.startsWith("/media/music/"))
        val audio = http.get(musicUrl)
        assertEquals(HttpStatusCode.OK, audio.status)
        assertEquals(ContentType.Audio.MPEG, audio.contentType()?.withoutParameters())
        assertTrue(audio.bodyAsBytes().isNotEmpty())
        val feed = com.fasterxml.jackson.databind.ObjectMapper().readTree(http.get("/api/posts").bodyAsText())
        assertEquals("Sáng sớm trời mưa", feed.path("posts").path(0).path("musicName").asText())
        assertEquals(musicUrl, feed.path("posts").path(0).path("musicUrl").asText())
        val mine = com.fasterxml.jackson.databind.ObjectMapper().readTree(http.get("/api/me/posts").bodyAsText())
        assertEquals("Sáng sớm trời mưa", mine.path("posts").path(0).path("musicName").asText())
    }

    @Test
    fun searchListsOtherUsersReadyMusicAndDismissHidesIt() = testApplication {
        val services = musicServices { _, _, _ -> MusicRender("mp3".toByteArray(), "River Song") }
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val author = createClient { install(HttpCookies) }
        author.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0901112233","password":"secretpass"}""")
        }
        val created = author.post("/api/music") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"River at dusk","instrumental":true}""")
        }
        val musicId = com.fasterxml.jackson.databind.ObjectMapper().readTree(created.bodyAsText()).path("id").asText()
        val listener = createClient { install(HttpCookies) }
        listener.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"An","phone":"0902223344","password":"secretpass"}""")
        }
        val found = com.fasterxml.jackson.databind.ObjectMapper().readTree(
            listener.get("/api/music?q=River").bodyAsText(),
        )
        assertEquals(1, found.path("music").size())
        assertEquals("River Song", found.path("music").path(0).path("name").asText())
        assertEquals(musicId, found.path("music").path(0).path("id").asText())

        val user = services.repo.findUserByPhone("84901112233")!!
        val avatar = services.repo.insertAvatar(user.id, "hi", 1)
        services.repo.updateAvatar(avatar.id, AvatarStatus.READY, "avatars/a.png", null)
        services.repo.insertPost(
            PostRecord(
                id = "",
                userId = user.id,
                avatarId = avatar.id,
                photoPath = "posts/x.jpg",
                message = "hello",
                location = "Cát Lái",
                createdAt = 20,
                musicId = musicId,
            ),
        )
        val dismissed = author.delete("/api/music/$musicId")
        assertEquals(HttpStatusCode.OK, dismissed.status)
        val hidden = com.fasterxml.jackson.databind.ObjectMapper().readTree(
            listener.get("/api/music?q=River").bodyAsText(),
        )
        assertEquals(0, hidden.path("music").size())
        val mine = com.fasterxml.jackson.databind.ObjectMapper().readTree(author.get("/api/me/music").bodyAsText())
        assertEquals(0, mine.path("music").size())
        val feed = com.fasterxml.jackson.databind.ObjectMapper().readTree(listener.get("/api/posts").bodyAsText())
        assertEquals("River Song", feed.path("posts").path(0).path("musicName").asText())
    }

    @Test
    fun ownerCanRenameMusicAndLyricsStayOnTheTrack() = testApplication {
        val lyrics = "[verse]\nMorning sunlight slips through the curtain"
        val services = musicServices { _, _, _ -> MusicRender("mp3".toByteArray(), "River Song", lyrics) }
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val author = createClient { install(HttpCookies) }
        author.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0901112233","password":"secretpass"}""")
        }
        val created = author.post("/api/music") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"River at dusk"}""")
        }
        assertEquals(HttpStatusCode.Accepted, created.status)
        val music = com.fasterxml.jackson.databind.ObjectMapper().readTree(created.bodyAsText())
        val musicId = music.path("id").asText()
        assertEquals(lyrics, music.path("lyrics").asText())

        val blank = author.patch("/api/music/$musicId") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"   "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, blank.status)
        assertEquals("invalid_music_name", com.fasterxml.jackson.databind.ObjectMapper().readTree(blank.bodyAsText()).path("error").asText())

        val renamed = author.patch("/api/music/$musicId") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Dawn River"}""")
        }
        assertEquals(HttpStatusCode.OK, renamed.status)
        val body = com.fasterxml.jackson.databind.ObjectMapper().readTree(renamed.bodyAsText())
        assertEquals("Dawn River", body.path("name").asText())
        assertEquals(lyrics, body.path("lyrics").asText())

        val mine = com.fasterxml.jackson.databind.ObjectMapper().readTree(author.get("/api/me/music").bodyAsText())
        assertEquals("Dawn River", mine.path("music").path(0).path("name").asText())
        assertEquals(lyrics, mine.path("music").path(0).path("lyrics").asText())

        val other = createClient { install(HttpCookies) }
        other.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"An","phone":"0902223344","password":"secretpass"}""")
        }
        val stolen = other.patch("/api/music/$musicId") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Stolen"}""")
        }
        assertEquals(HttpStatusCode.NotFound, stolen.status)
    }

    @Test
    fun musicWithoutAKeyFailsFast() = testApplication {
        val services = musicServices(configured = false) { _, _, _ -> MusicRender(byteArrayOf(1), "Nope") }
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val http = createClient { install(HttpCookies) }
        http.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0901112233","password":"secretpass"}""")
        }
        val missing = http.post("/api/music") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"smiling"}""")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, missing.status)
        assertTrue(missing.bodyAsText().contains("qwen_not_configured"))
    }

    private fun musicServices(
        configured: Boolean = true,
        renderer: (String, String?, Boolean) -> MusicRender,
    ): TownServices {
        val clock = { 1_700_000_000_000L }
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-music", "").apply {
            delete()
            mkdirs()
        }).also { it.ensure() }
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
                renderer = renderer,
                configured = configured,
                executor = { it.run() },
                clock = clock,
            ),
            config = AppConfig(uploadDir = uploads.root, qwenApiKey = if (configured) "test-key" else null),
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
