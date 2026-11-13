package quest.saigon.web

import quest.saigon.Language
import quest.saigon.Limits
import web.html.HTMLElement
import web.window.window

internal data class CheckInCard(
    val postId: String,
    val authorId: String,
    val authorName: String,
    val authorPhotoUrl: String?,
    val location: String,
    val placeId: String?,
    val locationId: String?,
    val lat: Double,
    val lng: Double,
    val untilAt: Long,
)

internal class CheckInBubbles(
    host: HTMLElement,
    private val router: HistoryRouter,
) {
    private val root = el("div", "check-in-bubbles")
    private var restoreTimer: dynamic = null
    private var closed = false
    private val expiryTimers = mutableListOf<dynamic>()

    init {
        root.setAttribute("aria-label", Town.t("checkInDialogTitle"))
        host.appendChild(root)
        http("GET", "/api/checkins") { status, body ->
            if (closed || status != 200) return@http
            val items: dynamic = parseJson(body).checkIns
            val count = if (items == null || items == undefined) 0 else num(items.length).toInt()
            for (index in 0 until count) {
                val item: dynamic = js("items[index]")
                val checkIn = checkInFrom(item) ?: continue
                if (checkIn.untilAt <= num(js("Date.now()")).toLong()) continue
                root.appendChild(bubble(checkIn, index))
            }
        }
    }

    fun hideUntilIdle() {
        if (closed) return
        if (restoreTimer != null && restoreTimer != undefined) window.asDynamic().clearTimeout(restoreTimer)
        root.asDynamic().classList.add("hidden")
        restoreTimer = window.asDynamic().setTimeout({
            restoreTimer = null
            if (!closed) root.asDynamic().classList.remove("hidden")
        }, 5_000)
    }

    fun close() {
        closed = true
        if (restoreTimer != null && restoreTimer != undefined) window.asDynamic().clearTimeout(restoreTimer)
        restoreTimer = null
        expiryTimers.forEach { window.asDynamic().clearTimeout(it) }
        expiryTimers.clear()
        root.asDynamic().remove()
    }

    private fun bubble(checkIn: CheckInCard, index: Int): HTMLElement {
        val button = el("button", "check-in-bubble")
        button.setAttribute("type", "button")
        button.setAttribute("aria-label", checkInSummary(checkIn))
        button.asDynamic().style.setProperty("--check-in-row-offset", "${index % 3 * 64}px")
        button.asDynamic().style.animationDelay = "-${index * 2}s"
        val avatar = avatar(checkIn)
        button.appendChild(avatar)
        button.appendChild(el("span", "check-in-bubble-text", text = checkInSummary(checkIn)))
        button.onClick { openCheckInDialog(buttonRootHost(), router, checkIn) }
        val remaining = checkIn.untilAt - num(js("Date.now()")).toLong()
        expiryTimers.add(window.asDynamic().setTimeout({ button.asDynamic().remove() }, remaining))
        return button
    }

    private fun buttonRootHost(): HTMLElement = root.asDynamic().parentElement.unsafeCast<HTMLElement>()
}

internal fun formatCheckInTime(at: Long): String {
    val saigonTime = js("new Date(at + 7 * 60 * 60 * 1000)")
    val hour = num(saigonTime.getUTCHours()).toInt()
    val minute = num(saigonTime.getUTCMinutes()).toInt()
    val minutes = minute.toString().padStart(2, '0')
    if (Town.language == Language.VI) return "${hour.toString().padStart(2, '0')}:$minutes"
    val hour12 = (hour % 12).takeIf { it != 0 } ?: 12
    return "$hour12:$minutes ${if (hour < 12) "AM" else "PM"}"
}

