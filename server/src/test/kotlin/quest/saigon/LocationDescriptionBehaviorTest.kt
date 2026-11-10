package quest.saigon

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.plugins.cookies.HttpCookies
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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationDescriptionBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun locationDescriptionsArePublicAndEditableByAnySignedInUser() = testApplication {
        val services = services()
        application { module(databaseHealth = { HealthStatus(true, "saigonquest", "3") }, services) }
        val writer = createClient { install(HttpCookies) }
        val otherUser = createClient { install(HttpCookies) }
        val guest = createClient { }

        assertEquals(HttpStatusCode.OK, guest.get(descriptionUrl("Park")).status)
        assertEquals("", description(guest, "Park"))
        assertEquals(HttpStatusCode.Unauthorized, patchDescription(guest, "Park", "Nope").status)

        register(writer, "Writer", "0905550401")
        register(otherUser, "Other", "0905550402")

        assertEquals(HttpStatusCode.OK, patchDescription(writer, "Cafe", "Google details", placeId = "google-cafe").status)
        assertEquals(HttpStatusCode.OK, patchDescription(writer, "Cafe", "Custom details", locationId = "custom-cafe").status)
        assertEquals(HttpStatusCode.OK, patchDescription(writer, "Cafe", "Named details").status)
        assertEquals("Google details", description(guest, "Cafe", placeId = "google-cafe"))
        assertEquals("Custom details", description(guest, "Cafe", locationId = "custom-cafe"))
        assertEquals("Named details", description(guest, "cafe"))

        val overwritten = patchDescription(otherUser, "Cafe", "Updated by anyone", placeId = "google-cafe")
        assertEquals(HttpStatusCode.OK, overwritten.status)
        assertEquals("Updated by anyone", mapper.readTree(overwritten.bodyAsText()).path("description").asText())
        assertEquals(HttpStatusCode.BadRequest, patchDescription(writer, "Cafe", "x".repeat(281), placeId = "google-cafe").status)
        assertEquals(HttpStatusCode.BadRequest, patchDescription(writer, "Cafe", "Invalid ID", placeId = "bad id").status)
        assertEquals(HttpStatusCode.BadRequest, patchDescription(writer, "Cafe", "Invalid ID", locationId = "x".repeat(129)).status)

        val cleared = patchDescription(writer, "Cafe", "  ", placeId = "google-cafe")
        assertEquals(HttpStatusCode.OK, cleared.status)
        assertEquals("", description(guest, "Cafe", placeId = "google-cafe"))

        val writerId = mapper.readTree(writer.get("/api/me").bodyAsText()).path("id").asText()
        services.repo.setUserPaused(writerId, true)
        assertEquals(HttpStatusCode.Forbidden, patchDescription(writer, "Park", "Paused").status)
    }

    private suspend fun description(
        client: io.ktor.client.HttpClient,
        name: String,
        placeId: String? = null,
        locationId: String? = null,
    ): String = mapper.readTree(client.get(descriptionUrl(name, placeId, locationId)).bodyAsText())
        .path("description").asText()

    private fun descriptionUrl(name: String, placeId: String? = null, locationId: String? = null): String = buildString {
        append("/api/places/description?name=")
        append(encode(name))
        placeId?.let { append("&placeId=").append(encode(it)) }
        locationId?.let { append("&locationId=").append(encode(it)) }
    }

    private suspend fun patchDescription(
        client: io.ktor.client.HttpClient,
        name: String,
        text: String,
        placeId: String? = null,
        locationId: String? = null,
    ) = client.patch("/api/places/description") {
        contentType(ContentType.Application.Json)
        val identity = buildString {
            append("\"name\":${mapper.writeValueAsString(name)}")
            placeId?.let { append(",\"placeId\":${mapper.writeValueAsString(it)}") }
            locationId?.let { append(",\"locationId\":${mapper.writeValueAsString(it)}") }
        }
        setBody("{$identity,\"description\":${mapper.writeValueAsString(text)}}")
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

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun services(): TownServices {
        val clock = { 1_700_000_000_000L }
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-location-descriptions", "").apply {
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