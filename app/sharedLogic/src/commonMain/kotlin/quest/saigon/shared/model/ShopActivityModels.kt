package quest.saigon.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ProductDto(
    val id: String,
    val userId: String = "",
    val name: String = "",
    val price: Long = 0L,
    val category: String = "",
    val description: String = "",
    val photoUrls: List<String> = emptyList(),
    val sellerName: String? = null,
    val sellerPhotoUrl: String? = null,
    val sellerLocation: String? = null,
    val sellerIntro: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
data class ProductsPageDto(
    val products: List<ProductDto> = emptyList(),
    val count: Long = 0L,
    val nextCursor: String? = null,
)

@Serializable
data class CreateProductOrderRequest(
    val contactMethod: String,
    val message: String,
)

@Serializable
data class ProductOrderDto(
    val id: String,
    val buyerId: String = "",
    val sellerId: String = "",
    val productId: String = "",
    val productName: String = "",
    val price: Long = 0L,
    val contactMethod: String = "",
    val message: String = "",
    val contactChatId: String? = null,
    val resolved: Boolean = false,
    val createdAt: Long = 0L,
    val buyerName: String? = null,
    val buyerPhone: String? = null,
    val buyerPhotoUrl: String? = null,
    val buyerLocation: String? = null,
    val buyerIntro: String? = null,
)

@Serializable
data class OrdersPageDto(
    val orders: List<ProductOrderDto> = emptyList(),
    val nextCursor: String? = null,
    val openCount: Long = 0L,
)

@Serializable
data class ResolveOrderRequest(
    val resolved: Boolean,
)

@Serializable
data class ActivityScheduleSlotDto(
    val dayOfWeek: Int = 0,
    val startMinutes: Int = 0,
    val endMinutes: Int = 0,
)

@Serializable
data class ActivityTranslationsDto(
    val status: String = "",
    val en: Map<String, String> = emptyMap(),
    val vi: Map<String, String> = emptyMap(),
)

@Serializable
data class ActivityDto(
    val id: String,
    val createdBy: String = "",
    val emoji: String = "✨",
    val name: String = "",
    val description: String = "",
    val locationName: String = "",
    val locationId: String? = null,
    val placeId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val hostUserId: String? = null,
    val host: EventPersonDto? = null,
    val schedule: List<ActivityScheduleSlotDto> = emptyList(),
    val interestedCount: Long = 0L,
    val interested: Boolean = false,
    val interestedPeople: List<EventPersonDto> = emptyList(),
    val photoUrls: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val translations: ActivityTranslationsDto? = null,
)

@Serializable
data class ActivitiesPageDto(
    val activities: List<ActivityDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class CreateActivityRequest(
    val emoji: String = "",
    val name: String,
    val description: String = "",
    val locationName: String,
    val locationId: String? = null,
    val placeId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val hostUserId: String? = null,
    val schedule: List<ActivityScheduleSlotDto> = emptyList(),
    val interested: Boolean = true,
)

@Serializable
data class UpdateActivityRequest(
    val emoji: String = "",
    val name: String,
    val description: String = "",
    val hostUserId: String? = null,
    val schedule: List<ActivityScheduleSlotDto> = emptyList(),
    val translations: Map<String, Map<String, String>>? = null,
)

@Serializable
data class ActivityStartChatRequest(
    val userIds: List<String>,
)
