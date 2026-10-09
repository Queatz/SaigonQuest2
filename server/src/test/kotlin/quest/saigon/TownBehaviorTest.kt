package quest.saigon

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
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
import quest.saigon.ai.MusicRender
import quest.saigon.media.UploadStore
import quest.saigon.town.AvatarService
import quest.saigon.town.MusicService
import quest.saigon.town.AvatarStatus
import quest.saigon.town.InMemoryTownRepository
import quest.saigon.town.PostRecord
import quest.saigon.town.SaigonDays
import quest.saigon.town.TownServices
import quest.saigon.town.avatarFailureReason
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
        assertEquals(0, cutout.getRGB(16, 16) ushr 24)
        assertEquals(255, (cutout.getRGB(10, 10) shr 16) and 0xFF)
        assertBinaryAlpha(cutout)

        val keyed = images.chromaKeyGray(color)
        assertEquals(0, keyed.getRGB(1, 1) ushr 24)
        assertEquals(255, keyed.getRGB(9, 9) ushr 24)
        assertEquals(255, keyed.getRGB(13, 13) ushr 24)
        assertBinaryAlpha(keyed)
    }

    @Test
    fun maskHalfGrayAndUnderIsTransparent() {
        val images = ImageOps()
        val color = solid(24, 24, Color(255, 250, 240))
        val mask = solid(24, 24, Color.BLACK)
        mask.setRGB(4, 4, Color(128, 128, 128).rgb)
        mask.setRGB(6, 6, Color(129, 129, 129).rgb)
        mask.setRGB(8, 8, Color(90, 90, 90).rgb)
        mask.setRGB(10, 10, Color(200, 200, 200).rgb)

        val cutout = images.mergeMaskAsAlpha(color, mask)
        assertEquals(0, cutout.getRGB(1, 1) ushr 24)
        assertEquals(0, cutout.getRGB(4, 4) ushr 24)
        assertEquals(255, cutout.getRGB(6, 6) ushr 24)
        assertEquals(0, cutout.getRGB(8, 8) ushr 24)
        assertEquals(255, cutout.getRGB(10, 10) ushr 24)
        assertEquals(255, (cutout.getRGB(6, 6) shr 16) and 0xFF)
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
        assertTrue(QwenResponses.imageUrls(node).contains("https://example.test/avatar.png"))
        assertEquals(
            "smiling",
            QwenResponses.chatText(QwenResponses.parse("""{"choices":[{"message":{"content":"smiling"}}]}""")),
        )
        val instruction = AvatarPrompts.editInstruction("smiling", "pleased")
        assertTrue(instruction.contains("#bababa"))
        assertTrue(instruction.contains("pleased"))
        assertTrue(instruction.contains("No white border"))
        assertTrue(instruction.contains("no sticker edge"))
        assertTrue(instruction.contains("illustrated character"))
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
        val audio = QwenResponses.parse(
            """
            {
              "output": {
                "audio": { "url": "https://cdn.example/song.mp3" },
                "extra_info": { "lyrics": "[verse] Morning rain" }
              }
            }
            """.trimIndent(),
        )
        assertEquals("https://cdn.example/song.mp3", QwenResponses.audioUrl(audio))
        assertEquals("[verse] Morning rain", QwenResponses.lyrics(audio))
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
    fun postsKeepTheCoverPhotoAndStoreExtras() = testApplication {
        val services = services()
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val http = createClient { install(HttpCookies) }
        val created = http.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0901234599","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.OK, created.status)
        val userId = mapper.readTree(created.bodyAsText()).path("id").asText()
        val avatar = services.repo.insertAvatar(userId, "smiling", 10)
        services.repo.updateAvatar(avatar.id, AvatarStatus.READY, "avatars/ready.png", null)
        val ops = ImageOps()
        val first = ops.writeJpeg(solid(8, 16, Color(40, 90, 160)), 0.85f)
        val second = ops.writeJpeg(solid(8, 16, Color(20, 140, 80)), 0.85f)
        val third = ops.writeJpeg(solid(8, 16, Color(180, 70, 40)), 0.85f)
        fun photoHeaders() = io.ktor.http.Headers.build {
            append(HttpHeaders.ContentType, "image/jpeg")
            append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
        }
        val posted = http.post("/api/posts") {
            setBody(
                io.ktor.client.request.forms.MultiPartFormDataContent(
                    io.ktor.client.request.forms.formData {
                        append("message", "Three views of the river.")
                        append("location", "Cát Lái")
                        append("avatarId", avatar.id)
                        append("photo", first, photoHeaders())
                        append("photo", second, photoHeaders())
                        append("photo", third, photoHeaders())
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, posted.status)
        val body = mapper.readTree(posted.bodyAsText())
        val photoUrl = body.path("photoUrl").asText()
        assertTrue(photoUrl.startsWith("/media/"))
        assertEquals(2, body.path("photoUrls").size())
        assertNotEquals(photoUrl, body.path("photoUrls").path(0).asText())
        assertNotEquals(body.path("photoUrls").path(0).asText(), body.path("photoUrls").path(1).asText())

        val stored = services.repo.findPost(body.path("id").asText())!!
        assertEquals(2, stored.extraPhotoPaths.size)
        assertTrue(services.uploads.resolve(stored.photoPath)!!.isFile)
        stored.extraPhotoPaths.forEach { assertTrue(services.uploads.resolve(it)!!.isFile) }

        val feedCard = mapper.readTree(http.get("/api/posts").bodyAsText()).path("posts").path(0)
        assertEquals(photoUrl, feedCard.path("photoUrl").asText())
        assertEquals(2, feedCard.path("photoUrls").size())

        val tooMany = http.post("/api/posts") {
            setBody(
                io.ktor.client.request.forms.MultiPartFormDataContent(
                    io.ktor.client.request.forms.formData {
                        append("message", "Too many photos")
                        append("location", "Cát Lái")
                        append("avatarId", avatar.id)
                        repeat(Limits.PHOTOS_MAX + 1) { append("photo", first, photoHeaders()) }
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, tooMany.status)
        assertEquals("too_many_photos", mapper.readTree(tooMany.bodyAsText()).path("error").asText())

        val single = http.post("/api/posts") {
            setBody(
                io.ktor.client.request.forms.MultiPartFormDataContent(
                    io.ktor.client.request.forms.formData {
                        append("message", "Just one photo")
                        append("location", "Cát Lái")
                        append("avatarId", avatar.id)
                        append("photo", first, photoHeaders())
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, single.status)
        val one = mapper.readTree(single.bodyAsText())
        assertTrue(one.path("photoUrl").asText().startsWith("/media/"))
        assertEquals(0, one.path("photoUrls").size())

        val extras = stored.extraPhotoPaths
        val cover = stored.photoPath
        assertEquals(HttpStatusCode.OK, http.delete("/api/posts/${stored.id}").status)
        assertEquals(null, services.uploads.resolve(cover))
        extras.forEach { assertEquals(null, services.uploads.resolve(it)) }
    }

    @Test
    fun postsCanBeCreatedWithoutAnAvatar() = testApplication {
        val services = services()
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val http = createClient { install(HttpCookies) }
        val created = http.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0901234500","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.OK, created.status)
        http.patch("/api/me") {
            contentType(ContentType.Application.Json)
            setBody("""{"location":"Cát Lái"}""")
        }
        val jpeg = ImageOps().writeJpeg(solid(8, 16, Color(40, 90, 160)), 0.85f)
        val posted = http.post("/api/posts") {
            setBody(
                io.ktor.client.request.forms.MultiPartFormDataContent(
                    io.ktor.client.request.forms.formData {
                        append("message", "Just a photo of the river.")
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
        val body = mapper.readTree(posted.bodyAsText())
        assertTrue(body.path("avatarUrl").isNull || body.path("avatarUrl").asText().isBlank())
        val stored = services.repo.findPost(body.path("id").asText())!!
        assertEquals(null, stored.avatarId)
        val feedCard = mapper.readTree(http.get("/api/posts").bodyAsText()).path("posts").path(0)
        assertTrue(feedCard.path("avatarUrl").isNull || feedCard.path("avatarUrl").asText().isBlank())
    }

    @Test
    fun postPhotosAreCappedAtEightHundredPixels() {
        val images = ImageOps()
        val source = BufferedImage(1600, 2844, BufferedImage.TYPE_INT_RGB)
        val graphics = source.createGraphics()
        graphics.color = Color(40, 90, 160)
        graphics.fillRect(0, 0, source.width, source.height)
        graphics.dispose()
        val prepared = images.read(images.preparePostPhoto(images.writeJpeg(source, 0.95f)))
        assertEquals(800, Limits.PHOTO_MAX_EDGE)
        assertEquals(0.85, Limits.JPEG_QUALITY)
        assertTrue(maxOf(prepared.width, prepared.height) <= Limits.PHOTO_MAX_EDGE)
    }

    @Test
    fun avatarsAreCappedAtFiveHundredTwelvePixels() {
        val images = ImageOps()
        val source = BufferedImage(1024, 1400, BufferedImage.TYPE_INT_ARGB)
        val graphics = source.createGraphics()
        graphics.color = Color(220, 40, 40, 255)
        graphics.fillRect(0, 0, source.width, source.height)
        graphics.dispose()
        val prepared = images.read(images.prepareAvatar(source))
        assertEquals(512, Limits.AVATAR_MAX_EDGE)
        assertTrue(maxOf(prepared.width, prepared.height) <= Limits.AVATAR_MAX_EDGE)
    }

    @Test
    fun publicStatsAreBatchedBySaigonDay() = testApplication {
        val services = services()
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val anonymous = createClient { }
        val empty = mapper.readTree(anonymous.get("/api/stats").bodyAsText())
        assertEquals("UTC+7", empty.path("timezone").asText())
        assertEquals(0, empty.path("totalUsers").asInt())
        assertEquals(0, empty.path("totalPosts").asInt())
        assertEquals(30, empty.path("days").size())
        val today = SaigonDays.dayKey(1_700_000_000_000L)
        assertEquals(today, empty.path("days").path(29).path("day").asText())

        assertEquals(HttpStatusCode.OK, anonymous.post("/api/stats/visit").status)
        val afterVisit = todayNode(mapper.readTree(anonymous.get("/api/stats").bodyAsText()), today)
        assertEquals(1, afterVisit.path("visitorVisits").asInt())
        assertEquals(0, afterVisit.path("userVisits").asInt())

        val http = createClient { install(HttpCookies) }
        val created = http.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0903334455","password":"secretpass"}""")
        }
        val userId = mapper.readTree(created.bodyAsText()).path("id").asText()
        http.post("/api/stats/visit")
        http.post("/api/stats/visit")
        val afterUserBody = mapper.readTree(anonymous.get("/api/stats").bodyAsText())
        val afterUser = todayNode(afterUserBody, today)
        assertEquals(1, afterUser.path("visitorVisits").asInt())
        assertEquals(2, afterUser.path("userVisits").asInt())
        assertEquals(1, afterUser.path("uniqueUserVisits").asInt())
        assertEquals(1, afterUser.path("newUsers").asInt())
        assertEquals(1, afterUserBody.path("totalUsers").asInt())
        assertEquals(0, afterUserBody.path("totalPosts").asInt())

        val avatar = services.repo.insertAvatar(userId, "smiling", 10)
        services.repo.updateAvatar(avatar.id, AvatarStatus.READY, "avatars/ready.png", null)
        val jpeg = ImageOps().writeJpeg(solid(8, 8, Color(40, 90, 160)), 0.85f)
        val path = services.uploads.writePost(jpeg)
        val post = services.repo.insertPost(PostRecord("", userId, avatar.id, path, "Hello town", "Cát Lái", 1_700_000_000_000L))
        assertEquals(HttpStatusCode.OK, anonymous.post("/api/posts/${post.id}/view").status)
        assertEquals(HttpStatusCode.OK, anonymous.post("/api/posts/${post.id}/contact").status)
        assertEquals(HttpStatusCode.OK, anonymous.post("/api/posts/${post.id}/share").status)

        val countedBody = mapper.readTree(anonymous.get("/api/stats").bodyAsText())
        val counted = todayNode(countedBody, today)
        assertEquals(1, counted.path("posts").asInt())
        assertEquals(1, counted.path("postViews").asInt())
        assertEquals(1, counted.path("replyClicks").asInt())
        assertEquals(1, counted.path("shareClicks").asInt())
        assertEquals(1, countedBody.path("totalUsers").asInt())
        assertEquals(1, countedBody.path("totalPosts").asInt())
    }

    @Test
    fun statsSplitAcrossSaigonMidnight() {
        val before = SaigonDays.startMillis("2023-11-15") - 1
        val after = SaigonDays.startMillis("2023-11-15")
        val repo = InMemoryTownRepository()
        repo.createUser("0901000001", "hash", "Linh", before)
        repo.insertPost(PostRecord("p1", "u", "a", "posts/x.jpg", "hi", "SG", before))
        repo.recordVisit(null, before)
        repo.recordVisit("u1", before)
        repo.recordDailyEvent("postViews", before)
        repo.recordVisit(null, after)

        val days = repo.statsByDay(after)
        val nov14 = days.first { it.day == "2023-11-14" }
        val nov15 = days.first { it.day == "2023-11-15" }
        assertEquals(1, nov14.visitorVisits)
        assertEquals(1, nov14.userVisits)
        assertEquals(1, nov14.uniqueUserVisits)
        assertEquals(1, nov14.posts)
        assertEquals(1, nov14.newUsers)
        assertEquals(1, nov14.postViews)
        assertEquals(1, nov15.visitorVisits)
        assertEquals(0, nov15.posts)
        assertEquals(0, nov15.newUsers)
        val allTime = repo.allTimeStats()
        assertEquals(1, allTime.totalUsers)
        assertEquals(1, allTime.totalPosts)
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

        val ownContact = http.post("/api/posts/${first.id}/contact")
        assertEquals(HttpStatusCode.OK, ownContact.status)
        assertEquals(0, services.repo.findPost(first.id)!!.replyClicks)

        val ownShare = http.post("/api/posts/${first.id}/share")
        assertEquals(HttpStatusCode.OK, ownShare.status)
        assertEquals(0, services.repo.findPost(first.id)!!.shareClicks)

        val seen = createClient { }.post("/api/posts/${first.id}/view")
        assertEquals(HttpStatusCode.OK, seen.status)
        assertEquals(1, services.repo.findPost(first.id)!!.views)
        createClient { }.post("/api/posts/${first.id}/view")
        assertEquals(1, services.repo.findPost(first.id)!!.views)

        val contact = createClient { }.post("/api/posts/${first.id}/contact")
        assertEquals(HttpStatusCode.OK, contact.status)
        assertEquals(1, services.repo.findPost(first.id)!!.replyClicks)

        val share = createClient { }.post("/api/posts/${first.id}/share")
        assertEquals(HttpStatusCode.OK, share.status)
        assertEquals(1, services.repo.findPost(first.id)!!.shareClicks)

        val anonymous = createClient { }
        assertEquals(HttpStatusCode.Unauthorized, anonymous.get("/api/me/posts").status)
        assertEquals(HttpStatusCode.NotFound, anonymous.post("/api/posts/missing/view").status)
        assertEquals(HttpStatusCode.NotFound, anonymous.post("/api/posts/missing/share").status)

        val page = http.get("/api/me/posts?limit=1")
        val listed = mapper.readTree(page.bodyAsText())
        assertEquals(1, listed.path("posts").size())
        assertEquals("Hello town", listed.path("posts").path(0).path("message").asText())
        assertEquals(1, listed.path("posts").path(0).path("views").asInt())
        assertEquals(1, listed.path("posts").path(0).path("replyClicks").asInt())
        assertEquals(1, listed.path("posts").path(0).path("shareClicks").asInt())
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
    fun feedServesAuthorProfilePhotoOnTheNameChipUrl() = testApplication {
        val services = services()
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val http = createClient { install(HttpCookies) }
        val created = http.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905556677","password":"secretpass"}""")
        }
        val userId = mapper.readTree(created.bodyAsText()).path("id").asText()
        val jpeg = ImageOps().writeJpeg(solid(8, 8, Color(40, 90, 160)), 0.85f)
        val photoPath = services.uploads.writeProfile(jpeg)
        val user = services.repo.findUser(userId)!!
        services.repo.updateUser(user.copy(profilePhotoPath = photoPath))
        val avatar = services.repo.insertAvatar(userId, "smiling", 10)
        services.repo.updateAvatar(avatar.id, AvatarStatus.READY, "avatars/ready.png", null)
        val post = services.repo.insertPost(
            PostRecord("", userId, avatar.id, services.uploads.writePost(jpeg), "Hello town", "Cát Lái", 5L),
        )

        val feed = mapper.readTree(http.get("/api/posts").bodyAsText())
        val card = feed.path("posts").path(0)
        assertEquals("Linh", card.path("authorName").asText())
        assertEquals("/api/posts/${post.id}/author-photo", card.path("authorPhotoUrl").asText())

        val anonymous = createClient { }
        val photo = anonymous.get("/api/posts/${post.id}/author-photo")
        assertEquals(HttpStatusCode.OK, photo.status)
        assertContentEquals(jpeg, photo.bodyAsBytes())
        assertEquals(HttpStatusCode.NotFound, anonymous.get("/api/posts/missing/author-photo").status)

        services.repo.updateUser(user.copy(profilePhotoPath = null))
        assertEquals(HttpStatusCode.NotFound, anonymous.get("/api/posts/${post.id}/author-photo").status)
        assertTrue(mapper.readTree(http.get("/api/posts").bodyAsText()).path("posts").path(0).path("authorPhotoUrl").isNull)
    }

    @Test
    fun introIsSavedOnProfileAndIncludedInFeed() = testApplication {
        val services = services()
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val http = createClient { install(HttpCookies) }
        val created = http.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905556688","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.OK, created.status)
        val userId = mapper.readTree(created.bodyAsText()).path("id").asText()

        val tooLong = http.patch("/api/me") {
            contentType(ContentType.Application.Json)
            setBody("""{"intro":"${"x".repeat(Limits.INTRO_MAX + 1)}"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, tooLong.status)
        assertEquals("invalid_intro", mapper.readTree(tooLong.bodyAsText()).path("error").asText())

        val saved = http.patch("/api/me") {
            contentType(ContentType.Application.Json)
            setBody("""{"location":"Thảo Điền","intro":"Coffee, bikes, and late dinners."}""")
        }
        assertEquals(HttpStatusCode.OK, saved.status)
        val me = mapper.readTree(saved.bodyAsText())
        assertEquals("Coffee, bikes, and late dinners.", me.path("intro").asText())
        assertEquals("Thảo Điền", me.path("location").asText())

        val jpeg = ImageOps().writeJpeg(solid(8, 8, Color(40, 90, 160)), 0.85f)
        val avatar = services.repo.insertAvatar(userId, "smiling", 10)
        services.repo.updateAvatar(avatar.id, AvatarStatus.READY, "avatars/ready.png", null)
        services.repo.insertPost(
            PostRecord("", userId, avatar.id, services.uploads.writePost(jpeg), "Hello town", "Cát Lái", 5L),
        )

        val feed = mapper.readTree(http.get("/api/posts").bodyAsText())
        val card = feed.path("posts").path(0)
        assertEquals("Linh", card.path("authorName").asText())
        assertEquals("Thảo Điền", card.path("authorLocation").asText())
        assertEquals("Coffee, bikes, and late dinners.", card.path("authorIntro").asText())
        assertEquals("Cát Lái", card.path("location").asText())
    }

    @Test
    fun servedAvatarKeepsEnclosedHolesTransparent() = testApplication {
        val services = services()
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val images = ImageOps()
        val avatar = BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until avatar.height) {
            for (x in 0 until avatar.width) {
                avatar.setRGB(x, y, 0xFFFF2828.toInt())
            }
        }
        avatar.setRGB(5, 5, 0x00000000)
        val path = services.uploads.writeAvatar(images.writePng(avatar))

        val response = client.get("/media/$path")
        assertEquals(HttpStatusCode.OK, response.status)
        val served = images.read(response.bodyAsBytes())
        assertEquals(0, served.getRGB(5, 5) ushr 24)
        assertEquals(255, served.getRGB(4, 5) ushr 24)
        assertEquals(0, images.read(services.uploads.resolve(path)!!.readBytes()).getRGB(5, 5) ushr 24)
    }

    @Test
    fun servedOversizedAvatarIsDownscaledToMaxEdge() = testApplication {
        val services = services()
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val images = ImageOps()
        val avatar = BufferedImage(800, 900, BufferedImage.TYPE_INT_ARGB)
        val graphics = avatar.createGraphics()
        graphics.color = Color(220, 40, 40, 255)
        graphics.fillRect(0, 0, avatar.width, avatar.height)
        graphics.dispose()
        val path = services.uploads.writeAvatar(images.writePng(avatar))

        val response = client.get("/media/$path")
        assertEquals(HttpStatusCode.OK, response.status)
        val served = images.read(response.bodyAsBytes())
        assertTrue(maxOf(served.width, served.height) <= Limits.AVATAR_MAX_EDGE)
        val stored = images.read(services.uploads.resolve(path)!!.readBytes())
        assertTrue(maxOf(stored.width, stored.height) <= Limits.AVATAR_MAX_EDGE)
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

    @Test
    fun uploadingAProfilePhotoStartsAWavingHelloAvatar() = testApplication {
        val services = services(avatarsConfigured = true)
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val http = createClient { install(HttpCookies) }
        http.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0901112244","password":"secretpass"}""")
        }
        val jpeg = ImageOps().writeJpeg(solid(8, 8, Color(40, 90, 160)), 0.85f)
        val uploaded = http.post("/api/me/photo") {
            setBody(
                io.ktor.client.request.forms.MultiPartFormDataContent(
                    io.ktor.client.request.forms.formData {
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
        assertEquals(HttpStatusCode.OK, uploaded.status)
        assertTrue(mapper.readTree(uploaded.bodyAsText()).path("hasProfilePhoto").asBoolean())
        val listed = mapper.readTree(http.get("/api/avatars").bodyAsText())
        assertEquals(1, listed.path("avatars").size())
        assertEquals(AvatarPrompts.WAVE_HELLO, listed.path("avatars").path(0).path("prompt").asText())
        assertEquals("ready", listed.path("avatars").path(0).path("status").asText())
    }

    @Test
    fun uploadingAProfilePhotoStillWorksWhenAvatarsAreUnavailable() = testApplication {
        val services = services()
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val http = createClient { install(HttpCookies) }
        http.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0901112255","password":"secretpass"}""")
        }
        val jpeg = ImageOps().writeJpeg(solid(8, 8, Color(40, 90, 160)), 0.85f)
        val uploaded = http.post("/api/me/photo") {
            setBody(
                io.ktor.client.request.forms.MultiPartFormDataContent(
                    io.ktor.client.request.forms.formData {
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
        assertEquals(HttpStatusCode.OK, uploaded.status)
        assertTrue(mapper.readTree(uploaded.bodyAsText()).path("hasProfilePhoto").asBoolean())
        assertEquals(0, mapper.readTree(http.get("/api/avatars").bodyAsText()).path("avatars").size())
    }

    private fun todayNode(body: JsonNode, day: String): JsonNode {
        body.path("days").forEach { row ->
            if (row.path("day").asText() == day) return row
        }
        error("missing day $day")
    }

    private fun services(avatarsConfigured: Boolean = false): TownServices {
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
                configured = avatarsConfigured,
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
            config = AppConfig(uploadDir = uploads.root, qwenApiKey = if (avatarsConfigured) "test-key" else null),
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
