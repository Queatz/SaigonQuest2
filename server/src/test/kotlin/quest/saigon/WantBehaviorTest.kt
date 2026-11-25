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

    @Test
    fun wantRosterAndChatCarryWantAttachment() = testApplication {
        val services = services()
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val linh = createClient { install(HttpCookies) }
        val minh = createClient { install(HttpCookies) }
        val an = createClient { install(HttpCookies) }
        val outsider = createClient { install(HttpCookies) }
        val duy = createClient { install(HttpCookies) }
        val hoa = createClient { install(HttpCookies) }
        val my = createClient { install(HttpCookies) }
        register(linh, "Linh", "0905550011")
        register(minh, "Minh", "0905550012")
        register(an, "An", "0905550013")
        register(outsider, "Bao", "0905550014")
        register(duy, "Duy", "0905550015")
        register(hoa, "Hoa", "0905550016")
        register(my, "My", "0905550017")
        val minhId = mapper.readTree(minh.get("/api/me").bodyAsText()).path("id").asText()
        val anId = mapper.readTree(an.get("/api/me").bodyAsText()).path("id").asText()
        val outsiderId = mapper.readTree(outsider.get("/api/me").bodyAsText()).path("id").asText()

        val created = linh.post("/api/wants") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Go hiking"}""")
        }
        val wantId = mapper.readTree(created.bodyAsText()).path("id").asText()
        minh.post("/api/wants/$wantId/join")
        an.post("/api/wants/$wantId/join")
        duy.post("/api/wants/$wantId/join")
        hoa.post("/api/wants/$wantId/join")
        my.post("/api/wants/$wantId/join")

        val cardWant = mapper.readTree(linh.get("/api/me/wants").bodyAsText()).path("wants").path(0)
        assertEquals(6, cardWant.path("memberCount").asInt())
        assertEquals(5, cardWant.path("members").size())
        val cardMemberNames = (0 until cardWant.path("members").size()).map { cardWant.path("members").path(it).path("name").asText() }
        assertEquals(5, cardMemberNames.toSet().size)
        val want = mapper.readTree(linh.get("/api/wants/$wantId").bodyAsText())
        assertEquals("Go hiking", want.path("name").asText())
        assertEquals(6, want.path("members").size())

        val direct = linh.post("/api/wants/$wantId/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"userIds":["$minhId"]}""")
        }
        assertEquals(HttpStatusCode.Created, direct.status)
        val directBody = mapper.readTree(direct.bodyAsText())
        assertEquals("direct", directBody.path("type").asText())
        val directMessages = mapper.readTree(linh.get("/api/chats/${directBody.path("id").asText()}/messages").bodyAsText())
            .path("messages")
        assertEquals("want", directMessages.path(0).path("attachment").path("type").asText())
        assertEquals(wantId, directMessages.path(0).path("attachment").path("wantId").asText())
        assertEquals("Go hiking", directMessages.path(0).path("attachment").path("wantName").asText())

        val group = linh.post("/api/wants/$wantId/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"userIds":["$minhId","$anId"]}""")
        }
        assertEquals(HttpStatusCode.Created, group.status)
        val groupBody = mapper.readTree(group.bodyAsText())
        assertEquals("group", groupBody.path("type").asText())
        val groupMessages = mapper.readTree(linh.get("/api/chats/${groupBody.path("id").asText()}/messages").bodyAsText())
            .path("messages")
        assertEquals("want", groupMessages.path(0).path("attachment").path("type").asText())

        val invalidChat = linh.post("/api/wants/$wantId/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"userIds":["$outsiderId"]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidChat.status)
        assertEquals("no_chat_members", mapper.readTree(invalidChat.bodyAsText()).path("error").asText())
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