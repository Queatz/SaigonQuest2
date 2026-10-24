package quest.saigon

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import quest.saigon.ai.CommunityPrompts
import quest.saigon.ai.MusicRender
import quest.saigon.auth.RateLimiter
import quest.saigon.media.ImageOps
import quest.saigon.media.UploadStore
import quest.saigon.town.AvatarService
import quest.saigon.town.CommunityNames
import quest.saigon.town.InMemoryTownRepository
import quest.saigon.town.MusicService
import quest.saigon.town.TownServices
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommunityBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun suggestionNamesAVeganCommunityWithoutCreatingIt() {
        assertEquals("vegans", CommunityNames.slug("Vegans"))
        assertEquals("nguoi-an-chay", CommunityNames.slug("Người ăn chay"))
        assertEquals("🌱", CommunityNames.cleanEmoji("🌱 Vegans"))
        val suggested = CommunityPrompts.fallback("Lunch with vegan friends downtown", emptyList())
        assertEquals("Vegans", suggested.name)
        assertEquals("🌱", suggested.emoji)
        val parsed = CommunityPrompts.parse("""{"emoji":"🌱","name":"Vegans"}""")
        assertEquals("Vegans", parsed?.name)
        assertEquals("🌱", CommunityPrompts.parseEmoji("""{"emoji":"🌱"}"""))
        assertEquals("🌱", CommunityPrompts.fallbackEmoji("Vegans", emptyList()))
        val vietnamese = CommunityPrompts.fallback("Hôm nay ăn chay với bạn ở quận 1", emptyList())
        assertEquals("Ăn chay", vietnamese.name)
        assertEquals("🌱", vietnamese.emoji)
        assertTrue(CommunityPrompts.SYSTEM.contains("same language as the post"))
    }

    @Test
    fun communitiesCanBeJoinedSearchedAndPostedExclusively() = testApplication {
        val services = services()
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val linh = createClient { install(HttpCookies) }
        val minh = createClient { install(HttpCookies) }
        linh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0904440001","password":"secretpass"}""")
        }
        minh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Minh","phone":"0904440002","password":"secretpass"}""")
        }
        val linhId = mapper.readTree(linh.get("/api/me").bodyAsText()).path("id").asText()

        val suggested = linh.post("/api/communities/suggest") {
            contentType(ContentType.Application.Json)
            setBody("""{"text":"A vegan lunch in District 1"}""")
        }
        assertEquals(HttpStatusCode.OK, suggested.status)
        val suggestion = mapper.readTree(suggested.bodyAsText())
        assertEquals("Vegans", suggestion.path("name").asText())
        assertEquals("🌱", suggestion.path("emoji").asText())
        assertTrue(suggestion.path("communityId").isNull || suggestion.path("communityId").asText().isBlank())

        val emoji = linh.post("/api/communities/emoji") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Vegans"}""")
        }
        assertEquals(HttpStatusCode.OK, emoji.status)
        assertEquals("🌱", mapper.readTree(emoji.bodyAsText()).path("emoji").asText())
        assertEquals(0, mapper.readTree(linh.get("/api/communities?q=veg").bodyAsText()).path("communities").size())

        val created = linh.post("/api/communities") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"${suggestion.path("name").asText()}","emoji":"${suggestion.path("emoji").asText()}"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val community = mapper.readTree(created.bodyAsText())
        val communityId = community.path("id").asText()
        assertEquals("Vegans", community.path("name").asText())
        assertTrue(community.path("joined").asBoolean())
        assertEquals(1, community.path("memberCount").asInt())

        val again = minh.post("/api/communities") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"vegans","emoji":"🌿"}""")
        }
        assertEquals(HttpStatusCode.OK, again.status)
        assertEquals(communityId, mapper.readTree(again.bodyAsText()).path("id").asText())
        assertEquals(2, mapper.readTree(again.bodyAsText()).path("memberCount").asInt())

        val found = mapper.readTree(linh.get("/api/communities?q=veg").bodyAsText()).path("communities")
        assertEquals(1, found.size())
        assertTrue(found.path(0).path("joined").asBoolean())

        val mine = mapper.readTree(linh.get("/api/me/communities").bodyAsText()).path("communities")
        assertEquals(communityId, mine.path(0).path("id").asText())
        val onProfile = mapper.readTree(minh.get("/api/users/$linhId/communities").bodyAsText()).path("communities")
        assertEquals("Vegans", onProfile.path(0).path("name").asText())
        assertTrue(onProfile.path(0).path("joined").asBoolean())

        val members = mapper.readTree(linh.get("/api/communities/$communityId/members").bodyAsText()).path("members")
        assertEquals(2, members.size())

        val left = linh.post("/api/communities/$communityId/leave")
        assertEquals(HttpStatusCode.OK, left.status)
        assertFalse(mapper.readTree(left.bodyAsText()).path("joined").asBoolean())
        assertEquals(1, mapper.readTree(left.bodyAsText()).path("memberCount").asInt())
        assertEquals(0, mapper.readTree(linh.get("/api/me/communities").bodyAsText()).path("communities").size())
        linh.post("/api/communities/$communityId/join")

        val shared = post(linh, "Shared vegan lunch", communityId, exclusive = false)
        assertEquals(HttpStatusCode.Created, shared.status)
        val sharedBody = mapper.readTree(shared.bodyAsText())
        assertEquals(communityId, sharedBody.path("communityId").asText())
        assertEquals("Vegans", sharedBody.path("communityName").asText())
        assertFalse(sharedBody.path("communityOnly").asBoolean())

        val hidden = post(linh, "Members only brunch", communityId, exclusive = true)
        assertEquals(HttpStatusCode.Created, hidden.status)
        val hiddenId = mapper.readTree(hidden.bodyAsText()).path("id").asText()
        assertTrue(mapper.readTree(hidden.bodyAsText()).path("communityOnly").asBoolean())

        val main = mapper.readTree(minh.get("/api/posts?limit=20").bodyAsText()).path("posts")
        assertEquals(1, main.size())
        assertEquals(sharedBody.path("id").asText(), main.path(0).path("id").asText())

        val communityFeed = mapper.readTree(minh.get("/api/communities/$communityId/posts?limit=20").bodyAsText()).path("posts")
        assertEquals(2, communityFeed.size())
        assertEquals(hiddenId, mapper.readTree(minh.get("/api/posts/$hiddenId").bodyAsText()).path("id").asText())

        val own = mapper.readTree(linh.get("/api/me/posts").bodyAsText()).path("posts")
        assertEquals(2, own.size())
        val exclusiveOwn = own.first { it.path("id").asText() == hiddenId }
        val sharedOwn = own.first { it.path("id").asText() == sharedBody.path("id").asText() }
        assertEquals(communityId, exclusiveOwn.path("communityId").asText())
        assertEquals("Vegans", exclusiveOwn.path("communityName").asText())
        assertTrue(exclusiveOwn.path("communityOnly").asBoolean())
        assertEquals(communityId, sharedOwn.path("communityId").asText())
        assertFalse(sharedOwn.path("communityOnly").asBoolean())

        val asAuthor = mapper.readTree(linh.get("/api/users/$linhId/posts").bodyAsText())
        assertEquals(2, asAuthor.path("count").asInt())
        assertEquals(2, asAuthor.path("posts").size())
        val asMember = mapper.readTree(minh.get("/api/users/$linhId/posts").bodyAsText())
        assertEquals(2, asMember.path("count").asInt())
        assertEquals(2, asMember.path("posts").size())
        val asStranger = mapper.readTree(createClient { }.get("/api/users/$linhId/posts").bodyAsText())
        assertEquals(1, asStranger.path("count").asInt())
        assertEquals(1, asStranger.path("posts").size())
        assertEquals(sharedBody.path("id").asText(), asStranger.path("posts").path(0).path("id").asText())
        assertEquals(HttpStatusCode.NotFound, createClient { }.get("/api/users/missing/posts").status)

        assertEquals(HttpStatusCode.BadRequest, post(linh, "Nowhere", null, exclusive = true).status)
        assertEquals(HttpStatusCode.BadRequest, post(linh, "Missing", "nope", exclusive = false).status)
        assertEquals(HttpStatusCode.NotFound, linh.get("/api/communities/missing").status)
    }

    @Test
    fun membersCanEditACommunityDescription() = testApplication {
        val services = services()
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val linh = createClient { install(HttpCookies) }
        val minh = createClient { install(HttpCookies) }
        val guest = createClient { install(HttpCookies) }
        linh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0904440011","password":"secretpass"}""")
        }
        minh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Minh","phone":"0904440012","password":"secretpass"}""")
        }
        guest.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Guest","phone":"0904440013","password":"secretpass"}""")
        }

        val created = linh.post("/api/communities") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Vegans","emoji":"🌱"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val community = mapper.readTree(created.bodyAsText())
        val communityId = community.path("id").asText()
        assertEquals("", community.path("description").asText())

        val note = "Lunch crew in D1 https://saigon.town/vegans"
        val saved = linh.patch("/api/communities/$communityId") {
            contentType(ContentType.Application.Json)
            setBody("""{"description":"$note"}""")
        }
        assertEquals(HttpStatusCode.OK, saved.status)
        assertEquals(note, mapper.readTree(saved.bodyAsText()).path("description").asText())
        assertEquals(note, mapper.readTree(linh.get("/api/communities/$communityId").bodyAsText()).path("description").asText())
        assertEquals(note, mapper.readTree(linh.get("/api/me/communities").bodyAsText()).path("communities").path(0).path("description").asText())
        assertEquals(note, mapper.readTree(linh.get("/api/communities?q=veg").bodyAsText()).path("communities").path(0).path("description").asText())

        assertEquals(
            HttpStatusCode.Forbidden,
            minh.patch("/api/communities/$communityId") {
                contentType(ContentType.Application.Json)
                setBody("""{"description":"nope"}""")
            }.status,
        )
        minh.post("/api/communities/$communityId/join")
        val asMember = minh.patch("/api/communities/$communityId") {
            contentType(ContentType.Application.Json)
            setBody("""{"description":"Members welcome"}""")
        }
        assertEquals(HttpStatusCode.OK, asMember.status)
        assertEquals("Members welcome", mapper.readTree(asMember.bodyAsText()).path("description").asText())

        assertEquals(
            HttpStatusCode.Forbidden,
            guest.patch("/api/communities/$communityId") {
                contentType(ContentType.Application.Json)
                setBody("""{"description":"nope"}""")
            }.status,
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            createClient { }.patch("/api/communities/$communityId") {
                contentType(ContentType.Application.Json)
                setBody("""{"description":"nope"}""")
            }.status,
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            linh.patch("/api/communities/$communityId") {
                contentType(ContentType.Application.Json)
                setBody("""{"description":"${"x".repeat(Limits.COMMUNITY_DESCRIPTION_MAX + 1)}"}""")
            }.status,
        )
        val cleared = linh.patch("/api/communities/$communityId") {
            contentType(ContentType.Application.Json)
            setBody("""{"description":"  "}""")
        }
        assertEquals(HttpStatusCode.OK, cleared.status)
        assertEquals("", mapper.readTree(cleared.bodyAsText()).path("description").asText())
        assertEquals(HttpStatusCode.NotFound, linh.patch("/api/communities/missing") {
            contentType(ContentType.Application.Json)
            setBody("""{"description":"nope"}""")
        }.status)
    }

    private suspend fun post(
        client: io.ktor.client.HttpClient,
        message: String,
        communityId: String?,
        exclusive: Boolean,
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
                    append("location", "D1")
                    if (!communityId.isNullOrBlank()) append("communityId", communityId)
                    if (exclusive) append("communityOnly", "true")
                },
            ),
        )
    }

    private fun services(): TownServices {
        val clock = { 1_700_000_000_000L }
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-community", "").apply {
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
