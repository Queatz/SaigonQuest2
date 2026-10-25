package quest.saigon

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.delete
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
import quest.saigon.ai.QuickReplyPrompts
import quest.saigon.auth.RateLimiter
import quest.saigon.media.ImageOps
import quest.saigon.media.UploadStore
import quest.saigon.town.AvatarService
import quest.saigon.town.AvatarStatus
import quest.saigon.town.InMemoryTownRepository
import quest.saigon.town.MusicService
import quest.saigon.town.NotificationType
import quest.saigon.town.PostRecord
import quest.saigon.town.TownServices
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun quickReplyParserReadsJsonArray() {
        val parsed = QuickReplyPrompts.parse("""["I'm down 🙋‍♂️", "What time? ⌚", "Let's ride 🏍️", "Which room? 📍", "Count me in"]""")
        assertEquals(5, parsed.size)
        assertEquals("I'm down 🙋‍♂️", parsed.first())
    }

    @Test
    fun commentsPageByFiftyAndNotifyPostOwnerAndThread() = testApplication {
        var now = 1_700_000_000_000L
        val services = services { now }
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val owner = createClient { install(HttpCookies) }
        val friend = createClient { install(HttpCookies) }
        val other = createClient { install(HttpCookies) }

        owner.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0901110001","password":"secretpass"}""")
        }
        friend.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Minh","phone":"0901110002","password":"secretpass"}""")
        }
        other.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"An","phone":"0901110003","password":"secretpass"}""")
        }
        val ownerId = mapper.readTree(owner.get("/api/me").bodyAsText()).path("id").asText()
        val avatar = services.repo.insertAvatar(ownerId, "hi", now)
        services.repo.updateAvatar(avatar.id, AvatarStatus.READY, "avatars/a.png", null)
        val post = services.repo.insertPost(
            PostRecord("", ownerId, avatar.id, "posts/x.jpg", "Coffee at 8?", "D1", now),
        )
        now += 1

        val guest = createClient { }
        assertEquals(HttpStatusCode.Unauthorized, guest.post("/api/posts/${post.id}/comments") {
            contentType(ContentType.Application.Json)
            setBody("""{"text":"I'm down"}""")
        }.status)

        val first = friend.post("/api/posts/${post.id}/comments") {
            contentType(ContentType.Application.Json)
            setBody("""{"text":"I'm down! What time?"}""")
        }
        assertEquals(HttpStatusCode.Created, first.status)
        now += 1

        val ownerNotes = mapper.readTree(owner.get("/api/notifications").bodyAsText())
        assertEquals(1, ownerNotes.path("unreadCount").asInt())
        assertEquals(NotificationType.POST_COMMENT, ownerNotes.path("notifications").path(0).path("type").asText())
        assertFalse(ownerNotes.path("notifications").path(0).path("read").asBoolean())
        val friendNotes = mapper.readTree(friend.get("/api/notifications").bodyAsText())
        assertEquals(0, friendNotes.path("unreadCount").asInt())

        other.post("/api/posts/${post.id}/comments") {
            contentType(ContentType.Application.Json)
            setBody("""{"text":"Same, which room?"}""")
        }
        now += 1

        val afterOther = mapper.readTree(friend.get("/api/notifications").bodyAsText())
        assertEquals(1, afterOther.path("unreadCount").asInt())
        assertEquals(NotificationType.THREAD_COMMENT, afterOther.path("notifications").path(0).path("type").asText())
        assertEquals(2, mapper.readTree(owner.get("/api/notifications").bodyAsText()).path("unreadCount").asInt())

        other.post("/api/posts/${post.id}/comments") {
            contentType(ContentType.Application.Json)
            setBody("""{"text":"Still waiting"}""")
        }
        now += 1
        assertEquals(1, mapper.readTree(friend.get("/api/notifications").bodyAsText()).path("unreadCount").asInt())
        assertEquals(3, mapper.readTree(owner.get("/api/notifications").bodyAsText()).path("unreadCount").asInt())

        friend.post("/api/posts/${post.id}/comments") {
            contentType(ContentType.Application.Json)
            setBody("""{"text":"Ok let's go"}""")
        }
        now += 1
        other.post("/api/posts/${post.id}/comments") {
            contentType(ContentType.Application.Json)
            setBody("""{"text":"See you"}""")
        }
        now += 1
        assertEquals(2, mapper.readTree(friend.get("/api/notifications").bodyAsText()).path("unreadCount").asInt())

        val noteId = afterOther.path("notifications").path(0).path("id").asText()
        val marked = friend.post("/api/notifications/$noteId/read")
        assertEquals(HttpStatusCode.OK, marked.status)
        assertTrue(mapper.readTree(marked.bodyAsText()).path("read").asBoolean())
        assertEquals(1, mapper.readTree(friend.get("/api/notifications").bodyAsText()).path("unreadCount").asInt())

        repeat(48) { index ->
            friend.post("/api/posts/${post.id}/comments") {
                contentType(ContentType.Application.Json)
                setBody("""{"text":"note $index"}""")
            }
            now += 1
        }
        val page = mapper.readTree(owner.get("/api/posts/${post.id}/comments").bodyAsText())
        assertEquals(50, page.path("comments").size())
        assertEquals(53, page.path("total").asInt())
        val cursor = page.path("nextCursor").asText()
        assertTrue(cursor.isNotBlank())
        val next = mapper.readTree(owner.get("/api/posts/${post.id}/comments?cursor=$cursor").bodyAsText())
        assertEquals(3, next.path("comments").size())
        assertTrue(next.path("nextCursor").isNull)

        val single = mapper.readTree(owner.get("/api/posts/${post.id}").bodyAsText())
        assertEquals(post.id, single.path("id").asText())
        assertEquals(53, single.path("commentCount").asInt())
        assertTrue(single.path("comments").size() > 0)
    }

    @Test
    fun replyQuotesParentAndFeedCarriesQuickReplies() = testApplication {
        var now = 1_700_000_000_000L
        val services = services { now }
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val http = createClient { install(HttpCookies) }
        http.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0902220001","password":"secretpass"}""")
        }
        val userId = mapper.readTree(http.get("/api/me").bodyAsText()).path("id").asText()
        val avatar = services.repo.insertAvatar(userId, "hi", now)
        services.repo.updateAvatar(avatar.id, AvatarStatus.READY, "avatars/a.png", null)
        val post = services.repo.insertPost(
            PostRecord("", userId, avatar.id, "posts/x.jpg", "Night ride?", "D3", now),
        )
        now += 1
        val parent = mapper.readTree(
            http.post("/api/posts/${post.id}/comments") {
                contentType(ContentType.Application.Json)
                setBody("""{"text":"What time?"}""")
            }.bodyAsText(),
        )
        now += 1
        val reply = mapper.readTree(
            http.post("/api/posts/${post.id}/comments") {
                contentType(ContentType.Application.Json)
                setBody("""{"text":"8pm at the bridge","replyToId":"${parent.path("id").asText()}"}""")
            }.bodyAsText(),
        )
        assertEquals("Linh", reply.path("replyToAuthorName").asText())
        assertEquals("What time?", reply.path("replyToText").asText())

        services.replies.enqueue(post.id, post.message)
        val feed = mapper.readTree(http.get("/api/posts").bodyAsText())
        val card = feed.path("posts").path(0)
        assertEquals(0, card.path("quickReplies").size())
        assertEquals(2, card.path("commentCount").asInt())
        assertEquals(userId, card.path("authorId").asText())
    }

    @Test
    fun ownerAndAuthorCanDeleteCommentsAndMarkAllRead() = testApplication {
        var now = 1_700_000_000_000L
        val services = services { now }
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val owner = createClient { install(HttpCookies) }
        val friend = createClient { install(HttpCookies) }
        val other = createClient { install(HttpCookies) }
        owner.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0903330001","password":"secretpass"}""")
        }
        friend.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Minh","phone":"0903330002","password":"secretpass"}""")
        }
        other.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"An","phone":"0903330003","password":"secretpass"}""")
        }
        val ownerId = mapper.readTree(owner.get("/api/me").bodyAsText()).path("id").asText()
        val avatar = services.repo.insertAvatar(ownerId, "hi", now)
        services.repo.updateAvatar(avatar.id, AvatarStatus.READY, "avatars/a.png", null)
        val post = services.repo.insertPost(
            PostRecord("", ownerId, avatar.id, "posts/x.jpg", "Coffee?", "D1", now),
        )
        now += 1
        val mine = mapper.readTree(
            friend.post("/api/posts/${post.id}/comments") {
                contentType(ContentType.Application.Json)
                setBody("""{"text":"I'm down"}""")
            }.bodyAsText(),
        )
        now += 1
        val theirs = mapper.readTree(
            other.post("/api/posts/${post.id}/comments") {
                contentType(ContentType.Application.Json)
                setBody("""{"text":"Me too"}""")
            }.bodyAsText(),
        )
        now += 1
        assertEquals(HttpStatusCode.NotFound, friend.delete("/api/posts/${post.id}/comments/${theirs.path("id").asText()}").status)
        assertEquals(HttpStatusCode.OK, friend.delete("/api/posts/${post.id}/comments/${mine.path("id").asText()}").status)
        assertEquals(HttpStatusCode.OK, owner.delete("/api/posts/${post.id}/comments/${theirs.path("id").asText()}").status)
        assertEquals(0, mapper.readTree(owner.get("/api/posts/${post.id}").bodyAsText()).path("commentCount").asInt())

        val before = mapper.readTree(owner.get("/api/notifications").bodyAsText())
        assertTrue(before.path("unreadCount").asInt() > 0)
        val marked = owner.post("/api/notifications/read-all")
        assertEquals(HttpStatusCode.OK, marked.status)
        assertEquals(0, mapper.readTree(marked.bodyAsText()).path("unreadCount").asInt())
        assertEquals(0, mapper.readTree(owner.get("/api/notifications").bodyAsText()).path("unreadCount").asInt())
    }

    @Test
    fun creatingAPostCanAttachAFirstComment() = testApplication {
        val services = services { 1_700_000_000_000L }
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val http = createClient { install(HttpCookies) }
        http.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0901110099","password":"secretpass"}""")
        }
        val jpeg = ImageOps().writeJpeg(solid(8, 16, Color(40, 90, 160)), 0.85f)
        fun postBody(first: String?) = MultiPartFormDataContent(
            formData {
                append("message", "Coffee at 8?")
                append("location", "D1")
                if (first != null) append("firstComment", first)
                append(
                    "photo",
                    jpeg,
                    Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                    },
                )
            },
        )

        val posted = http.post("/api/posts") { setBody(postBody("See you there")) }
        assertEquals(HttpStatusCode.Created, posted.status)
        val created = mapper.readTree(posted.bodyAsText())
        assertEquals(1, created.path("commentCount").asInt())
        assertEquals("See you there", created.path("comments").get(0).path("text").asText())
        val comments = mapper.readTree(http.get("/api/posts/${created.path("id").asText()}/comments").bodyAsText())
        assertEquals(1, comments.path("total").asInt())
        assertEquals("See you there", comments.path("comments").get(0).path("text").asText())
        assertEquals(created.path("authorId").asText(), comments.path("comments").get(0).path("userId").asText())

        val blank = http.post("/api/posts") { setBody(postBody("   ")) }
        assertEquals(HttpStatusCode.Created, blank.status)
        val blankId = mapper.readTree(blank.bodyAsText()).path("id").asText()
        assertEquals(0, mapper.readTree(http.get("/api/posts/$blankId/comments").bodyAsText()).path("total").asInt())

        val invalid = http.post("/api/posts") { setBody(postBody("x".repeat(Limits.COMMENT_MAX + 1))) }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        assertTrue(invalid.bodyAsText().contains("invalid_comment"))
    }

    private fun services(clock: () -> Long): TownServices {
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-comment", "").apply {
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
                renderer = { _, _, _ -> MusicRender(byteArrayOf(1, 2, 3, 4), "Song") },
                configured = false,
                executor = { it.run() },
                clock = clock,
            ),
            config = AppConfig(uploadDir = uploads.root, qwenApiKey = null),
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