internal fun openCheckInDialog(host: HTMLElement, router: HistoryRouter, checkIn: CheckInCard) {
    if (host.asDynamic().querySelector(".check-in-dialog-backdrop") != null) return
    val backdrop = el("div", "dialog-backdrop check-in-dialog-backdrop")
    val card = el("section", "dialog check-in-dialog")
    card.setAttribute("role", "dialog")
    card.setAttribute("aria-modal", "true")
    val heading = el("div", "check-in-dialog-heading")
    heading.appendChild(el("h2", text = Town.t("checkInDialogTitle")))
    heading.appendChild(el("button", "icon-btn", text = "×") { close ->
        close.setAttribute("type", "button")
        close.setAttribute("aria-label", Town.t("close"))
        close.onClick { backdrop.asDynamic().remove() }
    })
    card.appendChild(heading)
    val identity = el("div", "check-in-dialog-identity")
    identity.appendChild(avatar(checkIn))
    identity.appendChild(el("p", "check-in-dialog-summary", text = checkInSummary(checkIn)))
    card.appendChild(identity)
    val location = activityLocationBlock(
        host,
        router,
        checkIn.location,
        checkIn.locationId,
        checkIn.placeId,
        checkIn.lat,
        checkIn.lng,
    )
    card.appendChild(location.root)
    val self = checkIn.authorId == Town.me?.id
    if (!self) {
        val message = el("textarea", "check-in-message")
        message.setAttribute("maxlength", Limits.CHAT_MESSAGE_MAX.toString())
        message.setAttribute("placeholder", Town.t("checkInMessagePlaceholder", mapOf("name" to checkIn.authorName)))
        message.setAttribute("aria-label", Town.t("checkInMessagePlaceholder", mapOf("name" to checkIn.authorName)))
        card.appendChild(message)
        val send = el("button", "primary check-in-send", text = Town.t("sendMessage"))
        send.setAttribute("type", "button")
        send.onClick {
            val text = (message.asDynamic().value as? String).orEmpty().trim()
            if (text.isEmpty()) {
                message.asDynamic().focus()
                return@onClick
            }
            if (Town.me == null) {
                showAuthDialog(host, onSuccess = { send.asDynamic().click() })
                return@onClick
            }
            send.setAttribute("disabled", "disabled")
            val payload = js("({})")
            payload.userId = checkIn.authorId
            payload.text = text
            http("POST", "/api/chats", jsonStringify(payload)) { status, body ->
                if (status in 200..299) {
                    val chatId = parseJson(body).id as? String
                    if (!chatId.isNullOrBlank()) {
                        backdrop.asDynamic().remove()
                        router.navigate("/messages/$chatId")
                    }
                } else {
                    send.removeAttribute("disabled")
                    toast(host, errorText(body, status))
                }
            }
        }
        card.appendChild(send)
    }
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
    location.mapBox?.let {
        mountPlaceMap(it, checkIn.lat, checkIn.lng, checkIn.location, checkIn.placeId, zoomControl = false)
    }
}

private fun checkInFrom(node: dynamic): CheckInCard? {
    val authorId = (node.authorId as? String)?.takeIf { it.isNotBlank() } ?: return null
    val authorName = (node.authorName as? String)?.takeIf { it.isNotBlank() } ?: return null
    val location = (node.location as? String)?.takeIf { it.isNotBlank() } ?: return null
    if (node.lat == null || node.lat == undefined || node.lng == null || node.lng == undefined) return null
    val untilAt = num(node.untilAt).toLong()
    if (untilAt <= 0) return null
    return CheckInCard(
        postId = (node.postId as? String).orEmpty(),
        authorId = authorId,
        authorName = authorName,
        authorPhotoUrl = (node.authorPhotoUrl as? String)?.let(::prefixServerOrigin),
        location = location,
        placeId = (node.placeId as? String)?.takeIf { it.isNotBlank() },
        locationId = (node.locationId as? String)?.takeIf { it.isNotBlank() },
        lat = num(node.lat),
        lng = num(node.lng),
        untilAt = untilAt,
    )
}

private fun checkInSummary(checkIn: CheckInCard): String = Town.t(
    "checkInBubble",
    mapOf(
        "name" to checkIn.authorName,
        "location" to checkIn.location,
        "time" to formatCheckInTime(checkIn.untilAt),
    ),
)

private fun avatar(checkIn: CheckInCard): HTMLElement {
    val face = el("span", "check-in-avatar", text = checkIn.authorName.take(1).uppercase())
    val photoUrl = checkIn.authorPhotoUrl
    if (!photoUrl.isNullOrBlank()) {
        face.textContent = ""
        val image = el("img", "check-in-avatar-image")
        image.setAttribute("src", photoUrl)
        image.setAttribute("alt", "")
        image.asDynamic().onerror = { face.textContent = checkIn.authorName.take(1).uppercase() }
        face.appendChild(image)
    }
    return face
}