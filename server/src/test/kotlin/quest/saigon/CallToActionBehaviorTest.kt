package quest.saigon

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.plugins.cookies.HttpCookies
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
import kotlin.test.assertTrue

class CallToActionBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun callToActionCanBePostedForEachDestination() = testApplication {
        val now = 1_700_000_000_000L
        val services = services({ now })
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val linh = createClient { install(HttpCookies) }
        linh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905550101","password":"secretpass"}""")
        }

        val message = postCta(linh, "Come say hi", "Message me", "message", "")
        assertEquals(HttpStatusCode.Created, message.status)
        val messageBody = mapper.readTree(message.bodyAsText())
        assertEquals("Come say hi", messageBody.path("ctaTitle").asText())
        assertEquals("Message me", messageBody.path("ctaText").asText())
        assertEquals(CallToAction.MESSAGE, messageBody.path("ctaDestination").asText())
        assertTrue(messageBody.path("ctaUrl").isMissingNode || messageBody.path("ctaUrl").isNull)
        assertTrue(messageBody.path("authorZaloUrl").isMissingNode || messageBody.path("authorZaloUrl").isNull)

        val untitled = postCta(linh, "", "Book", "message", "")
        assertEquals(HttpStatusCode.Created, untitled.status)
        val untitledBody = mapper.readTree(untitled.bodyAsText())
        assertEquals("", untitledBody.path("ctaTitle").asText())
        assertEquals("Book", untitledBody.path("ctaText").asText())

        val website = postCta(linh, "Menu", "Open", "website", "saigon.town/menu")
        assertEquals(HttpStatusCode.Created, website.status)
        val websiteBody = mapper.readTree(website.bodyAsText())
        assertEquals("https://saigon.town/menu", websiteBody.path("ctaUrl").asText())
        assertEquals(CallToAction.WEBSITE, websiteBody.path("ctaDestination").asText())

        val zalo = postCta(linh, "", "Zalo me", "zalo", "")
        assertEquals(HttpStatusCode.Created, zalo.status)
        val zaloBody = mapper.readTree(zalo.bodyAsText())
        assertEquals(CallToAction.ZALO, zaloBody.path("ctaDestination").asText())
        assertEquals("https://zalo.me/84905550101", zaloBody.path("authorZaloUrl").asText())

        val fetched = mapper.readTree(linh.get("/api/posts/${zaloBody.path("id").asText()}").bodyAsText())
        assertEquals("Zalo me", fetched.path("ctaText").asText())
        assertEquals("https://zalo.me/84905550101", fetched.path("authorZaloUrl").asText())

        val profile = postCta(linh, "", "Show your profile", "profile", "")
        assertEquals(HttpStatusCode.Created, profile.status)
        val profileBody = mapper.readTree(profile.bodyAsText())
        assertEquals(CallToAction.PROFILE, profileBody.path("ctaDestination").asText())
        assertTrue(profileBody.path("ctaUrl").isMissingNode || profileBody.path("ctaUrl").isNull)
        val fetchedProfile = mapper.readTree(linh.get("/api/posts/${profileBody.path("id").asText()}").bodyAsText())
        assertEquals(CallToAction.PROFILE, fetchedProfile.path("ctaDestination").asText())
        assertEquals("Show your profile", fetchedProfile.path("ctaText").asText())
    }

    @Test
    fun callToActionRejectsInvalidPayloads() = testApplication {
        val now = 1_700_000_000_000L
        val services = services({ now })
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val linh = createClient { install(HttpCookies) }
        linh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905550101","password":"secretpass"}""")
        }

        val missingText = postCta(linh, "Title", "", "message", "")
        assertEquals(HttpStatusCode.BadRequest, missingText.status)
        assertEquals("invalid_cta", mapper.readTree(missingText.bodyAsText()).path("error").asText())

        val missingUrl = postCta(linh, "", "Open", "website", "")
        assertEquals(HttpStatusCode.BadRequest, missingUrl.status)
        assertEquals("invalid_cta_url", mapper.readTree(missingUrl.bodyAsText()).path("error").asText())

        val badUrl = postCta(linh, "", "Open", "website", "javascript:alert(1)")
        assertEquals(HttpStatusCode.BadRequest, badUrl.status)
        assertEquals("invalid_cta_url", mapper.readTree(badUrl.bodyAsText()).path("error").asText())

        val badDest = postCta(linh, "", "Open", "email", "")
        assertEquals(HttpStatusCode.BadRequest, badDest.status)
        assertEquals("invalid_cta", mapper.readTree(badDest.bodyAsText()).path("error").asText())
    }

    private suspend fun postCta(
        client: io.ktor.client.HttpClient,
        title: String,
        text: String,
        destination: String,
        url: String,
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
                    append("message", "Hello town")
                    append("location", "Saigon")
                    append("addCta", "true")
                    if (title.isNotEmpty()) append("ctaTitle", title)
                    append("ctaText", text)
                    append("ctaDestination", destination)
                    if (url.isNotEmpty()) append("ctaUrl", url)
                },
            ),
        )
    }

    private fun services(clock: () -> Long): TownServices {
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-cta", "").apply {
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
