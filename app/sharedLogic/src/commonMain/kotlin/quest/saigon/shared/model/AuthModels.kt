package quest.saigon.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class MeDto(
    val id: String,
    val name: String,
    val phone: String = "",
    val location: String = "",
    val placeId: String? = null,
    val locationId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val intro: String = "",
    val language: String = "system",
    val hasProfilePhoto: Boolean = false,
    val photoUrl: String? = null,
    val paused: Boolean = false,
    val admin: Boolean = false,
    val token: String? = null,
)

@Serializable
data class UserProfileDto(
    val id: String,
    val name: String = "",
    val photoUrl: String? = null,
    val location: String = "",
    val intro: String = "",
)

@Serializable
data class LoginRequest(
    val phone: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val name: String,
    val phone: String,
    val password: String,
)

@Serializable
data class UpdateMeRequest(
    val name: String? = null,
    val location: String? = null,
    val placeId: String? = null,
    val locationId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val intro: String? = null,
    val language: String? = null,
    val phone: String? = null,
)

@Serializable
data class ConfigDto(
    val mapsKey: String? = null,
)

@Serializable
data class OkResponse(
    val ok: Boolean = true,
)

@Serializable
data class ApiErrorBody(
    val error: String? = null,
)
