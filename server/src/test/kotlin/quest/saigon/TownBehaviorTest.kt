package quest.saigon

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import quest.saigon.ai.AvatarPrompts
import quest.saigon.ai.QwenClient
import quest.saigon.ai.QwenResponses
import quest.saigon.auth.PasswordHasher
import quest.saigon.auth.RateLimiter
import quest.saigon.media.ImageOps
import quest.saigon.media.UploadStore
import quest.saigon.town.AvatarService
import quest.saigon.town.AvatarStatus
import quest.saigon.town.InMemoryTownRepository
import quest.saigon.town.PostRecord
import quest.saigon.town.TownServices
import quest.saigon.town.avatarFailureReason
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TownBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun passwordRoundTrip() {
        val stored = PasswordHasher.hash("secretpass")
        assertTrue(PasswordHasher.verify("secretpass", stored))
        assertFalse(PasswordHasher.verify("secretpass!", stored))
        assertFalse(PasswordHasher.verify("secretpass", "nope"))
    }

    @Test
    fun maskBecomesAlphaAndGrayFallsBack() {
        val images = ImageOps()
        val color = BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB)
        val graphics = color.createGraphics()
        graphics.color = Color(128, 128, 128)
        graphics.fillRect(0, 0, 12, 12)
        graphics.color = Color(220, 40, 40)
        graphics.fillRect(4, 4, 4, 4)
        graphics.dispose()
        val mask = BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB)
        val maskGraphics = mask.createGraphics()
        maskGraphics.color = Color.BLACK
        maskGraphics.fillRect(0, 0, 12, 12)
        maskGraphics.color = Color.WHITE
        maskGraphics.fillRect(4, 4, 4, 4)
        maskGraphics.dispose()

        val cutout = images.mergeMaskAsAlpha(color, images.orientMask(mask))
        assertEquals(0, cutout.getRGB(0, 0) ushr 24)
        assertEquals(255, cutout.getRGB(6, 6) ushr 24)

        val keyed = images.chromaKeyGray(color)
        assertEquals(0, keyed.getRGB(0, 0) ushr 24)
        assertEquals(255, keyed.getRGB(6, 6) ushr 24)
        assertTrue(images.maskIsUsable(mask))
    }

    @Test
    fun maskHighlightsStayOpaqueAndBinary() {
        val images = ImageOps()
        val color = solid(30, 30, Color(128, 128, 128))
        val graphics = color.createGraphics()
        graphics.color = Color(255, 240, 220)
        graphics.fillRect(8, 8, 14, 14)
        graphics.color = Color(140, 132, 130)
        graphics.fillRect(12, 12, 3, 3)
        graphics.dispose()
        val mask = solid(30, 30, Color.BLACK)
        val maskGraphics = mask.createGraphics()
        maskGraphics.color = Color.WHITE
        maskGraphics.fillRect(8, 8, 14, 14)
        maskGraphics.color = Color(180, 180, 180)
        maskGraphics.fillRect(12, 12, 2, 2)
        maskGraphics.color = Color.BLACK
        maskGraphics.fillRect(16, 16, 2, 2)
        maskGraphics.dispose()
        mask.setRGB(2, 2, Color(40, 40, 40).rgb)

        val cutout = images.mergeMaskAsAlpha(color, mask)
        assertEquals(0, cutout.getRGB(1, 1) ushr 24)
        assertEquals(0, cutout.getRGB(2, 2) ushr 24)
        assertEquals(255, cutout.getRGB(12, 12) ushr 24)
        assertEquals(255, cutout.getRGB(16, 16) ushr 24)
        assertEquals(255, (cutout.getRGB(10, 10) shr 16) and 0xFF)
        assertBinaryAlpha(cutout)

        val keyed = images.chromaKeyGray(color)
        assertEquals(0, keyed.getRGB(1, 1) ushr 24)
        assertEquals(255, keyed.getRGB(9, 9) ushr 24)
        assertEquals(255, keyed.getRGB(13, 13) ushr 24)
        assertBinaryAlpha(keyed)
    }

    @Test
    fun softMaskDoesNotPunchCharacterHighlights() {
        val images = ImageOps()
        val color = solid(24, 24, Color(128, 128, 128))
        val graphics = color.createGraphics()
        graphics.color = Color(255, 250, 240)
        graphics.fillRect(6, 6, 12, 12)
        graphics.dispose()
        val mask = solid(24, 24, Color.BLACK)
        val maskGraphics = mask.createGraphics()
        maskGraphics.color = Color(90, 90, 90)
        maskGraphics.fillRect(6, 6, 12, 12)
        maskGraphics.color = Color(200, 200, 200)
        maskGraphics.fillRect(8, 8, 2, 2)
        maskGraphics.color = Color.BLACK
        maskGraphics.fillRect(11, 11, 3, 3)
        maskGraphics.dispose()

        val cutout = images.mergeMaskAsAlpha(color, mask)
        assertEquals(0, cutout.getRGB(1, 1) ushr 24)
        assertEquals(255, cutout.getRGB(7, 7) ushr 24)
        assertEquals(255, cutout.getRGB(8, 8) ushr 24)
        assertEquals(255, cutout.getRGB(12, 12) ushr 24)
        assertEquals(255, (cutout.getRGB(8, 8) shr 16) and 0xFF)
        assertBinaryAlpha(cutout)
    }

    @Test
    fun qwenResponsesFindImageAndMaskUrls() {
        val node = QwenResponses.parse(
            """
            {
              "output": {
                "task_id": "task-1",
                "task_status": "SUCCEEDED",
                "output_image_url": "https://example.test/mask.png",
                "output_vis_image_url": "https://example.test/vis.png",
                "choices": [{"message": {"content": [{"image": "https://example.test/avatar.png"}]}}]
              }
            }
            """.trimIndent(),
        )
        assertEquals("task-1", QwenResponses.taskId(node))
        assertEquals("SUCCEEDED", QwenResponses.taskStatus(node))
        assertEquals("https://example.test/mask.png", QwenResponses.maskUrl(node))
        assertTrue(QwenResponses.imageUrls(node).contains("https://example.test/avatar.png"))
        assertEquals(
            "smiling",
            QwenResponses.chatText(QwenResponses.parse("""{"choices":[{"message":{"content":"smiling"}}]}""")),
        )
        val instruction = AvatarPrompts.editInstruction("smiling", "pleased")
        assertTrue(instruction.contains("#808080"))
        assertTrue(instruction.contains("pleased"))
        assertTrue(instruction.contains("no white border"))
        assertTrue(instruction.contains("no sticker edge"))
        assertTrue(instruction.contains("realistic"))
        assertTrue(instruction.contains("expressive"))
        assertFalse(instruction.contains("visual-novel character"))
        assertEquals(360L, QwenClient.GENERATION_TIMEOUT_SECONDS)
        assertEquals(360_000L, QwenClient.GENERATION_POLL_MILLIS)
        assertEquals(
            "https://ws.ap-southeast-1.maas.aliyuncs.com",
            QwenClient.apiRoot("https://dashscope-intl.aliyuncs.com", "ws"),
        )
        assertEquals(
            "https://dashscope.aliyuncs.com",
            QwenClient.apiRoot("https://dashscope.aliyuncs.com/compatible-mode/v1", null),
        )
    }

    @Test
    fun loggedOutVisitorsCanReplyButNotPostAndFeedHidesTheNumber() = testApplication {
        val services = services()
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val http = createClient { install(HttpCookies) }

        val denied = http.post("/api/posts")
        assertEquals(HttpStatusCode.Unauthorized, denied.status)

        val created = http.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0901234567","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.OK, created.status)
        val me = mapper.readTree(created.bodyAsText())
        assertEquals("Linh", me.path("name").asText())
        assertFalse(created.bodyAsText().contains("passwordHash"))

        http.patch("/api/me") {
            contentType(ContentType.Application.Json)
            setBody("""{"location":"Cát Lãi","language":"vi"}""")
        }
        val userId = me.path("id").asText()
        val avatar = services.repo.insertAvatar(userId, "smiling", 10)
        services.repo.updateAvatar(avatar.id, AvatarStatus.READY, "avatars/ready.png", null)
        services.uploads.writeAvatar(byteArrayOf(1))
        val ready = services.repo.findAvatar(avatar.id)!!
        services.repo.updateAvatar(ready.id, AvatarStatus.READY, ready.imagePath, null)

        val jpeg = ImageOps().writeJpeg(solid(8, 16, Color(40, 90, 160)), 0.85f)
        val posted = http.post("/api/posts") {
            setBody(
                io.ktor.client.request.forms.MultiPartFormDataContent(
                    io.ktor.client.request.forms.formData {
                        append("message", "Would you like to join me for a run?")
                        append("avatarId", avatar.id)
                        append(
                            "photo",
                            jpeg,
                            io.ktor.http.Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                            },
                        )
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, posted.status)
        val post = mapper.readTree(posted.bodyAsText())
        assertEquals("Cát Lãi", post.path("location").asText())
        assertFalse(posted.bodyAsText().contains("84901234567"))

        val overridden = http.post("/api/posts") {
            setBody(
                io.ktor.client.request.forms.MultiPartFormDataContent(
                    io.ktor.client.request.forms.formData {
                        append("message", "Meet me by the river.")
                        append("location", "Thảo Điền")
                        append("avatarId", avatar.id)
                        append(
                            "photo",
                            jpeg,
                            io.ktor.http.Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                            },
                        )
                    },
                ),
            )
        }
        assertEquals("Thảo Điền", mapper.readTree(overridden.bodyAsText()).path("location").asText())

        val feed = http.get("/api/posts")
        val feedBody = feed.bodyAsText()
        assertFalse(feedBody.contains("84901234567"))
        assertFalse(feedBody.contains("phone"))
        assertTrue(feedBody.contains("Thảo Điền"))

        val anonymous = createClient { }
        val contact = anonymous.post("/api/posts/${post.path("id").asText()}/contact")
        assertEquals(HttpStatusCode.OK, contact.status)
        assertEquals("https://zalo.me/84901234567", mapper.readTree(contact.bodyAsText()).path("zaloUrl").asText())
        assertFalse(contact.bodyAsText().contains("\"phone\""))

        val again = anonymous.post("/api/posts/${post.path("id").asText()}/contact")
        assertEquals(HttpStatusCode.TooManyRequests, again.status)

        val duplicate = http.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Other","phone":"+84 90 123 4567","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)

        http.post("/api/auth/logout")
        val meAfter = http.get("/api/me")
        assertEquals(HttpStatusCode.Unauthorized, meAfter.status)
        val badLogin = http.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"phone":"0901234567","password":"wrongpass"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, badLogin.status)
    }

    @Test
    fun failedAvatarKeepsTheReasonAndCanBeRetriedOrDeleted() {
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-avatar", "").apply {
            delete()
            mkdirs()
        })
        val photo = uploads.writeProfile(byteArrayOf(1, 2, 3))
        val user = repo.createUser("0901000001", "hash", "Linh", 1L).copy(profilePhotoPath = photo)
        repo.updateUser(user)
        var calls = 0
        val service = AvatarService(
            repo = repo,
            uploads = uploads,
            renderer = { _, _ ->
                calls += 1
                if (calls == 1) throw IllegalStateException("model rejected the photo sk-secretkey")
                byteArrayOf(9, 9)
            },
            configured = true,
            executor = { it.run() },
        )
        val created = service.enqueue(user, "smiling")
        val failed = repo.findAvatar(created.id)!!
        assertEquals(AvatarStatus.FAILED, failed.status)
        assertTrue(failed.error!!.contains("model rejected"))
        assertFalse(failed.error!!.contains("sk-secretkey"))
        service.retry(user, created.id)
        val ready = repo.findAvatar(created.id)!!
        assertEquals(AvatarStatus.READY, ready.status)
        assertTrue(uploads.resolve(ready.imagePath!!)!!.isFile)
        repo.insertPost(PostRecord("", user.id, created.id, "posts/x.jpg", "hi", "Cát Lái", 2L))
        service.delete(user, created.id)
        assertEquals(AvatarStatus.REMOVED, repo.findAvatar(created.id)!!.status)
        assertTrue(repo.avatarsFor(user.id).isEmpty())
        assertTrue(uploads.resolve(ready.imagePath!!)!!.isFile)
        assertEquals("request timed out", avatarFailureReason(RuntimeException("  request   timed out  ")))
        val wide = BufferedImage(1400, 1800, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until wide.height step 4) {
            for (x in 0 until wide.width step 4) {
                wide.setRGB(x, y, (x * 13 + y * 29) or 0xFF000000.toInt())
            }
        }
        val ops = ImageOps()
        val large = ops.writeJpeg(wide, 0.95f)
        assertTrue(ops.shrinkForUpload(large).size <= 45_000)
    }

    @Test
    fun avatarTrimDropsTransparentPadding() {
        val images = ImageOps()
        val image = BufferedImage(20, 30, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.composite = java.awt.AlphaComposite.Clear
        graphics.fillRect(0, 0, 20, 30)
        graphics.composite = java.awt.AlphaComposite.SrcOver
        graphics.color = Color(220, 40, 40, 255)
        graphics.fillRect(5, 8, 6, 10)
        graphics.dispose()
        val trimmed = images.trimToContent(image)
        assertEquals(8, trimmed.width)
        assertEquals(12, trimmed.height)
        assertEquals(255, trimmed.getRGB(3, 4) ushr 24)
        assertTrue(trimmed.width < image.width && trimmed.height < image.height)
    }

    @Test
    fun ownerCanSeePostStatsAndDeleteThem() = testApplication {
        val services = services()
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val http = createClient { install(HttpCookies) }
        val created = http.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0903334455","password":"secretpass"}""")
        }
        val userId = mapper.readTree(created.bodyAsText()).path("id").asText()
        val avatar = services.repo.insertAvatar(userId, "smiling", 10)
        services.repo.updateAvatar(avatar.id, AvatarStatus.READY, "avatars/ready.png", null)
        val jpeg = ImageOps().writeJpeg(solid(8, 8, Color(40, 90, 160)), 0.85f)
        val firstPath = services.uploads.writePost(jpeg)
        val secondPath = services.uploads.writePost(jpeg)
        val first = services.repo.insertPost(PostRecord("", userId, avatar.id, firstPath, "Hello town", "Cát Lái", 5L))
        services.repo.insertPost(PostRecord("", userId, avatar.id, secondPath, "Second story", "Thảo Điền", 4L))

        val ownView = http.post("/api/posts/${first.id}/view")
        assertEquals(HttpStatusCode.OK, ownView.status)
        assertEquals(0, services.repo.findPost(first.id)!!.views)

        val seen = createClient { }.post("/api/posts/${first.id}/view")
        assertEquals(HttpStatusCode.OK, seen.status)
        assertEquals(1, services.repo.findPost(first.id)!!.views)
        createClient { }.post("/api/posts/${first.id}/view")
        assertEquals(1, services.repo.findPost(first.id)!!.views)

        val contact = createClient { }.post("/api/posts/${first.id}/contact")
        assertEquals(HttpStatusCode.OK, contact.status)
        assertEquals(1, services.repo.findPost(first.id)!!.replyClicks)

        val anonymous = createClient { }
        assertEquals(HttpStatusCode.Unauthorized, anonymous.get("/api/me/posts").status)
        assertEquals(HttpStatusCode.NotFound, anonymous.post("/api/posts/missing/view").status)

        val page = http.get("/api/me/posts?limit=1")
        val listed = mapper.readTree(page.bodyAsText())
        assertEquals(1, listed.path("posts").size())
        assertEquals("Hello town", listed.path("posts").path(0).path("message").asText())
        assertEquals(1, listed.path("posts").path(0).path("views").asInt())
        assertEquals(1, listed.path("posts").path(0).path("replyClicks").asInt())
        assertTrue(listed.path("nextCursor").asText().isNotBlank())
        assertFalse(page.bodyAsText().contains("phone"))

        val stranger = createClient { install(HttpCookies) }
        stranger.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Mai","phone":"0904445566","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.NotFound, stranger.delete("/api/posts/${first.id}").status)
        assertEquals(0, mapper.readTree(stranger.get("/api/me/posts").bodyAsText()).path("posts").size())

        val removed = http.delete("/api/posts/${first.id}")
        assertEquals(HttpStatusCode.OK, removed.status)
        assertEquals(null, services.repo.findPost(first.id))
        assertEquals(null, services.uploads.resolve(firstPath))
        assertEquals(HttpStatusCode.NotFound, http.delete("/api/posts/${first.id}").status)
        val feed = http.get("/api/posts").bodyAsText()
        assertFalse(feed.contains("Hello town"))
        assertTrue(feed.contains("Second story"))
    }

    @Test
    fun avatarWithoutAKeyFailsFast() = testApplication {
        val services = services()
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val http = createClient { install(HttpCookies) }
        http.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0901112233","password":"secretpass"}""")
        }
        val missingPhoto = http.post("/api/avatars") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"smiling"}""")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, missingPhoto.status)
        assertTrue(missingPhoto.bodyAsText().contains("qwen_not_configured"))
    }

    private fun services(): TownServices {
        val clock = { 1_700_000_000_000L }
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-upload", "").apply {
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
            config = AppConfig(uploadDir = uploads.root, qwenApiKey = null),
            clock = clock,
            contactLimiter = RateLimiter(1, 60_000, clock),
        )
    }

    private fun assertBinaryAlpha(image: BufferedImage) {
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val alpha = image.getRGB(x, y) ushr 24
                assertTrue(alpha == 0 || alpha == 255, "partial alpha $alpha at $x,$y")
            }
        }
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
