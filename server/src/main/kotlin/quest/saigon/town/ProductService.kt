package quest.saigon.town

import quest.saigon.Limits
import quest.saigon.ProductContactMethod
import quest.saigon.http.Jsons
import quest.saigon.media.UploadStore

class ProductService(
    private val repo: TownRepository,
    private val uploads: UploadStore,
    private val hub: NotificationHub,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pusher: NotificationPusher = NoNotificationPush,
    private val chats: ChatService,
) {
    fun create(
        actor: UserRecord,
        name: String,
        price: Long,
        category: String,
        description: String,
        photoPaths: List<String>,
    ): ProductRecord {
        ensureAvailable(actor)
        val cleaned = clean(name, price, category, description, photoPaths)
        return repo.insertProduct(
            ProductRecord("", actor.id, cleaned.name, cleaned.price, cleaned.category, cleaned.description, photoPaths, clock()),
        )
    }

    fun update(
        actor: UserRecord,
        id: String,
        name: String,
        price: Long,
        category: String,
        description: String,
        photoPaths: List<String>,
    ): ProductRecord {
        ensureAvailable(actor)
        val current = owned(actor, id)
        val cleaned = clean(name, price, category, description, photoPaths)
        val removedPhotos = current.photoPaths - photoPaths.toSet()
        val updated = repo.updateProduct(
            current.copy(
                name = cleaned.name,
                price = cleaned.price,
                category = cleaned.category,
                description = cleaned.description,
                photoPaths = photoPaths,
            ),
        ) ?: throw TownException("not_found")
        removedPhotos.forEach(uploads::delete)
        return updated
    }

    fun delete(actor: UserRecord, id: String) {
        ensureAvailable(actor)
        val product = owned(actor, id)
        repo.deleteProduct(id) ?: throw TownException("not_found")
        product.photoPaths.forEach(uploads::delete)
    }

    fun list(userId: String, cursor: String?, limit: Int): ProductPage =
        repo.productsByUser(userId, cursor, limit.coerceIn(1, 50))

    fun order(actor: UserRecord, productId: String, contactMethod: String, message: String): ProductOrderRecord {
        ensureAvailable(actor)
        val product = repo.findProduct(productId) ?: throw TownException("not_found")
        if (product.userId == actor.id) throw TownException("cannot_order_own_product")
        if (contactMethod != ProductContactMethod.ZALO && contactMethod != ProductContactMethod.MESSAGES) {
            throw TownException("invalid_contact_method")
        }
        val cleanedMessage = message.trim()
        if (cleanedMessage.length > Limits.CHAT_MESSAGE_MAX || cleanedMessage.any { it.isISOControl() && it != '\n' && it != '\t' }) {
            throw TownException("invalid_order_message")
        }
        val contactChat = if (contactMethod == ProductContactMethod.MESSAGES) {
            chats.createDirect(
                actor,
                product.userId,
                cleanedMessage,
                ChatAttachment(
                    type = "product",
                    productId = product.id,
                    productName = product.name,
                    price = product.price,
                    photoPath = product.photoPaths.firstOrNull(),
                ),
            )
        } else {
            null
        }
        val order = repo.insertProductOrder(
            ProductOrderRecord(
                id = "",
                buyerId = actor.id,
                sellerId = product.userId,
                productId = product.id,
                productName = product.name,
                price = product.price,
                contactMethod = contactMethod,
                message = cleanedMessage,
                contactChatId = contactChat?.id,
                createdAt = clock(),
            ),
        )
        val notification = repo.insertNotification(
            NotificationRecord(
                id = "",
                userId = product.userId,
                type = NotificationType.PRODUCT_ORDER,
                postId = order.id,
                commentId = "",
                actorId = actor.id,
                actorName = actor.name,
                preview = product.name.take(80),
                createdAt = clock(),
                chatId = contactChat?.id.orEmpty(),
            ),
        )
        hub.publish(product.userId, Jsons.write(CommentService.notificationPayload(notification)))
        pusher.notify(product.userId, notification)
        return order
    }

    fun orders(actor: UserRecord, cursor: String?, limit: Int): ProductOrderPage =
        repo.productOrdersForSeller(actor.id, cursor, limit.coerceIn(1, 50))

    fun resolve(actor: UserRecord, orderId: String, resolved: Boolean): ProductOrderView =
        repo.updateProductOrderResolved(orderId, actor.id, resolved) ?: throw TownException("not_found")

    private fun owned(actor: UserRecord, id: String): ProductRecord {
        val product = repo.findProduct(id) ?: throw TownException("not_found")
        if (product.userId != actor.id) throw TownException("not_found")
        return product
    }

    private fun ensureAvailable(actor: UserRecord) {
        if (actor.paused) throw TownException("account_paused")
    }

    private fun clean(
        name: String,
        price: Long,
        category: String,
        description: String,
        photoPaths: List<String>,
    ): ProductFields {
        val cleanName = name.trim()
        val cleanCategory = category.trim()
        val cleanDescription = description.replace("\r\n", "\n").trim()
        if (cleanName.isEmpty() || cleanName.length > Limits.PRODUCT_NAME_MAX || cleanName.any(Char::isISOControl)) {
            throw TownException("invalid_product_name")
        }
        if (price !in 1..Limits.EVENT_PRICE_MAX) throw TownException("invalid_product_price")
        if (cleanCategory.length > Limits.PRODUCT_CATEGORY_MAX || cleanCategory.any(Char::isISOControl)) {
            throw TownException("invalid_product_category")
        }
        if (cleanDescription.length > Limits.PRODUCT_DESCRIPTION_MAX || cleanDescription.any { it.isISOControl() && it != '\n' }) {
            throw TownException("invalid_product_description")
        }
        if (photoPaths.size > Limits.PHOTOS_MAX) throw TownException("too_many_photos")
        return ProductFields(cleanName, price, cleanCategory, cleanDescription)
    }

    private data class ProductFields(
        val name: String,
        val price: Long,
        val category: String,
        val description: String,
    )
}
