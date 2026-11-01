package quest.saigon

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
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

class ChatBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun directChatsStayPrivateAndNotifySeparately() = testApplication {
        var now = 1_700_000_000_000L
        val services = services { now }
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val linh = createClient { install(HttpCookies) }
        val minh = createClient { install(HttpCookies) }
        val an = createClient { install(HttpCookies) }
        linh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905550101","password":"secretpass"}""")
        }
        minh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Minh","phone":"0905550102","password":"secretpass"}""")
        }
        an.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"An","phone":"0905550103","password":"secretpass"}""")
        }
        val minhId = mapper.readTree(minh.get("/api/me").bodyAsText()).path("id").asText()
        val created = mapper.readTree(
            linh.post("/api/chats") {
                contentType(ContentType.Application.Json)
                setBody("""{"userId":"$minhId","text":"Hello Minh"}""")
            }.bodyAsText(),
        )
        val chatId = created.path("id").asText()
        assertEquals("direct", created.path("type").asText())
        assertEquals("Minh", created.path("name").asText())
        assertEquals(0, created.path("unreadCount").asInt())

        val minhList = mapper.readTree(minh.get("/api/chats").bodyAsText())
        assertEquals(1, minhList.path("unreadCount").asInt())
        assertEquals(1, minhList.path("chats")[0].path("unreadCount").asInt())
        assertTrue(minhList.path("chats")[0].path("lastMessageText").asText().contains("Hello"))

        val notes = mapper.readTree(minh.get("/api/notifications").bodyAsText())
        assertEquals(0, notes.path("unreadCount").asInt())
        assertEquals(0, notes.path("notifications").size())

        assertEquals(HttpStatusCode.NotFound, an.get("/api/chats/$chatId").status)
        assertEquals(
            HttpStatusCode.NotFound,
            an.post("/api/chats/$chatId/messages") {
                contentType(ContentType.Application.Json)
                setBody("""{"text":"spy"}""")
            }.status,
        )

        minh.post("/api/chats/$chatId/read")
        val afterRead = mapper.readTree(minh.get("/api/chats").bodyAsText())
        assertEquals(0, afterRead.path("unreadCount").asInt())
        assertEquals(0, afterRead.path("chats")[0].path("unreadCount").asInt())

        now += 1
        minh.post("/api/chats/$chatId/messages") {
            contentType(ContentType.Application.Json)
            setBody("""{"text":"Hi Linh"}""")
        }
        val reused = mapper.readTree(linh.get("/api/chats/direct/$minhId").bodyAsText())
        assertEquals(chatId, reused.path("id").asText())
    }

    @Test
    fun groupsSupportMembersReactionsAndPaging() = testApplication {
        var now = 1_700_000_000_000L
        val services = services { now }
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val linh = createClient { install(HttpCookies) }
        val minh = createClient { install(HttpCookies) }
        linh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905550201","password":"secretpass"}""")
        }
        minh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Minh","phone":"0905550202","password":"secretpass"}""")
        }
        val minhId = mapper.readTree(minh.get("/api/me").bodyAsText()).path("id").asText()
        val group = mapper.readTree(
            linh.post("/api/chats") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Dinner"}""")
            }.bodyAsText(),
        )
        val chatId = group.path("id").asText()
        assertEquals("group", group.path("type").asText())
        assertTrue(group.path("owner").asBoolean())

        linh.post("/api/chats/$chatId/members") {
            contentType(ContentType.Application.Json)
            setBody("""{"userId":"$minhId"}""")
        }
        assertEquals(
            HttpStatusCode.Conflict,
            linh.post("/api/chats/$chatId/members") {
                contentType(ContentType.Application.Json)
                setBody("""{"userId":"$minhId"}""")
            }.status,
        )
        val people = mapper.readTree(linh.get("/api/chats/$chatId/people?q=Minh").bodyAsText()).path("people")
        assertEquals(0, people.size())
        val renamed = mapper.readTree(
            linh.patch("/api/chats/$chatId") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Brunch"}""")
            }.bodyAsText(),
        )
        assertEquals("Brunch", renamed.path("name").asText())
        val minhChats = mapper.readTree(minh.get("/api/chats").bodyAsText())
        assertEquals(1, minhChats.path("unreadCount").asInt())
        assertEquals("Brunch", minhChats.path("chats")[0].path("name").asText())

        now += 1
        val first = mapper.readTree(
            linh.post("/api/chats/$chatId/messages") {
                contentType(ContentType.Application.Json)
                setBody("""{"text":"first"}""")
            }.bodyAsText(),
        )
        val minhEvents = java.util.concurrent.CopyOnWriteArrayList<String>()
        services.chatHub.subscribe(minhId) { minhEvents += it }
        val reacted = mapper.readTree(
            linh.post("/api/chats/$chatId/messages/${first.path("id").asText()}/reactions") {
                contentType(ContentType.Application.Json)
                setBody("""{"emoji":"❤️"}""")
            }.bodyAsText(),
        )
        assertEquals(1, reacted.path("reactions").size())
        assertTrue(reacted.path("reactions")[0].path("mine").asBoolean())
        val reactionEvent = minhEvents.map { mapper.readTree(it) }.first { it.path("type").asText() == "reaction" }
        assertEquals(chatId, reactionEvent.path("chatId").asText())
        assertEquals(first.path("id").asText(), reactionEvent.path("messageId").asText())
        assertEquals(1, reactionEvent.path("reactions").size())
        assertEquals("❤️", reactionEvent.path("reactions")[0].path("emoji").asText())
        assertFalse(reactionEvent.path("reactions")[0].path("mine").asBoolean())
        assertEquals(first.path("id").asText(), reactionEvent.path("message").path("id").asText())
        linh.post("/api/chats/$chatId/messages/${first.path("id").asText()}/reactions") {
            contentType(ContentType.Application.Json)
            setBody("""{"emoji":"👍"}""")
        }
        val details = mapper.readTree(
            minh.get("/api/chats/$chatId/messages/${first.path("id").asText()}/reactions").bodyAsText(),
        )
        assertEquals(2, details.path("reactions").size())

        repeat(51) { index ->
            now += 1
            linh.post("/api/chats/$chatId/messages") {
                contentType(ContentType.Application.Json)
                setBody("""{"text":"m$index"}""")
            }
        }
        val page = mapper.readTree(minh.get("/api/chats/$chatId/messages?limit=50").bodyAsText())
        assertEquals(50, page.path("messages").size())
        assertTrue(page.path("nextCursor").asText().isNotBlank())
        val older = mapper.readTree(
            minh.get("/api/chats/$chatId/messages?limit=50&cursor=${page.path("nextCursor").asText()}").bodyAsText(),
        )
        assertTrue(older.path("messages").size() >= 2)

        now += 1
        val direct = mapper.readTree(
            linh.post("/api/chats") {
                contentType(ContentType.Application.Json)
                setBody("""{"userId":"$minhId","text":"A direct message"}""")
            }.bodyAsText(),
        )
        val allChats = mapper.readTree(linh.get("/api/chats?limit=1").bodyAsText())
        assertEquals(direct.path("id").asText(), allChats.path("chats")[0].path("id").asText())
        val groupChats = mapper.readTree(linh.get("/api/chats?type=group&limit=1").bodyAsText())
        assertEquals(1, groupChats.path("chats").size())
        assertEquals(chatId, groupChats.path("chats")[0].path("id").asText())
        assertTrue(groupChats.path("nextCursor").isNull)
        assertEquals(HttpStatusCode.OK, linh.post("/api/chats/${direct.path("id").asText()}/leave").status)

        linh.post("/api/chats/$chatId/members/$minhId/owner") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        val members = mapper.readTree(minh.get("/api/chats/$chatId/members").bodyAsText()).path("members")
        var owner = false
        for (i in 0 until members.size()) {
            if (members[i].path("id").asText() == minhId && members[i].path("owner").asBoolean()) owner = true
        }
        assertTrue(owner)

        assertEquals(HttpStatusCode.OK, minh.delete("/api/chats/$chatId").status)
        assertEquals(HttpStatusCode.NotFound, linh.get("/api/chats/$chatId").status)
        val afterDelete = mapper.readTree(linh.get("/api/chats").bodyAsText())
        assertEquals(0, afterDelete.path("chats").size())
        val leftoverNotes = mapper.readTree(linh.get("/api/notifications").bodyAsText())
        assertEquals(0, leftoverNotes.path("unreadCount").asInt())
    }

    @Test
    fun strangersCannotBeMessagedWithoutAuthAndCopyMatches() {
        assertEquals(
            "New message from Minh",
            NotificationCopy.title(Language.EN, "chat_message", "Minh", "Hi"),
        )
        assertEquals(
            "Minh added you to a group chat",
            NotificationCopy.title(Language.EN, "chat_added", "Minh", "Dinner"),
        )
        assertEquals("/messages/abc", NotificationCopy.path("chat_message", "", "abc"))
        assertEquals("/messages", NotificationCopy.path("chat_deleted", "", "abc"))
        assertEquals(AppRoute.Chat("abc"), matchAppRoute(NotificationCopy.path("chat_message", "", "abc")))
    }

    private fun services(clock: () -> Long): TownServices {
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-chat", "").apply {
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
