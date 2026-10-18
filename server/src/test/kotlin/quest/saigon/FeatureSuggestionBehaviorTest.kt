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
import quest.saigon.town.InMemoryTownRepository
import quest.saigon.town.MusicService
import quest.saigon.town.TownServices
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureSuggestionBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun guestsCanReadButOnlySignedInUsersCanSuggestAndVote() = testApplication {
        val services = services()
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val guest = createClient { }
        val empty = guest.get("/api/features")
        assertEquals(HttpStatusCode.OK, empty.status)
        assertEquals(0, mapper.readTree(empty.bodyAsText()).path("features").size())
        assertEquals(
            HttpStatusCode.Unauthorized,
            guest.post("/api/features") {
                contentType(ContentType.Application.Json)
                setBody("""{"text":"Dark mode"}""")
            }.status,
        )

        val linh = createClient { install(HttpCookies) }
        linh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905550101","password":"secretpass"}""")
        }
        val created = linh.post("/api/features") {
            contentType(ContentType.Application.Json)
            setBody("""{"text":"Dark mode"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val item = mapper.readTree(created.bodyAsText())
        val id = item.path("id").asText()
        assertEquals("Dark mode", item.path("text").asText())
        assertEquals(0, item.path("voteCount").asInt())
        assertFalse(item.path("voted").asBoolean())
        assertFalse(item.path("resolved").asBoolean())

        assertEquals(
            HttpStatusCode.BadRequest,
            linh.post("/api/features") {
                contentType(ContentType.Application.Json)
                setBody("""{"text":"   "}""")
            }.status,
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            guest.post("/api/features/$id/vote").status,
        )

        val voted = linh.post("/api/features/$id/vote")
        assertEquals(HttpStatusCode.OK, voted.status)
        val afterVote = mapper.readTree(voted.bodyAsText())
        assertEquals(1, afterVote.path("voteCount").asInt())
        assertTrue(afterVote.path("voted").asBoolean())

        val unvoted = linh.post("/api/features/$id/vote")
        assertEquals(HttpStatusCode.OK, unvoted.status)
        val afterUnvote = mapper.readTree(unvoted.bodyAsText())
        assertEquals(0, afterUnvote.path("voteCount").asInt())
        assertFalse(afterUnvote.path("voted").asBoolean())
    }

    @Test
    fun topListRanksByVotesAndAdminsCanResolveOrDelete() = testApplication {
        val services = services()
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val admin = createClient { install(HttpCookies) }
        val linh = createClient { install(HttpCookies) }
        val minh = createClient { install(HttpCookies) }
        admin.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Admin","phone":"0905550100","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.OK, admin.get("/api/admin/me").status)
        linh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905550101","password":"secretpass"}""")
        }
        minh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Minh","phone":"0905550102","password":"secretpass"}""")
        }

        val low = createFeature(linh, "Low votes")
        val mid = createFeature(linh, "Mid votes")
        val high = createFeature(linh, "High votes")
        val extra = createFeature(linh, "Also popular")
        vote(linh, high)
        vote(minh, high)
        vote(admin, high)
        vote(linh, extra)
        vote(minh, extra)
        vote(linh, mid)

        val top = mapper.readTree(linh.get("/api/features?limit=3&open=1").bodyAsText()).path("features")
        assertEquals(3, top.size())
        assertEquals("High votes", top.path(0).path("text").asText())
        assertEquals(3, top.path(0).path("voteCount").asInt())
        assertEquals("Also popular", top.path(1).path("text").asText())
        assertEquals("Mid votes", top.path(2).path("text").asText())

        assertEquals(HttpStatusCode.Forbidden, linh.post("/api/features/$high/resolve").status)
        assertEquals(HttpStatusCode.Forbidden, linh.delete("/api/features/$low").status)

        val resolved = admin.post("/api/features/$high/resolve")
        assertEquals(HttpStatusCode.OK, resolved.status)
        assertTrue(mapper.readTree(resolved.bodyAsText()).path("resolved").asBoolean())

        val openTop = mapper.readTree(linh.get("/api/features?limit=3&open=1").bodyAsText()).path("features")
        assertEquals("Also popular", openTop.path(0).path("text").asText())
        val all = mapper.readTree(linh.get("/api/features?limit=10").bodyAsText()).path("features")
        assertEquals(4, all.size())
        assertEquals("Also popular", all.path(0).path("text").asText())
        assertEquals(high, all.path(3).path("id").asText())
        assertTrue(all.path(3).path("resolved").asBoolean())

        assertEquals(HttpStatusCode.OK, admin.delete("/api/features/$low").status)
        val afterDelete = mapper.readTree(linh.get("/api/features?limit=10").bodyAsText()).path("features")
        assertEquals(3, afterDelete.size())
        assertFalse((0 until afterDelete.size()).any { afterDelete.path(it).path("id").asText() == low })
        assertEquals(HttpStatusCode.NotFound, admin.delete("/api/features/$low").status)
        assertEquals(HttpStatusCode.NotFound, linh.post("/api/features/$low/vote").status)
    }

    private suspend fun createFeature(client: io.ktor.client.HttpClient, text: String): String {
        val created = client.post("/api/features") {
            contentType(ContentType.Application.Json)
            setBody("""{"text":"$text"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        return mapper.readTree(created.bodyAsText()).path("id").asText()
    }

    private suspend fun vote(client: io.ktor.client.HttpClient, id: String) {
        assertEquals(HttpStatusCode.OK, client.post("/api/features/$id/vote").status)
    }

    private fun services(): TownServices {
        val clock = { 1_700_000_000_000L }
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-features", "").apply {
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
