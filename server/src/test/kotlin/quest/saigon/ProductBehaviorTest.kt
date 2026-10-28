package quest.saigon

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import quest.saigon.ai.MusicRender
import quest.saigon.auth.RateLimiter
import quest.saigon.media.UploadStore
import quest.saigon.media.ImageOps
import quest.saigon.town.AvatarService
import quest.saigon.town.InMemoryTownRepository
import quest.saigon.town.MusicService
import quest.saigon.town.ProductOrderRecord
import quest.saigon.town.TownServices
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProductBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun productImagesAreCenterCroppedToSquare() {
        val original = BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB)
        val encoded = ByteArrayOutputStream().also { ImageIO.write(original, "jpg", it) }.toByteArray()
        val prepared = ImageOps().prepareProductPhoto(encoded)
        val result = ImageIO.read(ByteArrayInputStream(prepared))
        assertEquals(result.width, result.height)
    }

    @Test
    fun uploadedProductPhotoCanBeFetchedAsSquareJpeg() = testApplication {
        val services = services()
        application { module(databaseHealth = { HealthStatus(true, "saigonquest", "3") }, services) }
        val seller = createClient { install(HttpCookies) }
        register(seller, "Seller", "0905550141")
        val bytes = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB), "jpg", it)
        }.toByteArray()

        val created = createProduct(seller, "Photo item", "12000", "", "", bytes)
        assertEquals(HttpStatusCode.Created, created.status)
        val photoUrl = mapper.readTree(created.bodyAsText()).path("photoUrls").path(0).asText()
        val downloaded = seller.get(photoUrl)
        assertEquals(HttpStatusCode.OK, downloaded.status)
        val image = ImageIO.read(ByteArrayInputStream(downloaded.bodyAsBytes()))
        assertEquals(image.width, image.height)
    }

    @Test
    fun productPhotosCanBeReorderedWithNewUploadsDuringEdit() = testApplication {
        val services = services()
        application { module(databaseHealth = { HealthStatus(true, "saigonquest", "3") }, services) }
        val seller = createClient { install(HttpCookies) }
        register(seller, "Seller", "0905550142")
        fun photo(color: java.awt.Color): ByteArray = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(60, 60, BufferedImage.TYPE_INT_RGB).apply {
                createGraphics().apply { setColor(color); fillRect(0, 0, 60, 60); dispose() }
            }, "jpg", it)
        }.toByteArray()
        val first = photo(java.awt.Color.RED)
        val second = photo(java.awt.Color.BLUE)
        val created = seller.submitFormWithBinaryData("/api/products", formData {
            append("name", "Photo set")
            append("price", "12000")
            append("category", "")
            append("description", "")
            listOf(first, second).forEachIndexed { index, bytes ->
                append(
                    "photos",
                    bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"photo-$index.jpg\"")
                    },
                )
            }
        })
        assertEquals(HttpStatusCode.Created, created.status)
        val product = mapper.readTree(created.bodyAsText())
        val productId = product.path("id").asText()
        val initialUrls = product.path("photoUrls")
        val firstUrl = initialUrls.path(0).asText()
        val secondUrl = initialUrls.path(1).asText()
        val patch = seller.submitFormWithBinaryData("/api/products/$productId", formData {
            append("name", "Photo set")
            append("price", "12000")
            append("category", "")
            append("description", "")
            append("photoOrder", mapper.writeValueAsString(listOf(secondUrl, "new:0", firstUrl)))
            append(
                "photos",
                photo(java.awt.Color.GREEN),
                Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"new-photo.jpg\"")
                },
            )
        }) { method = io.ktor.http.HttpMethod.Patch }
        assertEquals(HttpStatusCode.OK, patch.status)
        val orderedUrls = mapper.readTree(patch.bodyAsText()).path("photoUrls")
        assertEquals(secondUrl, orderedUrls.path(0).asText())
        assertNotEquals(firstUrl, orderedUrls.path(1).asText())
        assertEquals(firstUrl, orderedUrls.path(2).asText())
    }

    @Test
    fun unresolvedProductOrdersComeFirstAcrossPages() = testApplication {
        val services = services()
        application { module(databaseHealth = { HealthStatus(true, "saigonquest", "3") }, services) }
        val seller = createClient { install(HttpCookies) }
        val buyer = createClient { install(HttpCookies) }
        register(seller, "Seller", "0905550123")
        register(buyer, "Buyer", "0905550124")
        val sellerId = mapper.readTree(seller.get("/api/me").bodyAsText()).path("id").asText()
        val buyerId = mapper.readTree(buyer.get("/api/me").bodyAsText()).path("id").asText()
        val productId = mapper.readTree(createProduct(seller, "Item", "1000", "", "").bodyAsText())
            .path("id").asText()
        services.repo.insertProductOrder(
            ProductOrderRecord(
                id = "order_open",
                buyerId = buyerId,
                sellerId = sellerId,
                productId = productId,
                productName = "Item",
                price = 1000,
                contactMethod = "messages",
                message = "",
                createdAt = 100,
            ),
        )
        services.repo.insertProductOrder(
            ProductOrderRecord(
                id = "order_resolved",
                buyerId = buyerId,
                sellerId = sellerId,
                productId = productId,
                productName = "Item",
                price = 1000,
                contactMethod = "messages",
                message = "",
                createdAt = 200,
            ),
        )

        val resolved = seller.patch("/api/orders/order_resolved") {
            contentType(ContentType.Application.Json)
            setBody("""{"resolved":true}""")
        }
        assertEquals(HttpStatusCode.OK, resolved.status)
        val firstPage = mapper.readTree(seller.get("/api/me/orders?limit=1").bodyAsText())
        assertEquals("order_open", firstPage.path("orders").path(0).path("id").asText())
        val cursor = firstPage.path("nextCursor").asText()
        assertTrue(cursor.isNotBlank())
        val secondPage = mapper.readTree(seller.get("/api/me/orders?limit=1&cursor=$cursor").bodyAsText())
        assertEquals("order_resolved", secondPage.path("orders").path(0).path("id").asText())
        assertTrue(secondPage.path("nextCursor").isNull)
    }

    @Test
    fun buyerOrderSnapshotsProductAndSellerCanResolveIt() = testApplication {
        val services = services()
        application { module(databaseHealth = { HealthStatus(true, "saigonquest", "3") }, services) }
        val seller = createClient { install(HttpCookies) }
        val buyer = createClient { install(HttpCookies) }
        register(seller, "Seller", "0905550121")
        register(buyer, "Buyer", "0905550122")
        val buyerId = mapper.readTree(buyer.get("/api/me").bodyAsText()).path("id").asText()
        val buyerRecord = requireNotNull(services.repo.findUser(buyerId))
        services.repo.updateUser(buyerRecord.copy(
            location = "District 1",
            profilePhotoPath = "buyer.jpg",
            intro = "Looking for handmade goods",
        ))

        val created = createProduct(seller, "Ceramic cup", "125000", "Handmade", "Cup description")
        assertEquals(HttpStatusCode.Created, created.status)
        val product = mapper.readTree(created.bodyAsText())
        val productId = product.path("id").asText()
        assertEquals("Ceramic cup", product.path("name").asText())
        assertEquals(125000, product.path("price").asInt())
        assertEquals(0, product.path("photoUrls").size())

        val publicPage = mapper.readTree(buyer.get("/api/users/${product.path("userId").asText()}/products").bodyAsText())
        assertEquals(1, publicPage.path("count").asInt())
        assertEquals(productId, publicPage.path("products").path(0).path("id").asText())

        val ownOrder = buyer.post("/api/products/$productId/orders") {
            contentType(ContentType.Application.Json)
            setBody("""{"contactMethod":"messages","message":"I'd like to buy this"}""")
        }
        assertEquals(HttpStatusCode.Created, ownOrder.status)
        val order = mapper.readTree(ownOrder.bodyAsText())
        val orderId = order.path("id").asText()
        assertEquals(125000, order.path("price").asInt())
        assertEquals("Ceramic cup", order.path("productName").asText())
        assertTrue(order.path("contactChatId").asText().isNotBlank())
        val chatId = order.path("contactChatId").asText()
        val messages = mapper.readTree(buyer.get("/api/chats/$chatId/messages").bodyAsText()).path("messages")
        assertEquals(1, messages.size())
        assertEquals("I'd like to buy this", messages.path(0).path("text").asText())
        assertEquals(productId, messages.path(0).path("attachment").path("productId").asText())
        assertEquals("Ceramic cup", messages.path(0).path("attachment").path("productName").asText())
        assertEquals(125000, messages.path(0).path("attachment").path("price").asInt())
        assertEquals(1, mapper.readTree(seller.get("/api/me/orders").bodyAsText()).path("openCount").asInt())

        val update = seller.submitFormWithBinaryData("/api/products/$productId", formData {
            append("name", "Ceramic cup")
            append("price", "180000")
            append("category", "Handmade")
            append("description", "Updated description")
        }) { method = io.ktor.http.HttpMethod.Patch }
        assertEquals(HttpStatusCode.OK, update.status)
        val sellerOrders = mapper.readTree(seller.get("/api/me/orders").bodyAsText()).path("orders")
        assertEquals(1, sellerOrders.size())
        assertEquals(orderId, sellerOrders.path(0).path("id").asText())
        assertEquals(125000, sellerOrders.path(0).path("price").asInt())
        assertEquals("Buyer", sellerOrders.path(0).path("buyerName").asText())
        assertTrue(sellerOrders.path(0).path("createdAt").asLong() > 0)
        assertEquals("/api/users/$buyerId/photo", sellerOrders.path(0).path("buyerPhotoUrl").asText())
        assertEquals("District 1", sellerOrders.path(0).path("buyerLocation").asText())
        assertEquals("Looking for handmade goods", sellerOrders.path(0).path("buyerIntro").asText())

        val notifications = mapper.readTree(seller.get("/api/notifications").bodyAsText()).path("notifications")
        assertTrue((0 until notifications.size()).any { notifications.path(it).path("type").asText() == "product_order" })

        val resolved = seller.patch("/api/orders/$orderId") {
            contentType(ContentType.Application.Json)
            setBody("""{"resolved":true}""")
        }
        assertEquals(HttpStatusCode.OK, resolved.status)
        val resolvedOrder = mapper.readTree(resolved.bodyAsText())
        assertTrue(resolvedOrder.path("resolved").asBoolean())
        assertEquals("/api/users/$buyerId/photo", resolvedOrder.path("buyerPhotoUrl").asText())
        assertEquals("District 1", resolvedOrder.path("buyerLocation").asText())
        assertEquals(0, mapper.readTree(seller.get("/api/me/orders").bodyAsText()).path("openCount").asInt())
        val noNoteOrderResponse = buyer.post("/api/products/$productId/orders") {
            contentType(ContentType.Application.Json)
            setBody("""{"contactMethod":"messages","message":""}""")
        }
        assertEquals(HttpStatusCode.Created, noNoteOrderResponse.status)
        val noNoteOrder = mapper.readTree(noNoteOrderResponse.bodyAsText())
        assertEquals(chatId, noNoteOrder.path("contactChatId").asText())
        val messagesWithoutNote = mapper.readTree(buyer.get("/api/chats/$chatId/messages").bodyAsText()).path("messages")
        assertTrue((0 until messagesWithoutNote.size()).any { index ->
            val item = messagesWithoutNote.path(index)
            item.path("text").asText().isEmpty() && item.path("attachment").path("productId").asText() == productId
        })
        assertEquals(1, mapper.readTree(seller.get("/api/me/orders").bodyAsText()).path("openCount").asInt())
        val secondResolved = seller.patch("/api/orders/${noNoteOrder.path("id").asText()}") {
            contentType(ContentType.Application.Json)
            setBody("""{"resolved":true}""")
        }
        assertEquals(HttpStatusCode.OK, secondResolved.status)
        assertEquals(0, mapper.readTree(seller.get("/api/me/orders").bodyAsText()).path("openCount").asInt())
        assertEquals(HttpStatusCode.NotFound, buyer.patch("/api/orders/$orderId") {
            contentType(ContentType.Application.Json)
            setBody("""{"resolved":false}""")
        }.status)

        assertEquals(HttpStatusCode.OK, seller.delete("/api/products/$productId").status)
        assertEquals(2, mapper.readTree(seller.get("/api/me/orders").bodyAsText()).path("orders").size())
    }

    @Test
    fun productValidationAndOwnershipAreEnforced() = testApplication {
        val services = services()
        application { module(databaseHealth = { HealthStatus(true, "saigonquest", "3") }, services) }
        val seller = createClient { install(HttpCookies) }
        val other = createClient { install(HttpCookies) }
        register(seller, "Seller", "0905550131")
        register(other, "Other", "0905550132")

        assertEquals(HttpStatusCode.Unauthorized, createProduct(createClient {}, "Guest item", "1000", "", "").status)
        assertEquals(HttpStatusCode.BadRequest, createProduct(seller, "", "1000", "", "").status)
        assertEquals(HttpStatusCode.BadRequest, createProduct(seller, "x".repeat(121), "1000", "", "").status)
        assertEquals(HttpStatusCode.BadRequest, createProduct(seller, "Item", "1000", "c".repeat(81), "").status)
        assertEquals(HttpStatusCode.BadRequest, createProduct(seller, "Item", "1000", "", "d".repeat(5001)).status)
        assertEquals(HttpStatusCode.BadRequest, createProduct(seller, "Item", "100000001", "", "").status)

        val productId = mapper.readTree(createProduct(seller, "Item", "1000", "", "").bodyAsText()).path("id").asText()
        val patch = other.submitFormWithBinaryData("/api/products/$productId", formData {
            append("name", "Changed")
            append("price", "2000")
            append("category", "")
            append("description", "")
        }) { method = io.ktor.http.HttpMethod.Patch }
        assertEquals(HttpStatusCode.NotFound, patch.status)
        assertEquals(HttpStatusCode.NotFound, other.delete("/api/products/$productId").status)
        assertEquals(HttpStatusCode.NotFound, other.patch("/api/orders/missing") {
            contentType(ContentType.Application.Json)
            setBody("""{"resolved":true}""")
        }.status)

        val selfOrder = seller.post("/api/products/$productId/orders") {
            contentType(ContentType.Application.Json)
            setBody("""{"contactMethod":"zalo","message":""}""")
        }
        assertEquals(HttpStatusCode.BadRequest, selfOrder.status)
        assertFalse(mapper.readTree(seller.get("/api/me/orders").bodyAsText()).path("orders").size() > 0)
    }

    private suspend fun register(client: io.ktor.client.HttpClient, name: String, phone: String) {
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"$name","phone":"$phone","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private suspend fun createProduct(
        client: io.ktor.client.HttpClient,
        name: String,
        price: String,
        category: String,
        description: String,
        photo: ByteArray? = null,
    ): io.ktor.client.statement.HttpResponse = client.submitFormWithBinaryData("/api/products", formData {
        append("name", name)
        append("price", price)
        append("category", category)
        append("description", description)
        if (photo != null) {
            append(
                "photos",
                photo,
                Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"product.jpg\"")
                },
            )
        }
    })

    private fun services(): TownServices {
        val clock = { 1_700_000_000_000L }
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-products", "").apply {
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
                renderer = { _, _, _ -> MusicRender(byteArrayOf(1, 2, 3, 4), "Song") },
                configured = false,
                executor = { it.run() },
                clock = clock,
            ),
            config = AppConfig(uploadDir = uploads.root, qwenApiKey = null),
            clock = clock,
            contactLimiter = RateLimiter(1, 60_000, clock),
        )
    }
}
