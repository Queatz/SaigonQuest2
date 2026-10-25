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
import quest.saigon.town.CommunityRecord
import quest.saigon.town.InMemoryTownRepository
import quest.saigon.town.MusicService
import quest.saigon.town.PostRecord
import quest.saigon.town.TownServices
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostSearchBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun searchFindsPostContentAndPaginatesVisibleResults() = testApplication {
        val now = 1_700_000_000_000L
        val services = services { now }
        application {
            module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services)
        }
        val member = createClient { install(HttpCookies) }
        val guest = createClient { }
        val registered = member.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905550001","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.OK, registered.status)
        val memberId = mapper.readTree(registered.bodyAsText()).path("id").asText()
        val authorClient = createClient { install(HttpCookies) }
        val authorRegistered = authorClient.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Zadie Searchable","phone":"0905550002","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.OK, authorRegistered.status)
        val authorId = mapper.readTree(authorRegistered.bodyAsText()).path("id").asText()
        services.repo.insertCommunity(CommunityRecord("gardeners", "Saigon Gardeners", "🌱", "gardeners", memberId, now))
        services.repo.joinCommunity("gardeners", memberId, now)

        fun post(
            id: String,
            createdAt: Long,
            message: String = "",
            location: String = "",
            communityId: String? = null,
            communityOnly: Boolean = false,
            eventPrice: Long? = null,
            pollOptions: List<String> = emptyList(),
            hasQuestion: Boolean = false,
            questionTitle: String = "",
            ctaTitle: String = "",
            ctaText: String = "",
            ctaDestination: String = "",
            ctaUrl: String = "",
            authorId: String = memberId,
        ) = services.repo.insertPost(
            PostRecord(
                id = id,
                userId = authorId,
                photoPath = "photo.jpg",
                message = message,
                location = location,
                createdAt = createdAt,
                communityId = communityId,
                communityOnly = communityOnly,
                eventPrice = eventPrice,
                pollOptions = pollOptions,
                hasQuestion = hasQuestion,
                questionTitle = questionTitle,
                ctaTitle = ctaTitle,
                ctaText = ctaText,
                ctaDestination = ctaDestination,
                ctaUrl = ctaUrl,
            ),
        )
        post("author-name-post", now - 1, authorId = authorId)

        post("message-post", now + 1, message = "Riverwalk at dawn")
        post("location-post", now + 2, message = "Morning coffee", location = "Cafe Lantern")
        post("community-post", now + 3, message = "Plant swap", communityId = "gardeners")
        post("event-post", now + 4, message = "Live music", location = "Riverside", eventPrice = 76500)
        post("poll-post", now + 5, message = "Choose a drink", pollOptions = listOf("Matcha latte", "Iced tea"))
        post("question-post", now + 6, message = "Anyone free?", hasQuestion = true, questionTitle = "Badminton partners")
        post(
            "cta-post",
            now + 7,
            message = "Dinner plans",
            ctaTitle = "Book a Table",
            ctaText = "Reserve a seat",
            ctaDestination = "website",
            ctaUrl = "https://cafe.example/menu",
        )
        post("private-post", now + 8, message = "Hidden garden", communityId = "gardeners", communityOnly = true)

        suspend fun ids(client: io.ktor.client.HttpClient, query: String = "", cursor: String? = null): List<String> {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
            val more = cursor?.let { "&cursor=${URLEncoder.encode(it, StandardCharsets.UTF_8)}" }.orEmpty()
            val response = client.get("/api/posts/search?q=$encoded&limit=2$more")
            assertEquals(HttpStatusCode.OK, response.status)
            val rows = mapper.readTree(response.bodyAsText()).path("posts")
            return (0 until rows.size()).map { rows.path(it).path("id").asText() }
        }

        val firstPage = member.get("/api/posts/search?limit=2")
        assertEquals(HttpStatusCode.OK, firstPage.status)
        val firstBody = mapper.readTree(firstPage.bodyAsText())
        assertEquals(listOf("private-post", "cta-post"), (0 until 2).map { firstBody.path("posts").path(it).path("id").asText() })
        val nextCursor = firstBody.path("nextCursor").asText()
        assertTrue(nextCursor.isNotBlank())
        val nextPage = ids(member, cursor = nextCursor)
        assertEquals(listOf("question-post", "poll-post"), nextPage)

        assertEquals(listOf("message-post"), ids(guest, "RIVERWALK"))
        assertEquals(listOf("location-post"), ids(guest, "cafe lantern"))
        assertEquals(listOf("community-post"), ids(guest, "saigon gardeners"))
        assertEquals(listOf("event-post"), ids(guest, "76500"))
        assertEquals(listOf("poll-post"), ids(guest, "matcha latte"))
        assertEquals(listOf("question-post"), ids(guest, "badminton partners"))
        assertEquals(listOf("cta-post"), ids(guest, "reserve a seat"))
        assertEquals(listOf("cta-post"), ids(guest, "book a table"))
        assertEquals(listOf("cta-post"), ids(guest, "website"))
        assertEquals(listOf("cta-post"), ids(guest, "cafe.example/menu"))
        assertEquals(listOf("author-name-post"), ids(guest, "zAdIe sEaRcHaBlE"))
        assertFalse(ids(guest, "hidden garden").contains("private-post"))
        assertEquals(listOf("private-post"), ids(member, "hidden garden"))
    }

    private fun services(clock: () -> Long): TownServices {
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-search", "").apply {
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
            config = AppConfig(uploadDir = uploads.root, googleMapsApiKey = "test-maps"),
            clock = clock,
            contactLimiter = RateLimiter(1, 60_000, clock),
        )
    }
}
