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
import quest.saigon.town.AvatarService
import quest.saigon.town.AvatarStatus
import quest.saigon.town.CommentRecord
import quest.saigon.town.InMemoryTownRepository
import quest.saigon.town.MusicService
import quest.saigon.town.PostRecord
import quest.saigon.town.TownServices
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun firstVisitorBecomesAdminAndLaterUsersAreRejected() = testApplication {
        val services = services()
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }

        val guest = createClient { }
        assertEquals(HttpStatusCode.Unauthorized, guest.get("/api/admin/me").status)
        assertEquals(HttpStatusCode.Unauthorized, guest.get("/api/admin/users").status)

        val admin = createClient { install(HttpCookies) }
        admin.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0901112233","password":"secretpass"}""")
        }
        val claimed = admin.get("/api/admin/me")
        assertEquals(HttpStatusCode.OK, claimed.status)
        assertTrue(mapper.readTree(claimed.bodyAsText()).path("admin").asBoolean())
        assertEquals(HttpStatusCode.OK, admin.get("/api/admin/me").status)

        val other = createClient { install(HttpCookies) }
        other.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Mai","phone":"0902223344","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, other.get("/api/admin/me").status)
        assertEquals(HttpStatusCode.Forbidden, other.get("/api/admin/users").status)
        assertEquals(HttpStatusCode.Forbidden, other.get("/api/admin/posts").status)
        assertEquals(HttpStatusCode.Forbidden, other.get("/api/admin/comments").status)
        assertEquals(HttpStatusCode.Forbidden, other.post("/api/admin/users/missing/pause").status)
        assertEquals(HttpStatusCode.Forbidden, other.delete("/api/admin/comments/missing").status)
    }

    @Test
    fun adminListsUsersAndPostsAndCanPauseOrDelete() = testApplication {
        val services = services()
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }

        val admin = createClient { install(HttpCookies) }
        admin.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0901112233","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.OK, admin.get("/api/admin/me").status)

        val older = services.repo.createUser("0901000001", "hash", "Old", 100).copy(intro = "Lives in Cát Lái")
        services.repo.updateUser(older)
        val newer = services.repo.createUser("0901000002", "hash", "New", 200)
        val avatar = services.repo.insertAvatar(newer.id, "smiling", 10)
        services.repo.updateAvatar(avatar.id, AvatarStatus.READY, "avatars/ready.png", null)
        val post = services.repo.insertPost(
            PostRecord("", newer.id, avatar.id, "posts/x.jpg", "Hello town", "Thảo Điền", 50L, views = 3, replyClicks = 2, shareClicks = 1),
        )

        val page = admin.get("/api/admin/users?limit=2")
        assertEquals(HttpStatusCode.OK, page.status)
        val listed = mapper.readTree(page.bodyAsText())
        assertEquals(2, listed.path("users").size())
        assertEquals("Linh", listed.path("users").path(0).path("name").asText())
        assertEquals("New", listed.path("users").path(1).path("name").asText())
        assertEquals(1, listed.path("users").path(1).path("postCount").asInt())
        assertTrue(listed.path("nextCursor").asText().isNotBlank())
        assertFalse(page.bodyAsText().contains("phone"))
        assertFalse(page.bodyAsText().contains("password"))

        val rest = mapper.readTree(admin.get("/api/admin/users?limit=2&cursor=${listed.path("nextCursor").asText()}").bodyAsText())
        assertEquals("Old", rest.path("users").path(0).path("name").asText())
        assertEquals("Lives in Cát Lái", rest.path("users").path(0).path("intro").asText())
        assertEquals(0, rest.path("users").path(0).path("postCount").asInt())
        assertTrue(rest.path("nextCursor").isNull)

        val member = createClient { install(HttpCookies) }
        val registered = member.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"An","phone":"0903334455","password":"secretpass"}""")
        }
        val memberId = mapper.readTree(registered.bodyAsText()).path("id").asText()
        assertFalse(mapper.readTree(member.get("/api/me").bodyAsText()).path("paused").asBoolean())

        val paused = admin.post("/api/admin/users/$memberId/pause")
        assertEquals(HttpStatusCode.OK, paused.status)
        assertTrue(mapper.readTree(paused.bodyAsText()).path("paused").asBoolean())
        assertTrue(mapper.readTree(member.get("/api/me").bodyAsText()).path("paused").asBoolean())

        val blocked = member.post("/api/posts")
        assertEquals(HttpStatusCode.Forbidden, blocked.status)
        assertTrue(blocked.bodyAsText().contains("account_paused"))

        val resumed = admin.post("/api/admin/users/$memberId/unpause")
        assertEquals(HttpStatusCode.OK, resumed.status)
        assertFalse(mapper.readTree(resumed.bodyAsText()).path("paused").asBoolean())
        assertFalse(mapper.readTree(member.get("/api/me").bodyAsText()).path("paused").asBoolean())

        val posts = mapper.readTree(admin.get("/api/admin/posts").bodyAsText())
        assertEquals(1, posts.path("posts").size())
        assertEquals("Hello town", posts.path("posts").path(0).path("message").asText())
        assertEquals("New", posts.path("posts").path(0).path("authorName").asText())
        assertEquals(3, posts.path("posts").path(0).path("views").asInt())
        assertEquals(2, posts.path("posts").path(0).path("replyClicks").asInt())
        assertEquals(1, posts.path("posts").path(0).path("shareClicks").asInt())
        assertTrue(posts.path("nextCursor").isNull)

        assertEquals(HttpStatusCode.Forbidden, member.delete("/api/admin/posts/${post.id}").status)
        val removed = admin.delete("/api/admin/posts/${post.id}")
        assertEquals(HttpStatusCode.OK, removed.status)
        assertNull(services.repo.findPost(post.id))
        assertEquals(HttpStatusCode.NotFound, admin.delete("/api/admin/posts/${post.id}").status)
        assertEquals(0, mapper.readTree(admin.get("/api/admin/posts").bodyAsText()).path("posts").size())
    }

    @Test
    fun adminListsLatestCommentsAndCanDeleteThem() = testApplication {
        val services = services()
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }

        val admin = createClient { install(HttpCookies) }
        admin.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0901112233","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.OK, admin.get("/api/admin/me").status)
        assertTrue(mapper.readTree(admin.get("/api/me").bodyAsText()).path("admin").asBoolean())

        val owner = services.repo.createUser("0901000001", "hash", "Old", 100)
        val commenter = services.repo.createUser("0901000002", "hash", "New", 200)
        val avatar = services.repo.insertAvatar(owner.id, "smiling", 10)
        services.repo.updateAvatar(avatar.id, AvatarStatus.READY, "avatars/ready.png", null)
        val post = services.repo.insertPost(
            PostRecord("", owner.id, avatar.id, "posts/x.jpg", "Hello town", "Thảo Điền", 50L),
        )
        val older = services.repo.insertComment(
            CommentRecord("", post.id, commenter.id, "first hello", null, 10),
        )
        val newer = services.repo.insertComment(
            CommentRecord("", post.id, commenter.id, "later hello", null, 20),
        )

        val member = createClient { install(HttpCookies) }
        member.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"An","phone":"0903334455","password":"secretpass"}""")
        }
        assertFalse(mapper.readTree(member.get("/api/me").bodyAsText()).path("admin").asBoolean())
        assertEquals(HttpStatusCode.Forbidden, member.get("/api/admin/comments").status)
        assertEquals(HttpStatusCode.NotFound, member.delete("/api/posts/${post.id}/comments/${newer.id}").status)

        val page = admin.get("/api/admin/comments?limit=1")
        assertEquals(HttpStatusCode.OK, page.status)
        val listed = mapper.readTree(page.bodyAsText())
        assertEquals(1, listed.path("comments").size())
        assertEquals("later hello", listed.path("comments").path(0).path("text").asText())
        assertEquals(newer.id, listed.path("comments").path(0).path("id").asText())
        assertEquals(post.id, listed.path("comments").path(0).path("postId").asText())
        assertEquals("Hello town", listed.path("comments").path(0).path("postText").asText())
        assertTrue(listed.path("nextCursor").asText().isNotBlank())

        val rest = mapper.readTree(admin.get("/api/admin/comments?limit=1&cursor=${listed.path("nextCursor").asText()}").bodyAsText())
        assertEquals("first hello", rest.path("comments").path(0).path("text").asText())
        assertEquals(older.id, rest.path("comments").path(0).path("id").asText())
        assertTrue(rest.path("nextCursor").isNull)

        assertEquals(HttpStatusCode.OK, admin.delete("/api/posts/${post.id}/comments/${newer.id}").status)
        assertEquals(HttpStatusCode.OK, admin.delete("/api/admin/comments/${older.id}").status)
        assertNull(services.repo.findComment(older.id))
        assertNull(services.repo.findComment(newer.id))
        assertEquals(0, mapper.readTree(admin.get("/api/admin/comments").bodyAsText()).path("comments").size())
        assertEquals(HttpStatusCode.NotFound, admin.delete("/api/admin/comments/${older.id}").status)
    }

    private fun services(): TownServices {
        val clock = { 1_700_000_000_000L }
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-admin", "").apply {
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
}
