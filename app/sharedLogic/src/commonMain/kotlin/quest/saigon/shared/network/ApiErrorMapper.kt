package quest.saigon.shared.network

import kotlinx.serialization.json.Json
import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.shared.model.ApiErrorBody

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(
        val status: Int,
        val code: String?,
        val copyKey: String,
        val message: String,
    ) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

object ApiErrorMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun extractCode(body: String): String? {
        if (body.isBlank()) return null
        return try {
            json.decodeFromString(ApiErrorBody.serializer(), body).error?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    fun copyKey(code: String?, status: Int): String = when (code) {
        "bad_credentials" -> "badCredentials"
        "phone_taken" -> "phoneTaken"
        "invalid_phone" -> "invalidPhone"
        "invalid_password" -> "invalidPassword"
        "invalid_name" -> "invalidName"
        "invalid_message" -> "invalidMessage"
        "invalid_location" -> "invalidLocation"
        "invalid_intro" -> "invalidIntro"
        "invalid_prompt" -> "invalidPrompt"
        "photo_required" -> "photoRequired"
        "too_many_photos" -> "tooManyPhotos"
        "avatar_required" -> "avatarRequired"
        "profile_photo_required" -> "profilePhotoRequired"
        "avatar_busy" -> "avatarBusy"
        "avatar_not_failed" -> "avatarNotFailed"
        "invalid_music_prompt" -> "invalidMusicPrompt"
        "invalid_music_name" -> "invalidMusicName"
        "music_busy" -> "musicBusy"
        "music_not_failed" -> "musicNotFailed"
        "invalid_music", "invalid_gender" -> "invalidMusic"
        "qwen_not_configured" -> "qwenMissing"
        "too_large" -> "tooLarge"
        "invalid_photo" -> "invalidPhoto"
        "rate_limited" -> "rateLimited"
        "unavailable" -> "unavailable"
        "unauthorized" -> "needSignInToPost"
        "invalid_feature" -> "invalidFeature"
        "invalid_comment" -> "invalidComment"
        "invalid_community" -> "invalidCommunity"
        "invalid_description" -> "invalidDescription"
        "invalid_event" -> "invalidEvent"
        "invalid_event_end" -> "invalidEventEnd"
        "invalid_event_price" -> "invalidEventPrice"
        "invalid_product_name" -> "invalidProductName"
        "invalid_product_price" -> "invalidProductPrice"
        "invalid_product_category" -> "invalidProductCategory"
        "invalid_product_description" -> "invalidProductDescription"
        "invalid_activity_name" -> "invalidActivityName"
        "invalid_activity_description" -> "invalidActivityDescription"
        "invalid_activity_schedule" -> "invalidActivitySchedule"
        "invalid_activity_host" -> "invalidActivityHost"
        "no_chat_members" -> "noChatMembers"
        "cannot_order_own_product" -> "cannotOrderOwnProduct"
        "invalid_contact_method" -> "invalidContactMethod"
        "invalid_order_message" -> "invalidOrderMessage"
        "invalid_poll" -> "invalidPoll"
        "invalid_question" -> "invalidQuestion"
        "invalid_answer" -> "invalidAnswer"
        "invalid_cta" -> "invalidCallToAction"
        "invalid_cta_url" -> "invalidCallToActionUrl"
        "invalid_open_chat" -> "invalidOpenChat"
        "invalid_check_in" -> "invalidCheckIn"
        "open_chat_expired" -> "openChatExpired"
        "open_chat_full" -> "openChatFull"
        "invalid_chat_message" -> "invalidChatMessage"
        "invalid_reaction" -> "invalidReaction"
        "already_member" -> "alreadyMember"
        "account_paused" -> "accountPaused"
        else -> if (status == 0) "unavailable" else "errorGeneric"
    }

    fun toFailure(status: Int, body: String, language: Language): ApiResult.Failure {
        val code = extractCode(body)
        val key = copyKey(code, status)
        return ApiResult.Failure(
            status = status,
            code = code,
            copyKey = key,
            message = Copy.text(language, key),
        )
    }
}
