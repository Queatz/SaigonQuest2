package quest.saigon.web

import web.window.window

internal val parseJson: (String) -> dynamic = js("JSON.parse")
internal val jsonStringify: (dynamic) -> String = js("JSON.stringify")

internal fun serverOrigin(): String {
    val raw = window.asDynamic().SAIGON_SERVER_ORIGIN
    return (raw as? String)?.trim()?.trimEnd('/').orEmpty()
}

internal fun prefixServerOrigin(path: String): String {
    if (
        path.startsWith("http://") ||
        path.startsWith("https://") ||
        path.startsWith("data:") ||
        path.startsWith("blob:")
    ) {
        return path
    }
    val origin = serverOrigin()
    if (origin.isEmpty() || !path.startsWith("/")) return path
    return origin + path
}

internal fun http(
    method: String,
    path: String,
    json: String? = null,
    form: dynamic = null,
    done: (Int, String) -> Unit,
) {
    val init = js("({})")
    init.method = method
    init.credentials = "include"
    if (json != null) {
        val headers = js("({})")
        headers["Content-Type"] = "application/json"
        init.headers = headers
        init.body = json
    } else if (form != null) {
        init.body = form
    }
    window.asDynamic().fetch(prefixServerOrigin(path), init).then({ response: dynamic ->
        response.text().then({ text: dynamic ->
            done(response.status as Int, text as String)
        })
    }, { _: dynamic ->
        done(0, "")
    })
}

internal fun errorText(body: String, status: Int): String {
    val code = try {
        parseJson(body).error as? String
    } catch (_: Throwable) {
        null
    }
    val key = when (code) {
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
        "invalid_event_price" -> "invalidEventPrice"
        "invalid_poll" -> "invalidPoll"
        "invalid_question" -> "invalidQuestion"
        "invalid_answer" -> "invalidAnswer"
        "invalid_chat_message" -> "invalidChatMessage"
        "invalid_reaction" -> "invalidReaction"
        "already_member" -> "alreadyMember"
        "account_paused" -> "accountPaused"
        else -> if (status == 0) "unavailable" else "errorGeneric"
    }
    return Town.t(key)
}

internal fun num(value: dynamic): Double {
    val parsed = js("Number")(value) as Double
    return if (parsed.isNaN()) 0.0 else parsed
}

internal fun encodeQuery(value: String): String =
    window.asDynamic().encodeURIComponent(value) as String

internal fun recordAppVisit() {
    val storage = window.asDynamic().sessionStorage
    if (storage.getItem("sq_visit") == "1") return
    storage.setItem("sq_visit", "1")
    http("POST", "/api/stats/visit", "{}") { _, _ -> }
}

internal fun recordUserVisit() {
    http("POST", "/api/stats/visit", "{}") { _, _ -> }
}
