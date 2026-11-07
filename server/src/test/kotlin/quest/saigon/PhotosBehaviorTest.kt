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
import quest.saigon.auth.RateLimiter
import quest.saigon.media.ImageOps
import quest.saigon.media.UploadStore
import quest.saigon.town.AvatarService
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

class PhotosBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun activityPhotosArePublicAndOnlyOwnersOrAdminsCanDelete() = testApplication {
        var now = 1_700_000_000_000L
        val services = services { now++ }
        application { module(databaseHealth = { HealthStatus(true, "saigonquest", "3") }, services) }
        val admin = createClient { install(HttpCookies) }
        val owner = createClient { install(HttpCookies) }
        val other = createClient { install(HttpCookies) }
        register(admin, "Admin", "0905550301")
        assertEquals(HttpStatusCode.OK, admin.get("/api/admin/me").status)
        register(owner, "Owner", "0905550302")
        register(other, "Other", "0905550303")
        val activityResponse = owner.post("/api/activities") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Coffee walk","locationName":"The Workshop"}""")
        }
        assertEquals(HttpStatusCode.Created, activityResponse.status)
        val activityId = mapper.readTree(activityResponse.bodyAsText()).path("id").asText()
        val guest = createClient { }
        assertEquals(HttpStatusCode.OK, guest.get("/api/activities/$activityId/photos").status)

        val uploaded = upload(owner, "/api/activities/$activityId/photos", photoCount = 2)
        assertEquals(HttpStatusCode.Created, uploaded.status)
        val photos = mapper.readTree(guest.get("/api/activities/$activityId/photos").bodyAsText()).path("photos")
        assertEquals(2, photos.size())
        assertTrue(photos.all { it.path("url").asText().startsWith("/media/photos/") })
        assertEquals(HttpStatusCode.OK, guest.get(photos.path(0).path("url").asText()).status)
        assertFalse(photos.all { it.path("canDelete").asBoolean() })
        val ownerPhotos = mapper.readTree(owner.get("/api/activities/$activityId/photos").bodyAsText()).path("photos")
        assertTrue(ownerPhotos.all { it.path("canDelete").asBoolean() })
        val photoId = photos.path(0).path("id").asText()
        assertFalse(mapper.readTree(other.get("/api/activities/$activityId/photos").bodyAsText())
            .path("photos").path(0).path("canDelete").asBoolean())
        assertEquals(HttpStatusCode.Forbidden, other.delete("/api/activities/$activityId/photos/$photoId").status)
        assertEquals(HttpStatusCode.OK, admin.delete("/api/activities/$activityId/photos/$photoId").status)

        val remaining = mapper.readTree(owner.get("/api/activities/$activityId/photos").bodyAsText()).path("photos")
        val ownerPhotoId = remaining.path(0).path("id").asText()
        assertEquals(HttpStatusCode.OK, owner.delete("/api/activities/$activityId/photos/$ownerPhotoId").status)
        assertEquals(HttpStatusCode.Created, upload(owner, "/api/activities/$activityId/photos", photoCount = 1).status)
        assertEquals(HttpStatusCode.OK, owner.delete("/api/activities/$activityId").status)
        assertEquals(HttpStatusCode.NotFound, guest.get("/api/activities/$activityId/photos").status)
        assertTrue(File(services.uploads.root, "photos").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun locationPhotosUseLocationIdentityAndKeepOnlyTheLatestTwenty() = testApplication {
        val now = 1_700_000_000_000L
        val services = services { now }
        application { module(databaseHealth = { HealthStatus(true, "saigonquest", "3") }, services) }
        val owner = createClient { install(HttpCookies) }
        register(owner, "Owner", "0905550311")

        assertEquals(
            HttpStatusCode.Created,
            upload(owner, "/api/places/photos", photoCount = 1, fields = mapOf("name" to "Cafe", "placeId" to "google-cafe")).status,
        )
        assertEquals(
            HttpStatusCode.Created,
            upload(owner, "/api/places/photos", photoCount = 1, fields = mapOf("name" to "Cafe", "locationId" to "custom-cafe")).status,
        )
        assertEquals(
            HttpStatusCode.Created,
            upload(owner, "/api/places/photos", photoCount = 1, fields = mapOf("name" to "Park")).status,
        )
        val google = mapper.readTree(owner.get("/api/places/photos?name=Cafe&placeId=google-cafe").bodyAsText()).path("photos")
        val custom = mapper.readTree(owner.get("/api/places/photos?name=Cafe&locationId=custom-cafe").bodyAsText()).path("photos")
        val named = mapper.readTree(owner.get("/api/places/photos?name=Park").bodyAsText()).path("photos")
        assertEquals(1, google.size())
        assertEquals(1, custom.size())
        assertEquals(1, named.size())
        assertEquals(HttpStatusCode.OK, owner.delete("/api/places/photos/${google.path(0).path("id").asText()}").status)
        assertEquals(0, mapper.readTree(owner.get("/api/places/photos?name=Cafe&placeId=google-cafe").bodyAsText()).path("photos").size())

        repeat(20) {
            assertEquals(
                HttpStatusCode.Created,
                upload(owner, "/api/places/photos", photoCount = 1, fields = mapOf("name" to "Latest park", "locationId" to "latest-park")).status,
            )
        }
        assertEquals(
            HttpStatusCode.Created,
            upload(owner, "/api/places/photos", photoCount = 1, fields = mapOf("name" to "Latest park", "locationId" to "latest-park")).status,
        )
        val latest = mapper.readTree(owner.get("/api/places/photos?name=Latest%20park&locationId=latest-park").bodyAsText()).path("photos")
        assertEquals(20, latest.size())
        assertTrue(latest.path(0).path("createdAt").asLong() > latest.path(19).path("createdAt").asLong())
    }

    private suspend fun register(client: io.ktor.client.HttpClient, name: String, phone: String) {
        assertEquals(
            HttpStatusCode.OK,
            client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"$name","phone":"$phone","password":"secretpass"}""")
            }.status,
        )
    }

    private suspend fun upload(
        client: io.ktor.client.HttpClient,
        path: String,
        photoCount: Int,
        fields: Map<String, String> = emptyMap(),
    ) = client.post(path) {
        setBody(
            MultiPartFormDataContent(
                formData {
                    fields.forEach { (name, value) -> append(name, value) }
                    repeat(photoCount) { index ->
                        append(
                            "photos",
                            ImageOps().writeJpeg(solid(12, 16, Color(40, 120, 80)), 0.85f),
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"photo$index.jpg\"")
                            },
                        )
                    }
                },
            ),
        )
    }

    private fun services(clock: () -> Long): TownServices {
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-photos", "").apply {
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