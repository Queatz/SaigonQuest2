package quest.saigon.web

import quest.saigon.CheckIns
import quest.saigon.Language
import quest.saigon.Limits
import web.html.HTMLElement
import web.window.window

internal data class CheckInCard(
    val postId: String,
    val message: String,
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
    private var refreshGeneration = 0
    private val expiryTimers = mutableListOf<dynamic>()

    init {
        root.setAttribute("aria-label", Town.t("checkInDialogTitle"))
        host.appendChild(root)
        refresh()
    }

    fun refresh() {
        if (closed) return
        expiryTimers.forEach { window.asDynamic().clearTimeout(it) }
        expiryTimers.clear()
        root.textContent = ""
        val generation = ++refreshGeneration
        http("GET", "/api/checkins") { status, body ->
            if (closed || generation != refreshGeneration || status != 200) return@http
            val items: dynamic = parseJson(body).checkIns
            val count = if (items == null || items == undefined) 0 else num(items.length).toInt()
            for (index in 0 until count) {
                val item: dynamic = js("items[index]")
                val checkIn = checkInCardFrom(item) ?: continue
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
        val button = createCheckInBubble(buttonRootHost(), router, checkIn, onChanged = { refresh() })
        button.asDynamic().style.setProperty("--check-in-row-offset", "${index % 3 * 64}px")
        button.asDynamic().style.animationDelay = "-${index * 2}s"
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

internal fun openCheckInDialog(
    host: HTMLElement,
    router: HistoryRouter,
    checkIn: CheckInCard,
    onChanged: () -> Unit = {},
) {
    if (host.asDynamic().querySelector(".check-in-dialog-backdrop") != null) return
    val backdrop = el("div", "dialog-backdrop check-in-dialog-backdrop")
    val card = el("section", "dialog check-in-dialog")
    card.setAttribute("role", "dialog")
    card.setAttribute("aria-modal", "true")
    var currentCheckIn = checkIn
    val heading = el("div", "check-in-dialog-heading")
    heading.appendChild(el("h2", text = Town.t("checkInDialogTitle")))
    heading.appendChild(el("button", "icon-btn dialog-close", text = "×") { close ->
        close.setAttribute("type", "button")
        close.setAttribute("aria-label", Town.t("close"))
        close.onClick { backdrop.asDynamic().remove() }
    })
    card.appendChild(heading)
    val identity = el("div", "check-in-dialog-identity")
    val profileAvatar = avatar(checkIn)
    profileAvatar.setAttribute("role", "button")
    profileAvatar.setAttribute("tabindex", "0")
    profileAvatar.setAttribute("aria-label", Town.t("openProfile"))
    fun openAuthorProfile() {
        backdrop.asDynamic().remove()
        openMiniProfile(host, checkIn.authorName, checkIn.authorPhotoUrl, userId = checkIn.authorId, router = router)
    }
    profileAvatar.asDynamic().addEventListener("click", { event: dynamic ->
        event.stopPropagation()
        openAuthorProfile()
    })
    profileAvatar.asDynamic().addEventListener("keydown", { event: dynamic ->
        val key = event.key as? String
        if (key == "Enter" || key == " ") {
            event.preventDefault()
            event.stopPropagation()
            openAuthorProfile()
        }
    })
    identity.appendChild(profileAvatar)
    val summary = el("p", "check-in-dialog-summary", text = checkInSummary(checkIn))
    identity.appendChild(summary)
    card.appendChild(identity)
    if (checkIn.message.isNotBlank()) {
        val postMessage = el("button", "check-in-dialog-post", text = checkIn.message)
        postMessage.setAttribute("type", "button")
        postMessage.onClick {
            backdrop.asDynamic().remove()
            router.navigate("/p/${checkIn.postId}")
        }
        card.appendChild(postMessage)
    }
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
    if (self) {
        val manage = el("section", "check-in-manage")
        manage.appendChild(el("p", "check-in-manage-label", text = Town.t("updateCheckIn")))
        val durations = el("div", "chip-row check-in-duration-options")
        val durationButtons = mutableListOf<HTMLElement>()
        var selectedCheckInMinutes: Int? = null
        val until = el(
            "p",
            "check-in-until",
            text = Town.t("checkInUntil", mapOf("time" to formatCheckInTime(checkIn.untilAt))),
        )
        val checkInAction = el("button", "secondary check-in-check-out", text = Town.t("checkOut"))
        checkInAction.setAttribute("type", "button")
        fun refreshManageControls() {
            durationButtons.forEachIndexed { index, button ->
                val selected = selectedCheckInMinutes == CheckIns.durationOptionsMinutes[index]
                button.asDynamic().classList.toggle("selected", selected)
                button.setAttribute("aria-pressed", selected.toString())
            }
            val untilAt = selectedCheckInMinutes?.let { minutes ->
                CheckIns.endAt(num(js("Date.now()")).toLong(), minutes)
            } ?: currentCheckIn.untilAt
            until.textContent = Town.t(
                "checkInUntil",
                mapOf("time" to formatCheckInTime(untilAt)),
            )
            checkInAction.textContent = Town.t(if (selectedCheckInMinutes == null) "checkOut" else "updateCheckIn")
        }
        for (minutes in CheckIns.durationOptionsMinutes) {
            val duration = el("button", "chip", text = Town.t(checkInDurationKey(minutes)))
            duration.setAttribute("type", "button")
            duration.setAttribute("aria-pressed", "false")
            duration.onClick {
                selectedCheckInMinutes = if (selectedCheckInMinutes == minutes) null else minutes
                refreshManageControls()
            }
            durationButtons.add(duration)
            durations.appendChild(duration)
        }
        manage.appendChild(durations)
        manage.appendChild(until)
        card.appendChild(manage)
        checkInAction.onClick {
            val minutes = selectedCheckInMinutes
            checkInAction.setAttribute("disabled", "disabled")
            durationButtons.forEach { it.setAttribute("disabled", "disabled") }
            if (minutes != null) {
                val payload = js("({})")
                payload.checkInMinutes = minutes
                http("PATCH", "/api/posts/${currentCheckIn.postId}/checkin", jsonStringify(payload)) { status, body ->
                    if (status in 200..299) {
                        currentCheckIn = currentCheckIn.copy(untilAt = num(parseJson(body).untilAt).toLong())
                        summary.textContent = checkInSummary(currentCheckIn)
                        selectedCheckInMinutes = null
                        refreshManageControls()
                        onChanged()
                    } else {
                        toast(host, errorText(body, status))
                    }
                    checkInAction.removeAttribute("disabled")
                    durationButtons.forEach { it.removeAttribute("disabled") }
                }
            } else {
                http("DELETE", "/api/posts/${currentCheckIn.postId}/checkin") { status, body ->
                    if (status in 200..299) {
                        backdrop.asDynamic().remove()
                        onChanged()
                    } else {
                        checkInAction.removeAttribute("disabled")
                        durationButtons.forEach { it.removeAttribute("disabled") }
                        toast(host, errorText(body, status))
                    }
                }
            }
        }
        card.appendChild(checkInAction)
    } else {
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

internal fun createCheckInBubble(
    host: HTMLElement,
    router: HistoryRouter,
    checkIn: CheckInCard,
    onChanged: () -> Unit = {},
    cssClass: String = "check-in-bubble",
): HTMLElement {
    val button = el("button", cssClass)
    button.setAttribute("type", "button")
    button.setAttribute("aria-label", listOf(checkInSummary(checkIn), checkIn.message).filter { it.isNotBlank() }.joinToString(". "))
    button.appendChild(avatar(checkIn))
    val text = el("span", "check-in-bubble-text")
    text.appendChild(el("span", "check-in-bubble-summary", text = checkInSummary(checkIn)))
    if (checkIn.message.isNotBlank()) {
        text.appendChild(el("span", "check-in-bubble-message", text = checkIn.message))
    }
    button.appendChild(text)
    button.onClick { openCheckInDialog(host, router, checkIn, onChanged) }
    return button
}

internal fun checkInCardFrom(node: dynamic): CheckInCard? {
    val authorId = (node.authorId as? String)?.takeIf { it.isNotBlank() } ?: return null
    val authorName = (node.authorName as? String)?.takeIf { it.isNotBlank() } ?: return null
    val location = (node.location as? String)?.takeIf { it.isNotBlank() } ?: return null
    if (node.lat == null || node.lat == undefined || node.lng == null || node.lng == undefined) return null
    val untilAt = num(node.untilAt).toLong()
    if (untilAt <= 0) return null
    return CheckInCard(
        postId = (node.postId as? String).orEmpty(),
        message = (node.message as? String).orEmpty(),
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

private fun checkInDurationKey(minutes: Int): String = when (minutes) {
    30 -> "checkIn30Minutes"
    60 -> "checkIn1Hour"
    90 -> "checkIn1AndHalfHours"
    120 -> "checkIn2Hours"
    180 -> "checkIn3Hours"
    240 -> "checkIn4Hours"
    300 -> "checkIn5Hours"
    360 -> "checkIn6Hours"
    else -> "checkInNow"
}

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