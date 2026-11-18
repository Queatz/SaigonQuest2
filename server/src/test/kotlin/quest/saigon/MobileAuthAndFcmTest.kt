package quest.saigon

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import quest.saigon.media.UploadStore
import quest.saigon.push.VapidKeys
import quest.saigon.push.WebPushResult
import quest.saigon.town.AvatarService
import quest.saigon.town.CompositeNotificationPusher
import quest.saigon.town.EventService
import quest.saigon.town.FcmDelivery
import quest.saigon.town.FcmPushResult
import quest.saigon.town.FcmPushService
import quest.saigon.town.FcmTokenRecord
import quest.saigon.town.InMemoryTownRepository
import quest.saigon.town.MusicService
import quest.saigon.town.NotificationHub
import quest.saigon.town.NotificationRecord
import quest.saigon.town.NotificationType
import quest.saigon.town.PostRecord
import quest.saigon.town.PushDelivery
import quest.saigon.town.PushSubscriptionRecord
import quest.saigon.town.TownServices
import quest.saigon.town.WebPushService
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MobileAuthAndFcmTest {
    private val mapper = ObjectMapper()

    @Test
    fun bearerTokenWorksAcrossAuthLifecycleAndRevokesOnLogout() = testApplication {
        val services = testServices(clock = { 1_700_000_000_000L })
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }

        val client = createClient { }
        val regResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"An","phone":"0901110101","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.OK, regResponse.status)
        val regJson = mapper.readTree(regResponse.bodyAsText())
        val registerToken = regJson.path("token").asText()
        assertTrue(registerToken.isNotBlank())
        assertTrue(regResponse.headers.getAll(HttpHeaders.SetCookie).orEmpty().any { it.contains("sq_session=") })

        val meResponse = client.get("/api/me") {
            header(HttpHeaders.Authorization, "Bearer $registerToken")
        }
        assertEquals(HttpStatusCode.OK, meResponse.status)
        assertEquals("An", mapper.readTree(meResponse.bodyAsText()).path("name").asText())

        val logoutResponse = client.post("/api/auth/logout") {
            header(HttpHeaders.Authorization, "Bearer $registerToken")
        }
        assertEquals(HttpStatusCode.OK, logoutResponse.status)

        val meAfterLogout = client.get("/api/me") {
            header(HttpHeaders.Authorization, "Bearer $registerToken")
        }
        assertEquals(HttpStatusCode.Unauthorized, meAfterLogout.status)

        val loginResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"phone":"0901110101","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val loginToken = mapper.readTree(loginResponse.bodyAsText()).path("token").asText()
        assertTrue(loginToken.isNotBlank())
        assertTrue(loginToken != registerToken)

        val meAfterLogin = client.get("/api/me") {
            header(HttpHeaders.Authorization, "Bearer $loginToken")
        }
        assertEquals(HttpStatusCode.OK, meAfterLogin.status)
    }

    @Test
    fun webCookieSessionAndAndroidBearerSessionCoexistWithoutInvalidation() = testApplication {
        val services = testServices(clock = { 1_700_000_000_000L })
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }

        val client = createClient { }
        val regResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"An","phone":"0901110102","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.OK, regResponse.status)
        val webToken = mapper.readTree(regResponse.bodyAsText()).path("token").asText()
        assertTrue(webToken.isNotBlank())

        // Simulate Android logging in as the same user later.
        val androidLogin = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"phone":"0901110102","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.OK, androidLogin.status)
        val androidToken = mapper.readTree(androidLogin.bodyAsText()).path("token").asText()
        assertTrue(androidToken.isNotBlank())
        assertTrue(androidToken != webToken)

        // Existing Web cookie session must still remain valid.
        val webMe = client.get("/api/me") {
            header(HttpHeaders.Cookie, "sq_session=$webToken")
        }
        assertEquals(HttpStatusCode.OK, webMe.status)
        assertEquals("An", mapper.readTree(webMe.bodyAsText()).path("name").asText())

        // Android Bearer session must also work concurrently.
        val androidMe = client.get("/api/me") {
            header(HttpHeaders.Authorization, "Bearer $androidToken")
        }
        assertEquals(HttpStatusCode.OK, androidMe.status)

        // Logging out on Android revokes only Android's token, leaving Web's session intact.
        val androidLogout = client.post("/api/auth/logout") {
            header(HttpHeaders.Authorization, "Bearer $androidToken")
        }
        assertEquals(HttpStatusCode.OK, androidLogout.status)

        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/api/me") { header(HttpHeaders.Authorization, "Bearer $androidToken") }.status,
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get("/api/me") { header(HttpHeaders.Cookie, "sq_session=$webToken") }.status,
        )
    }

    @Test
    fun fcmRoutesRequireAuthValidateTokenAndCompositePusherFansOut() = testApplication {
        val webPayloads = mutableListOf<String>()
        val fcmPayloads = mutableListOf<Triple<String, String, Map<String, String>>>()
        val repo = InMemoryTownRepository()
        val vapid = VapidKeys.generate()
        val webPush = WebPushService(
            repo = repo,
            keys = vapid,
            delivery = PushDelivery { _, payload ->
                webPayloads += payload
                WebPushResult.SENT
            },
        )
        val fcmPush = FcmPushService(
            repo = repo,
            delivery = FcmDelivery { record, title, body, data ->
                fcmPayloads += Triple(record.token, "$title|$body", data)
                if (record.token.contains("unregistered")) FcmPushResult.UNREGISTERED else FcmPushResult.SENT
            },
        )
        val composite = CompositeNotificationPusher(listOf(webPush, fcmPush))
        val services = testServices(repo = repo, clock = { 1_700_000_000_000L }, pusher = composite, vapid = vapid)
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }

        val guest = createClient { }
        assertEquals(
            HttpStatusCode.Unauthorized,
            guest.post("/api/push/fcm") {
                contentType(ContentType.Application.Json)
                setBody("""{"token":"fcm-token-1234567890"}""")
            }.status,
        )

        val ownerReg = mapper.readTree(
            guest.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Linh","phone":"0901110102","password":"secretpass"}""")
            }.bodyAsText(),
        )
        val ownerToken = ownerReg.path("token").asText()
        val ownerId = ownerReg.path("id").asText()

        val friendToken = mapper.readTree(
            guest.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Minh","phone":"0901110103","password":"secretpass"}""")
            }.bodyAsText(),
        ).path("token").asText()

        assertEquals(
            HttpStatusCode.BadRequest,
            guest.post("/api/push/fcm") {
                header(HttpHeaders.Authorization, "Bearer $ownerToken")
                contentType(ContentType.Application.Json)
                setBody("""{"token":"short"}""")
            }.status,
        )

        assertEquals(
            HttpStatusCode.OK,
            guest.post("/api/push/fcm") {
                header(HttpHeaders.Authorization, "Bearer $ownerToken")
                contentType(ContentType.Application.Json)
                setBody("""{"token":"fcm-device-token-valid-1234","language":"vi"}""")
            }.status,
        )
        assertEquals(
            HttpStatusCode.OK,
            guest.post("/api/push/fcm") {
                header(HttpHeaders.Authorization, "Bearer $ownerToken")
                contentType(ContentType.Application.Json)
                setBody("""{"token":"fcm-device-token-unregistered-999","language":"en"}""")
            }.status,
        )
        assertEquals(
            HttpStatusCode.OK,
            guest.post("/api/push/subscribe") {
                header(HttpHeaders.Authorization, "Bearer $ownerToken")
                contentType(ContentType.Application.Json)
                setBody("""{"endpoint":"https://push.example/owner-web","keys":{"p256dh":"ABCDEFGHIJK","auth":"1234567890"},"language":"en"}""")
            }.status,
        )

        val post = repo.insertPost(
            PostRecord(
                id = "",
                userId = ownerId,
                photoPath = "p.jpg",
                message = "Banh mi morning",
                location = "District 1",
                createdAt = 1_700_000_000_000L,
            ),
        )

        val commentRes = guest.post("/api/posts/${post.id}/comments") {
            header(HttpHeaders.Authorization, "Bearer $friendToken")
            contentType(ContentType.Application.Json)
            setBody("""{"text":"On my way!"}""")
        }
        assertEquals(HttpStatusCode.Created, commentRes.status)

        assertEquals(1, webPayloads.size)
        assertEquals(2, fcmPayloads.size)
        val viPayload = fcmPayloads.first { it.first == "fcm-device-token-valid-1234" }
        assertTrue(viPayload.second.startsWith(NotificationCopy.title(Language.VI, NotificationType.POST_COMMENT, "Minh", "On my way!")))
        assertEquals("/p/${post.id}", viPayload.third["url"])
        assertEquals(post.id, viPayload.third["postId"])
        assertEquals(NotificationType.POST_COMMENT, viPayload.third["type"])

        // Unregistered FCM token was pruned automatically
        assertEquals(listOf("fcm-device-token-valid-1234"), repo.fcmTokensFor(ownerId).map { it.token })

        // Explicit DELETE /api/push/fcm removes remaining token
        val deleteRes = guest.delete("/api/push/fcm") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"token":"fcm-device-token-valid-1234"}""")
        }
        assertEquals(HttpStatusCode.OK, deleteRes.status)
        assertEquals(emptyList(), repo.fcmTokensFor(ownerId))
    }

    @Test
    fun appConfigLoadsFcmServiceAccountJson() {
        val jsonFile = File.createTempFile("fcm-sa", ".json").apply {
            deleteOnExit()
            writeText(
                """
                {
                  "project_id": "saigon-town-test",
                  "client_email": "fcm@saigon-town-test.iam.gserviceaccount.com",
                  "private_key": "-----BEGIN PRIVATE KEY-----\nABC\n-----END PRIVATE KEY-----\n"
                }
                """.trimIndent(),
            )
        }
        val config = AppConfig.load(env = { key -> if (key == "FCM_SERVICE_ACCOUNT_JSON") jsonFile.absolutePath else null })
        assertTrue(config.fcmConfigured)
        assertEquals("saigon-town-test", config.fcmProjectId)
        assertEquals("fcm@saigon-town-test.iam.gserviceaccount.com", config.fcmClientEmail)
    }

    @Test
    fun appConfigLoadsFcmFromEnvFileAndParsesRsaKey() {
        val keyPair = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pkcs8Base64 = java.util.Base64.getEncoder().encodeToString(keyPair.private.encoded)
        val pem = "-----BEGIN PRIVATE KEY-----\n$pkcs8Base64\n-----END PRIVATE KEY-----\n"
        val jsonFile = File.createTempFile("fcm-sa-real", ".json").apply {
            deleteOnExit()
            writeText(
                mapper.writeValueAsString(
                    mapOf(
                        "project_id" to "saigon-town-3e607",
                        "client_email" to "firebase-adminsdk@saigon-town-3e607.iam.gserviceaccount.com",
                        "private_key" to pem,
                    ),
                ),
            )
        }
        val envFile = File.createTempFile("saigon-test", ".env").apply {
            deleteOnExit()
            writeText("FCM_SERVICE_ACCOUNT_JSON=${jsonFile.absolutePath}\n")
        }
        val config = AppConfig.load(env = { null }, envFile = envFile)
        assertTrue(config.fcmConfigured)
        assertEquals("saigon-town-3e607", config.fcmProjectId)
        val delivery = quest.saigon.town.HttpFcmDelivery.fromConfig(config)
        // Verify key parsed and configured (would fail at network/JWT call only if invoked; non-skipped config is active)
        assertTrue(config.fcmPrivateKey?.contains("BEGIN PRIVATE KEY") == true)
        assertEquals(quest.saigon.town.HttpFcmDelivery::class, delivery::class)
    }

    @Test
    fun multipartPhotoPostUploadMatchesClientFormData() = testApplication {
        val repo = InMemoryTownRepository()
        val services = testServices(repo = repo, clock = { 1_700_000_000_000L })
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }

        val mobile = createClient { }
        val regBody = mobile.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"phone":"0903334455","password":"password123","name":"Vy"}""")
        }.bodyAsText()
        val token = mapper.readTree(regBody).path("token").asText()
        assertTrue(token.isNotBlank())

        val img = java.awt.image.BufferedImage(16, 28, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val jpegBytes = java.io.ByteArrayOutputStream().also { out ->
            javax.imageio.ImageIO.write(img, "jpg", out)
        }.toByteArray()

        val response = mobile.post("/api/posts") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(
                io.ktor.client.request.forms.MultiPartFormDataContent(
                    io.ktor.client.request.forms.formData {
                        append("message", "Morning coffee")
                        append("location", "District 1")
                        append(
                            "photo",
                            jpegBytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
                                append(HttpHeaders.ContentDisposition, "filename=\"photo_0.jpg\"")
                            },
                        )
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        val postJson = mapper.readTree(response.bodyAsText())
        assertEquals("Morning coffee", postJson.path("message").asText())
        assertTrue(postJson.path("photoUrl").asText().startsWith("/media/"))
    }

    private fun testServices(
        repo: InMemoryTownRepository = InMemoryTownRepository(),
        clock: () -> Long,
        pusher: quest.saigon.town.NotificationPusher = quest.saigon.town.NoNotificationPush,
        vapid: VapidKeys? = null,
    ): TownServices {
        val uploads = UploadStore(File.createTempFile("sq-mobile", "").apply {
            delete()
            mkdirs()
        })
        val hub = NotificationHub()
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
            config = AppConfig(
                uploadDir = uploads.root,
                vapidPublicKey = vapid?.publicKey,
                vapidPrivateKey = vapid?.privateKey,
            ),
            clock = clock,
            hub = hub,
            pusher = pusher,
            contactLimiter = RateLimiter(1, 60_000, clock),
            events = EventService(repo, hub, clock, pusher),
        )
    }
}
