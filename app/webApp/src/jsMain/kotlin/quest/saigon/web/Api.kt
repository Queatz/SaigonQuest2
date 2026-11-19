package quest.saigon.web

import quest.saigon.shared.js.JsSharedHolder
import quest.saigon.shared.js.launchAction
import quest.saigon.shared.network.ApiErrorMapper
import quest.saigon.shared.platform.defaultServerOrigin
import web.window.window

internal val parseJson: (String) -> dynamic = js("JSON.parse")
internal val jsonStringify: (dynamic) -> String = js("JSON.stringify")

internal fun serverOrigin(): String = defaultServerOrigin()

internal fun prefixServerOrigin(path: String): String =
    JsSharedHolder.client.api.resolveUrl(path)

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
    val headers = js("({})")
    headers["X-Saigon-Client"] = "web"
    if (json != null) {
        headers["Content-Type"] = "application/json"
        init.body = json
    } else if (form != null) {
        init.body = form
    }
    init.headers = headers
    window.asDynamic().fetch(prefixServerOrigin(path), init).then({ response: dynamic ->
        response.text().then({ text: dynamic ->
            done(response.status as Int, text as String)
        })
    }, { _: dynamic ->
        done(0, "")
    })
}

internal fun errorText(body: String, status: Int): String =
    ApiErrorMapper.toFailure(status, body, Town.language).message

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
    launchAction(JsSharedHolder.scope) { JsSharedHolder.client.api.recordVisit() }
}

internal fun recordUserVisit() {
    launchAction(JsSharedHolder.scope) { JsSharedHolder.client.api.recordVisit() }
}
