package quest.saigon

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.delete
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
        assertEquals("Hello town", checkIns[0].path("message").asText())
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

    @Test
    fun ownersCanExtendAndCheckOutButOthersAndExpiredCheckInsCannotChangeThem() = testApplication {
        var now = 1_700_000_000_000L
        val repo = InMemoryTownRepository()
        val services = services({ now }, repo)
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val owner = createClient { install(HttpCookies) }
        owner.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905550303","password":"secretpass"}""")
        }
        val created = post(owner, 30, includeCoordinates = true)
        val postId = mapper.readTree(created.bodyAsText()).path("id").asText()
        val extendedUntil = CheckIns.endAt(now, 90)!!

        val extended = owner.patch("/api/posts/$postId/checkin") {
            contentType(ContentType.Application.Json)
            setBody("""{"checkInMinutes":90}""")
        }
        assertEquals(HttpStatusCode.OK, extended.status)
        assertEquals(extendedUntil, mapper.readTree(extended.bodyAsText()).path("untilAt").asLong())

        val outsider = createClient { install(HttpCookies) }
        outsider.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Minh","phone":"0905550304","password":"secretpass"}""")
        }
        assertEquals(
            HttpStatusCode.NotFound,
            outsider.patch("/api/posts/$postId/checkin") {
                contentType(ContentType.Application.Json)
                setBody("""{"checkInMinutes":60}""")
            }.status,
        )
        assertEquals(HttpStatusCode.NotFound, outsider.delete("/api/posts/$postId/checkin").status)
        assertEquals(
            HttpStatusCode.BadRequest,
            owner.patch("/api/posts/$postId/checkin") {
                contentType(ContentType.Application.Json)
                setBody("""{"checkInMinutes":45}""")
            }.status,
        )

        val ownerId = mapper.readTree(owner.get("/api/me").bodyAsText()).path("id").asText()
        val unmapped = repo.insertPost(
            PostRecord(
                id = "",
                userId = ownerId,
                photoPath = "unmapped.jpg",
                message = "Unmapped check-in",
                location = "Unmapped place",
                createdAt = now,
                checkInUntilAt = CheckIns.endAt(now, 30),
            ),
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            owner.patch("/api/posts/${unmapped.id}/checkin") {
                contentType(ContentType.Application.Json)
                setBody("""{"checkInMinutes":60}""")
            }.status,
        )
        assertEquals(HttpStatusCode.BadRequest, owner.delete("/api/posts/${unmapped.id}/checkin").status)

        assertEquals(HttpStatusCode.OK, owner.delete("/api/posts/$postId/checkin").status)
        assertEquals(0, mapper.readTree(owner.get("/api/checkins").bodyAsText()).path("checkIns").size())

        val expiring = post(owner, 30, includeCoordinates = true)
        val expiringPostId = mapper.readTree(expiring.bodyAsText()).path("id").asText()
        now = CheckIns.endAt(now, 30)!!
        assertEquals(
            HttpStatusCode.BadRequest,
            owner.patch("/api/posts/$expiringPostId/checkin") {
                contentType(ContentType.Application.Json)
                setBody("""{"checkInMinutes":60}""")
            }.status,
        )
        assertEquals(HttpStatusCode.BadRequest, owner.delete("/api/posts/$expiringPostId/checkin").status)
    }

    @Test
    fun checkInsIncludePlaceCountsActivitiesAndPhoto() = testApplication {
        var now = 1_700_000_000_000L
        val repo = InMemoryTownRepository()
        val services = services({ now }, repo)
        application { module(databaseHealth = { HealthStatus(true, "saigonquest", "3") }, services) }
        val client = createClient { install(HttpCookies) }
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905550305","password":"secretpass"}""")
        }
        val linhId = mapper.readTree(client.get("/api/me").bodyAsText()).path("id").asText()
        val location = repo.insertUserLocation("Climbing Gym", 10.77, 106.70, linhId, now)

        assertEquals(
            HttpStatusCode.Created,
            client.post("/api/activities") {
                contentType(ContentType.Application.Json)
                setBody("""{"emoji":"🧗","name":"Climb","locationName":"Climbing Gym","locationId":"${location.id}","interested":true}""")
            }.status,
        )
        now += 1
        assertEquals(
            HttpStatusCode.Created,
            client.post("/api/activities") {
                contentType(ContentType.Application.Json)
                setBody("""{"emoji":"☕","name":"Coffee","locationName":"Climbing Gym","locationId":"${location.id}","interested":false}""")
            }.status,
        )
        now += 1

        val checkInPost = client.post("/api/posts") {
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
                        append("message", "Climbing now")
                        append("location", "Climbing Gym")
                        append("locationId", location.id)
                        append("checkInMinutes", "60")
                        append("lat", "10.77")
                        append("lng", "106.70")
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, checkInPost.status)

        val checkIns = mapper.readTree(client.get("/api/checkins").bodyAsText()).path("checkIns")
        assertEquals(1, checkIns.size())
        val item = checkIns[0]
        assertEquals(1, item.path("postCount").asInt())
        assertEquals(2, item.path("activityCount").asInt())
        assertTrue(item.path("hasActivities").asBoolean())
        assertEquals(
            listOf("🧗", "☕"),
            (0 until item.path("activityEmojis").size()).map { item.path("activityEmojis").path(it).asText() },
        )
        assertTrue(item.path("photoUrl").asText().startsWith("/media/"))
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