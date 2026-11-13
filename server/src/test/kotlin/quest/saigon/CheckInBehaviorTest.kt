package quest.saigon

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.get
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
import quest.saigon.town.PostRecord
import quest.saigon.town.TownServices
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CheckInBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun checkInsRequireMappedLocationsAndExpireAtQuantizedEndTime() = testApplication {
        var now = 1_700_000_000_000L
        val repo = InMemoryTownRepository()
        val services = services({ now }, repo)
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val client = createClient { install(HttpCookies) }
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905550301","password":"secretpass"}""")
        }

        val created = post(client, 30, includeCoordinates = true)
        assertEquals(HttpStatusCode.Created, created.status)
        val firstPostId = mapper.readTree(created.bodyAsText()).path("id").asText()
        val expectedUntil = CheckIns.endAt(now, 30)!!
        assertTrue(expectedUntil > now)

        now += 1
        val latestPost = post(client, 60, includeCoordinates = true, location = "District 1")
        val latestPostId = mapper.readTree(latestPost.bodyAsText()).path("id").asText()
        val latestUntil = CheckIns.endAt(now, 60)!!

        val privateClient = createClient { install(HttpCookies) }
        privateClient.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Minh","phone":"0905550302","password":"secretpass"}""")
        }
        val privateAuthorId = mapper.readTree(privateClient.get("/api/me").bodyAsText()).path("id").asText()
        repo.insertPost(
            PostRecord(
                id = "",
                userId = privateAuthorId,
                photoPath = "private.jpg",
                message = "Private check-in",
                location = "Private place",
                createdAt = now + 1,
                communityId = "private-community",
                communityOnly = true,
                lat = 10.78,
                lng = 106.7,
                checkInUntilAt = latestUntil,
            ),
        )

        val active = client.get("/api/checkins")
        assertEquals(HttpStatusCode.OK, active.status)
        val checkIns = mapper.readTree(active.bodyAsText()).path("checkIns")
        assertEquals(1, checkIns.size())
        assertEquals(latestPostId, checkIns[0].path("postId").asText())
        assertEquals(latestUntil, checkIns[0].path("untilAt").asLong())
        assertEquals("District 1", checkIns[0].path("location").asText())
        assertTrue(checkIns[0].path("authorName").asText().isNotBlank())
        assertTrue(firstPostId != latestPostId)

        val invalidDuration = post(client, 45, includeCoordinates = true)
        assertEquals(HttpStatusCode.BadRequest, invalidDuration.status)
        assertEquals("invalid_check_in", mapper.readTree(invalidDuration.bodyAsText()).path("error").asText())

        val missingCoordinates = post(client, 60, includeCoordinates = false)
        assertEquals(HttpStatusCode.BadRequest, missingCoordinates.status)
        assertEquals("invalid_check_in", mapper.readTree(missingCoordinates.bodyAsText()).path("error").asText())

        val regularPost = post(client, null, includeCoordinates = false)
        assertEquals(HttpStatusCode.Created, regularPost.status)

        now = latestUntil
        val expired = mapper.readTree(client.get("/api/checkins").bodyAsText()).path("checkIns")
        assertEquals(0, expired.size())
    }

    private suspend fun post(
        client: io.ktor.client.HttpClient,
        durationMinutes: Int?,
        includeCoordinates: Boolean,
        location: String = "Saigon",
    ) = client.post("/api/posts") {
        setBody(
            MultiPartFormDataContent(
                formData {
                    append(
                        "photo",
                        ImageOps().writeJpeg(solid(12, 16, Color(40, 120, 80)), 0.85f),
                        Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                        },
                    )
                    append("message", "Hello town")
                    append("location", location)
                    durationMinutes?.let { append("checkInMinutes", it.toString()) }
                    if (includeCoordinates) {
                        append("lat", "10.78")
                        append("lng", "106.7")
                    }
                },
            ),
        )
    }

    private fun services(clock: () -> Long, repo: InMemoryTownRepository): TownServices {
        val uploads = UploadStore(File.createTempFile("sq-checkins", "").apply {
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