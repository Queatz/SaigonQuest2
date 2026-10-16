package quest.saigon.web

import quest.saigon.Limits
import web.dom.document
import web.html.HTMLElement
import web.window.window

private const val SAIGON_OFFSET = 7 * 60 * 60 * 1000L
internal const val EVENT_ICON = "📅"
internal const val PLACE_ICON = "📍"
internal const val PRICE_ICON = "💰"
private const val COPY_ICON =
    """<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="8" y="7" width="12" height="14" rx="2" fill="none" stroke="currentColor" stroke-width="2"/><path d="M6 17H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v1" fill="none" stroke="currentColor" stroke-width="2"/></svg>"""
private const val CHAT_ICON =
    """<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 5h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H9l-4 4v-4H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg>"""
private var mapsApi: dynamic = null
private var mapsLoadStarted = false
private var mapsImporting = false
private val mapsWaiters = mutableListOf<(Boolean) -> Unit>()

internal class PlacePick(
    var name: String = "",
    var placeId: String? = null,
    var lat: Double? = null,
    var lng: Double? = null,
)

internal fun eventsScreen(router: HistoryRouter): HTMLElement {
    val shell = el("section", "shell page-shell events-shell")
    shell.appendChild(pageHead(router, Town.t("events"), showProfile = true))
    val body = el("div", "page-body events-body")
    val mapBox = el("div", "events-map")
    val lists = el("div", "events-lists")
    val tabs = el("div", "chip-row events-tabs")
    val eventsChip = el("button", "chip selected", text = Town.t("events"))
    eventsChip.setAttribute("type", "button")
    val peopleChip = el("button", "chip", text = Town.t("people"))
    peopleChip.setAttribute("type", "button")
    val communitiesChip = el("button", "chip", text = Town.t("communities"))
    communitiesChip.setAttribute("type", "button")
    tabs.appendChild(eventsChip)
    tabs.appendChild(peopleChip)
    tabs.appendChild(communitiesChip)
    val split = el("div", "events-split")
    val eventCol = el("div", "events-col")
    val peopleCol = el("div", "events-col")
    val communityCol = el("div", "events-col")
    eventCol.appendChild(el("h3", text = Town.t("events")))
    peopleCol.appendChild(el("h3", text = Town.t("people")))
    communityCol.appendChild(el("h3", text = Town.t("communities")))
    val eventList = el("div", "event-list")
    val peopleList = el("div", "member-list")
    val communityList = el("div", "community-list")
    eventCol.appendChild(eventList)
    peopleCol.appendChild(peopleList)
    communityCol.appendChild(communityList)
    split.appendChild(eventCol)
    split.appendChild(peopleCol)
    split.appendChild(communityCol)
    lists.appendChild(tabs)
    lists.appendChild(split)
    body.appendChild(mapBox)
    body.appendChild(lists)
    shell.appendChild(body)
    fun selectTab(chip: HTMLElement) {
        eventsChip.asDynamic().classList.toggle("selected", chip === eventsChip)
        peopleChip.asDynamic().classList.toggle("selected", chip === peopleChip)
        communitiesChip.asDynamic().classList.toggle("selected", chip === communitiesChip)
        split.asDynamic().classList.toggle("show-people", chip === peopleChip)
        split.asDynamic().classList.toggle("show-communities", chip === communitiesChip)
    }
    eventsChip.onClick { selectTab(eventsChip) }
    peopleChip.onClick { selectTab(peopleChip) }
    communitiesChip.onClick { selectTab(communitiesChip) }
    loadGoogleMaps { }

    http("GET", "/api/events") { status, text ->
        if (status != 200) {
            eventList.appendChild(el("p", "hint", text = Town.t("unavailable")))
            return@http
        }
        val parsed = parseJson(text)
        val events = readPosts(parsed.events)
        if (events.isEmpty()) {
            eventList.appendChild(el("p", "hint", text = Town.t("noEvents")))
        } else {
            renderGroupedEvents(eventList, events, router)
        }
        val people = parsed.people
        val length = people?.length as? Int ?: 0
        if (length == 0) {
            peopleList.appendChild(el("p", "hint", text = Town.t("noMembers")))
        } else {
            for (i in 0 until length) {
                peopleList.appendChild(peopleRow(people[i], shell, router))
            }
        }
        mountEventsMap(mapBox, events)
    }
    fun loadCommunities() {
        http("GET", "/api/communities?limit=50") { status, body ->
            communityList.clear()
            if (status != 200) {
                communityList.appendChild(el("p", "hint", text = Town.t("unavailable")))
                return@http
            }
            val items = readCommunities(parseJson(body).communities)
            if (items.isEmpty()) {
                communityList.appendChild(el("p", "hint", text = Town.t("noCommunities")))
                return@http
            }
            items.forEach { community ->
                communityList.appendChild(
                    communityActionRow(shell, router, community, leaving = community.joined) { loadCommunities() },
                )
            }
        }
    }
    loadCommunities()
    return shell
}

internal fun eventsSummaryButton(router: HistoryRouter): HTMLElement {
    val button = el("button", "pill-btn events-summary")
    button.setAttribute("type", "button")
    button.setAttribute("aria-label", Town.t("openEvents"))
    button.setAttribute("hidden", "hidden")
    button.onClick { router.navigate("/events") }
    http("GET", "/api/events/summary") { status, body ->
        button.textContent = if (status == 200) {
            val parsed = parseJson(body)
            val today = num(parsed.today).toInt()
            val tomorrow = num(parsed.tomorrow).toInt()
            when {
                today == 1 -> Town.t("eventToday")
                today > 1 -> Town.t("eventsToday", mapOf("count" to today.toString()))
                tomorrow == 1 -> Town.t("eventTomorrow")
                tomorrow > 1 -> Town.t("eventsTomorrow", mapOf("count" to tomorrow.toString()))
                else -> Town.t("noUpcomingEvents")
            }
        } else {
            Town.t("noUpcomingEvents")
        }
        button.removeAttribute("hidden")
    }
    return button
}

internal fun eventCard(
    post: PostCard,
    host: HTMLElement,
    router: HistoryRouter,
    onUpdate: (PostCard) -> Unit,
): HTMLElement {
    val card = el("div", "event-card")
    fun paint(current: PostCard) {
        card.clear()
        val start = current.eventStartAt ?: return
        val details = el("div", "event-details")
        val whenRow = el("div", "event-when")
        whenRow.appendChild(el("span", "event-icon", text = EVENT_ICON))
        val whenCopy = el("div", "event-when-copy")
        whenCopy.appendChild(el("strong", text = eventScheduleLabel(start, current.eventEndAt)))
        val relative = eventRelativeLabel(start, current.eventEndAt)
        if (relative != null) whenCopy.appendChild(el("small", text = relative))
        whenRow.appendChild(whenCopy)
        details.appendChild(whenRow)
        if (current.location.isNotBlank()) {
            val place = el("button", "event-place")
            place.setAttribute("type", "button")
            place.appendChild(el("span", "event-icon", text = PLACE_ICON))
            place.appendChild(el("span", text = current.location))
            place.onClick { window.asDynamic().open(mapsUrl(current.location, current.placeId), "_blank") }
            details.appendChild(place)
        }
        val price = current.eventPrice
        val priceRow = el("span", "event-price")
        priceRow.appendChild(el("span", "event-icon", text = PRICE_ICON))
        priceRow.appendChild(el("span", text = if (price != null && price > 0) formatVnd(price) else Town.t("free")))
        details.appendChild(priceRow)
        val faces = el("button", "event-faces")
        faces.setAttribute("type", "button")
        fillAttendeeFaces(faces, current)
        faces.onClick { openAttendees(current, host, router) }
        details.appendChild(faces)
        card.appendChild(details)
        val join = el(
            "button",
            if (current.joined) "event-join is-leave" else "event-join",
            text = Town.t(if (current.joined) "leaveEvent" else "joinEvent"),
        )
        join.setAttribute("type", "button")
        join.onClick {
            if (Town.me == null) {
                showAuthDialog(host, onSuccess = { join.asDynamic().click() })
                return@onClick
            }
            join.setAttribute("disabled", "disabled")
            val action = if (current.joined) "leave" else "join"
            http("POST", "/api/posts/${current.id}/$action") { status, body ->
                join.removeAttribute("disabled")
                if (status != 200) {
                    toast(host, errorText(body, status))
                    return@http
                }
                val next = postFrom(parseJson(body))
                onUpdate(next)
                paint(next)
            }
        }
        card.appendChild(join)
    }
    paint(post)
    return card
}

internal fun openLocationPicker(
    host: HTMLElement,
    communityId: String?,
    current: PlacePick,
    onPick: (PlacePick) -> Unit,
) {
    if (host.asDynamic().querySelector(".location-picker") != null) return
    val backdrop = el("div", "dialog-backdrop location-picker")
    val card = el("div", "dialog location-dialog")
    card.setAttribute("role", "dialog")
    card.appendChild(el("h2", text = Town.t("pickLocation")))
    val search = el("input")
    search.setAttribute("type", "search")
    search.setAttribute("placeholder", Town.t("searchPlaces"))
    card.appendChild(search)
    val recentBox = el("div", "place-recent")
    val results = el("div", "place-results")
    val mapBox = el("div", "picker-map")
    card.appendChild(recentBox)
    card.appendChild(results)
    card.appendChild(mapBox)
    val actions = el("div", "dialog-actions")
    actions.appendChild(el("button", "text-button", text = Town.t("close")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    val confirm = el("button", "primary", text = Town.t("usePlace"))
    confirm.setAttribute("type", "button")
    confirm.asDynamic().disabled = true
    actions.appendChild(confirm)
    card.appendChild(actions)
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
    search.asDynamic().focus()

    var map: dynamic = null
    var marker: dynamic = null
    var pending: PlacePick? = null
    var selectedRow: HTMLElement? = null
    var request = 0
    var previewToken = 0
    fun pan(lat: Double, lng: Double) {
        if (map == null || map == undefined) return
        val pos = js("{}")
        pos.lat = lat
        pos.lng = lng
        map.setCenter(pos)
        map.setZoom(16)
        if (marker == null || marker == undefined) {
            val opts = js("{}")
            opts.map = map
            opts.position = pos
            marker = googleMarker(opts)
        } else {
            marker.setPosition(pos)
        }
    }

    loadGoogleMaps { ready ->
        if (!ready || backdrop.asDynamic().isConnected == false) return@loadGoogleMaps
        val opts = js("{}")
        opts.center = js("{lat:10.7769,lng:106.7009}")
        opts.zoom = 12
        opts.disableDefaultUI = true
        opts.zoomControl = true
        map = googleMap(mapBox, opts)
        val lat = current.lat
        val lng = current.lng
        if (lat != null && lng != null) pan(lat, lng)
    }

    fun markSelected(row: HTMLElement) {
        selectedRow?.asDynamic()?.classList?.remove("selected")
        row.asDynamic().classList.add("selected")
        selectedRow = row
    }

    fun preview(place: PlacePick, row: HTMLElement) {
        markSelected(row)
        confirm.asDynamic().disabled = true
        val token = ++previewToken
        fun ready() {
            if (token != previewToken) return
            val lat = place.lat
            val lng = place.lng
            if (lat != null && lng != null) pan(lat, lng)
            pending = place
            confirm.asDynamic().disabled = false
        }
        val id = place.placeId
        if (!id.isNullOrBlank() && (place.lat == null || place.lng == null)) {
            http("GET", "/api/places/details?id=${encodeQuery(id)}") { status, body ->
                if (status == 200) {
                    val parsed = parseJson(body)
                    place.name = (parsed.name as? String)?.takeIf { it.isNotBlank() } ?: place.name
                    place.lat = num(parsed.lat).takeIf { it != 0.0 }
                    place.lng = num(parsed.lng).takeIf { it != 0.0 }
                }
                ready()
            }
        } else {
            ready()
        }
    }

    confirm.onClick {
        val place = pending ?: return@onClick
        onPick(place)
        backdrop.asDynamic().remove()
    }

    fun placeButton(node: dynamic, recent: Boolean): HTMLElement {
        val name = (node.name as? String).orEmpty()
        val address = (node.address as? String).orEmpty()
        val row = el("button", if (recent) "chooser-row recent" else "chooser-row")
        row.setAttribute("type", "button")
        row.appendChild(el("strong", text = name))
        if (address.isNotBlank() && address != name) row.appendChild(el("small", text = address))
        row.onClick {
            preview(
                PlacePick(
                    name = name,
                    placeId = (node.placeId as? String)?.takeIf { it.isNotBlank() },
                    lat = num(node.lat).takeIf { it != 0.0 },
                    lng = num(node.lng).takeIf { it != 0.0 },
                ),
                row,
            )
        }
        return row
    }

    fun load(query: String) {
        val searching = query.isNotBlank()
        if (searching) {
            recentBox.clear()
            recentBox.asDynamic().hidden = true
        }
        val token = ++request
        val q = if (searching) "&q=${encodeQuery(query)}" else ""
        val community = if (communityId.isNullOrBlank()) "" else "&communityId=${encodeQuery(communityId)}"
        http("GET", "/api/places?$q$community") { status, body ->
            if (status != 200 || token != request) return@http
            val parsed = parseJson(body)
            recentBox.clear()
            if (searching) {
                recentBox.asDynamic().hidden = true
            } else {
                recentBox.asDynamic().hidden = false
                val recent = parsed.recent
                val recentLen = recent?.length as? Int ?: 0
                if (recentLen > 0) {
                    recentBox.appendChild(el("p", "hint", text = Town.t("recentPlaces")))
                    for (i in 0 until recentLen) recentBox.appendChild(placeButton(recent[i], true))
                }
            }
            results.clear()
            val places = parsed.places
            val placeLen = places?.length as? Int ?: 0
            for (i in 0 until placeLen) results.appendChild(placeButton(places[i], false))
        }
    }

    load("")
    var timer: dynamic = null
    search.asDynamic().oninput = {
        val query = (search.asDynamic().value as? String).orEmpty()
        if (query.isNotBlank()) {
            recentBox.clear()
            recentBox.asDynamic().hidden = true
        }
        if (timer != null) window.asDynamic().clearTimeout(timer)
        timer = window.asDynamic().setTimeout({ load(query) }, 220)
    }
}

internal fun composerEventFields(): ComposerEvent {
    val state = ComposerEvent()
    val box = el("div", "event-composer")
    val toggle = el("input")
    toggle.setAttribute("type", "checkbox")
    val row = el("label", "switch-row")
    row.appendChild(toggle)
    row.appendChild(el("span", "switch-ui"))
    row.appendChild(el("span", text = Town.t("addEvent")))
    val fields = el("div", "event-fields")
    fields.asDynamic().hidden = true
    val start = field(Town.t("eventStart"), "datetime-local", "")
    val end = field(Town.t("eventEnd"), "datetime-local", "")
    val price = field(Town.t("eventPrice"), "text", Town.t("eventPriceHint"))
    price.input.setAttribute("inputmode", "numeric")
    price.input.setAttribute("pattern", "[0-9]*")
    price.input.setAttribute("autocomplete", "off")
    fields.appendChild(start.root)
    fields.appendChild(end.root)
    fields.appendChild(price.root)
    toggle.asDynamic().onchange = {
        val on = toggle.asDynamic().checked == true
        fields.asDynamic().hidden = !on
        state.enabled = on
    }
    start.input.asDynamic().onchange = { state.startAt = epochFromLocalInput(start.value()) }
    start.input.asDynamic().oninput = { state.startAt = epochFromLocalInput(start.value()) }
    end.input.asDynamic().onchange = { state.endAt = epochFromLocalInput(end.value()) }
    end.input.asDynamic().oninput = { state.endAt = epochFromLocalInput(end.value()) }
    price.input.asDynamic().oninput = {
        val digits = price.value().filter { it.isDigit() }
        val amount = digits.toLongOrNull()?.coerceAtMost(Limits.EVENT_PRICE_MAX.toLong())
        val shown = amount?.toString().orEmpty()
        if (price.value() != shown) price.input.asDynamic().value = shown
        state.price = amount
    }
    box.appendChild(row)
    box.appendChild(fields)
    state.root = box
    return state
}

internal class ComposerEvent {
    var enabled: Boolean = false
    var startAt: Long? = null
    var endAt: Long? = null
    var price: Long? = null
    lateinit var root: HTMLElement
}

internal fun mapsUrl(location: String, placeId: String?): String {
    val query = encodeQuery(if (placeId.isNullOrBlank()) "$location ở gần Sài Gòn" else location)
    return if (placeId.isNullOrBlank()) {
        "https://www.google.com/maps/search/?api=1&query=$query"
    } else {
        "https://www.google.com/maps/search/?api=1&query=$query&query_place_id=${encodeQuery(placeId)}"
    }
}

internal fun formatVnd(amount: Long): String {
    val digits = amount.toString()
    val grouped = buildString {
        digits.forEachIndexed { index, char ->
            if (index > 0 && (digits.length - index) % 3 == 0) append('.')
            append(char)
        }
    }
    return "${grouped}₫"
}

internal fun eventScheduleLabel(startAt: Long, endAt: Long?): String {
    val startClock = formatEventClock(startAt)
    val startDay = eventDayHeading(startAt)
    val end = endAt ?: return "$startClock ($startDay)"
    val endClock = formatEventClock(end)
    return if (saigonDay(startAt) == saigonDay(end)) {
        "$startClock – $endClock ($startDay)"
    } else {
        "$startClock ($startDay) – $endClock (${eventDayHeading(end)})"
    }
}

internal fun eventRelativeLabel(startAt: Long, endAt: Long?): String? {
    val now = num(js("Date.now()")).toLong()
    if (saigonDay(startAt) != saigonDay(now)) return null
    val delta = startAt - now
    return when {
        delta >= 60 * 60 * 1000L -> {
            val hours = delta / (60 * 60 * 1000L)
            if (hours <= 1L) Town.t("startsInHour") else Town.t("startsInHours", mapOf("count" to hours.toString()))
        }
        delta > 60 * 1000L -> Town.t("startsInMinutes", mapOf("count" to (delta / (60 * 1000L)).toString()))
        delta > 0 -> Town.t("startsInMinutes", mapOf("count" to "1"))
        endAt == null || now < endAt -> Town.t("happeningNow")
        else -> null
    }
}

internal fun eventWhenLabel(startAt: Long, endAt: Long?): String =
    eventRelativeLabel(startAt, endAt) ?: eventScheduleLabel(startAt, endAt)

internal fun epochFromLocalInput(value: String): Long? {
    if (value.isBlank()) return null
    val parsed = js("Date.parse")(value) as Double
    if (parsed.isNaN()) return null
    return parsed.toLong()
}

private fun openAttendees(post: PostCard, host: HTMLElement, router: HistoryRouter) {
    if (host.asDynamic().querySelector(".attendee-sheet") != null) return
    val backdrop = el("div", "dialog-backdrop attendee-sheet")
    val card = el("div", "dialog")
    card.appendChild(el("h2", text = Town.t("attendees")))
    val list = el("div", "member-list")
    card.appendChild(list)
    card.appendChild(el("button", "text-button", text = Town.t("close")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
    http("GET", "/api/posts/${post.id}/attendees") { status, body ->
        if (status != 200) {
            list.appendChild(el("p", "hint", text = errorText(body, status)))
            return@http
        }
        val people = parseJson(body).attendees
        val length = people?.length as? Int ?: 0
        if (length == 0) {
            list.appendChild(el("p", "hint", text = Town.t("noMembers")))
            return@http
        }
        for (i in 0 until length) list.appendChild(attendeeRow(eventPersonFrom(people[i]), host, router))
    }
}

private fun attendeeRow(person: EventPerson, host: HTMLElement, router: HistoryRouter): HTMLElement {
    val row = el("div", "attendee-row")
    val profile = el("button", "attendee-profile")
    profile.setAttribute("type", "button")
    if (!person.photoUrl.isNullOrBlank()) {
        val face = el("img", "face")
        face.setAttribute("alt", "")
        face.setAttribute("src", person.photoUrl)
        profile.appendChild(face)
    } else {
        profile.appendChild(el("span", "face initial", text = person.name.take(1).uppercase()))
    }
    val copy = el("span", "member-copy")
    copy.appendChild(el("strong", text = person.name))
    if (person.location.isNotBlank()) copy.appendChild(el("small", text = person.location))
    profile.appendChild(copy)
    profile.onClick {
        openMiniProfile(host, person.name, person.photoUrl, person.location, person.intro, person.id, router)
    }
    row.appendChild(profile)
    if (!person.phone.isNullOrBlank()) {
        row.appendChild(
            iconAction(Town.t("copyPhone"), COPY_ICON) {
                val value = person.phone
                try {
                    window.asDynamic().navigator.clipboard.writeText(value)
                    toast(host, Town.t("phoneCopied"))
                } catch (_: Throwable) {
                    toast(host, Town.t("copyFailed"))
                }
            },
        )
    }
    if (!person.zaloUrl.isNullOrBlank()) {
        row.appendChild(
            iconAction(Town.t("zaloReply"), CHAT_ICON) {
                window.asDynamic().open(person.zaloUrl, "_blank")
            },
        )
    }
    return row
}

private fun iconAction(label: String, svg: String, onClick: () -> Unit): HTMLElement {
    val button = el("button", "attendee-icon")
    button.setAttribute("type", "button")
    button.setAttribute("aria-label", label)
    button.asDynamic().innerHTML = svg
    button.onClick(onClick)
    return button
}

private fun peopleRow(node: dynamic, host: HTMLElement, router: HistoryRouter): HTMLElement {
    val person = eventPersonFrom(node)
    val row = el("button", "member-row")
    row.setAttribute("type", "button")
    if (!person.photoUrl.isNullOrBlank()) {
        val face = el("img", "face")
        face.setAttribute("alt", "")
        face.setAttribute("src", person.photoUrl)
        row.appendChild(face)
    } else {
        row.appendChild(el("span", "face initial", text = person.name.take(1).uppercase()))
    }
    val copy = el("span", "member-copy")
    copy.appendChild(el("strong", text = person.name))
    if (person.location.isNotBlank()) copy.appendChild(el("small", text = person.location))
    row.appendChild(copy)
    row.onClick {
        openMiniProfile(host, person.name, person.photoUrl, person.location, person.intro, person.id, router)
    }
    return row
}

private fun renderGroupedEvents(list: HTMLElement, events: List<PostCard>, router: HistoryRouter) {
    var lastDay = ""
    events.forEach { post ->
        val start = post.eventStartAt ?: return@forEach
        val day = eventDayHeading(start)
        if (day != lastDay) {
            list.appendChild(el("h4", "event-day", text = day))
            lastDay = day
        }
        val row = el("button", "event-row")
        row.setAttribute("type", "button")
        row.appendChild(el("span", "event-icon", text = EVENT_ICON))
        val copy = el("span", "event-row-copy")
        copy.appendChild(el("strong", text = post.message.ifBlank { post.location }))
        copy.appendChild(el("small", text = "${eventScheduleLabel(start, post.eventEndAt)} · ${post.location}"))
        copy.appendChild(attendeeFaces(post))
        row.appendChild(copy)
        row.onClick { router.navigate("/p/${post.id}") }
        list.appendChild(row)
    }
}

private fun mountEventsMap(host: HTMLElement, events: List<PostCard>) {
    val marked = events.filter { it.lat != null && it.lng != null }
    fun tryMount() {
        if (host.asDynamic().isConnected == false) {
            window.asDynamic().requestAnimationFrame { tryMount() }
            return
        }
        loadGoogleMaps { ready ->
            if (!ready || host.asDynamic().isConnected == false) {
                if (host.asDynamic().isConnected != false && host.childElementCount == 0) {
                    host.appendChild(el("p", "hint", text = "📍 ${Town.t("events")}"))
                }
                return@loadGoogleMaps
            }
            val opts = js("{}")
            opts.center = js("{lat:10.7769,lng:106.7009}")
            opts.zoom = 12
            opts.disableDefaultUI = true
            opts.zoomControl = true
            val map = googleMap(host, opts)
            val bounds = googleBounds()
            marked.forEach { post ->
                val pos = js("{}")
                pos.lat = post.lat
                pos.lng = post.lng
                googleHtmlMarker(map, pos, eventMapBubble(post))
                bounds.extend(pos)
            }
            fun fit() {
                if (marked.size == 1) {
                    val only = js("{}")
                    only.lat = marked[0].lat
                    only.lng = marked[0].lng
                    map.setCenter(only)
                    map.setZoom(15)
                } else if (marked.size > 1) {
                    map.fitBounds(bounds)
                }
            }
            fun relayout() {
                val trigger = mapsEvent()?.trigger
                if (trigger != null && trigger != undefined) trigger(map, "resize")
                fit()
            }
            fit()
            window.asDynamic().requestAnimationFrame { relayout() }
            window.asDynamic().setTimeout({ relayout() }, 120)
        }
    }
    tryMount()
}

private fun loadGoogleMaps(done: (Boolean) -> Unit) {
    fun capture(lib: dynamic = null): Boolean {
        val fromWindow = window.asDynamic().google?.maps
        val windowReady = fromWindow != null && fromWindow != undefined && isJsFunction(fromWindow.Map)
        val fromLib = if (lib != null && lib != undefined && isJsFunction(lib.Map)) lib else null
        val ns = if (windowReady) fromWindow else fromLib
        if (ns == null) return false
        mapsApi = ns
        return true
    }
    fun complete(ok: Boolean) {
        if (mapsWaiters.isEmpty() && (ok || !mapsLoadStarted)) return
        if (!ok) {
            mapsLoadStarted = false
            mapsImporting = false
        }
        val waiters = mapsWaiters.toList()
        mapsWaiters.clear()
        waiters.forEach { it(ok) }
    }
    fun finish() {
        if (capture()) {
            complete(true)
            return
        }
        if (mapsImporting) return
        val maps = window.asDynamic().google?.maps
        val importer = maps?.importLibrary
        if (importer == null || importer == undefined) return
        mapsImporting = true
        val onOk: (dynamic) -> Unit = { lib ->
            mapsImporting = false
            complete(capture(lib) || capture())
        }
        val onErr: (dynamic) -> Unit = {
            mapsImporting = false
            complete(capture())
        }
        try {
            importer("maps").then(onOk, onErr)
        } catch (_: Throwable) {
            mapsImporting = false
            window.asDynamic().setTimeout({ finish() }, 50)
        }
    }
    if (capture()) {
        done(true)
        return
    }
    mapsWaiters += done
    if (mapsLoadStarted) {
        finish()
        return
    }
    mapsLoadStarted = true
    if (window.asDynamic().google?.maps != null && window.asDynamic().google.maps != undefined) {
        finish()
        return
    }
    http("GET", "/api/config") { status, body ->
        if (status != 200) {
            complete(false)
            return@http
        }
        val key = parseJson(body).mapsKey as? String
        if (key.isNullOrBlank()) {
            complete(false)
            return@http
        }
        window.asDynamic().gm_authFailure = {
            mapsApi = null
            complete(false)
        }
        window.asDynamic().__sqMapsReady = { finish() }
        val pending = document.asDynamic().querySelector("script[data-sq-maps]")
        if (pending != null && pending != undefined) {
            pending.addEventListener("load") { finish() }
            pending.addEventListener("error") { complete(false) }
            finish()
            return@http
        }
        val script = document.createElement("script")
        script.setAttribute("data-sq-maps", "1")
        script.setAttribute("async", "")
        script.setAttribute("referrerpolicy", "origin")
        script.setAttribute("src", "https://maps.googleapis.com/maps/api/js?key=$key&v=weekly&loading=async&callback=__sqMapsReady")
        script.asDynamic().onload = { finish() }
        script.asDynamic().onerror = { complete(false) }
        document.asDynamic().head.appendChild(script)
    }
}

private fun mapsEvent(): dynamic {
    val fromApi = mapsApi?.event
    if (fromApi != null && fromApi != undefined) return fromApi
    return window.asDynamic().google?.maps?.event
}

private fun isJsFunction(value: dynamic): Boolean {
    if (value == null || value == undefined) return false
    return js("typeof value === 'function'") == true
}

private fun googleMap(element: HTMLElement, opts: dynamic): dynamic {
    val ctor = mapsApi.Map
    return js("new ctor(element, opts)")
}

private fun eventMapBubble(post: PostCard): HTMLElement {
    val bubble = el("button", "event-map-bubble")
    bubble.setAttribute("type", "button")
    val name = post.message.ifBlank { post.location }
    bubble.setAttribute("aria-label", name)
    val title = el("span", "event-map-bubble-title")
    title.appendChild(el("span", "event-map-bubble-emoji", text = post.communityEmoji ?: EVENT_ICON))
    title.appendChild(el("strong", text = name))
    bubble.appendChild(title)
    val start = post.eventStartAt
    val price = post.eventPrice
    val priceText = if (price != null && price > 0) formatVnd(price) else Town.t("free")
    val meta = buildString {
        if (start != null) {
            append(formatEventClock(start))
            append(" · ")
        }
        append(PRICE_ICON)
        append(' ')
        append(priceText)
    }
    bubble.appendChild(el("span", "event-map-bubble-meta", text = meta))
    if (post.location.isNotBlank()) {
        val place = el("span", "event-map-bubble-place")
        place.appendChild(el("span", text = PLACE_ICON))
        place.appendChild(el("span", text = post.location))
        bubble.appendChild(place)
    }
    bubble.appendChild(attendeeFaces(post, "event-faces event-map-bubble-faces"))
    bubble.onClick {
        window.asDynamic().location.assign("/p/${post.id}")
    }
    return bubble
}

private fun attendeeFaces(post: PostCard, className: String = "event-faces"): HTMLElement {
    val faces = el("span", className)
    fillAttendeeFaces(faces, post)
    return faces
}

private fun fillAttendeeFaces(host: HTMLElement, post: PostCard) {
    val shown = post.attendees.take(5)
    shown.forEach { person ->
        if (!person.photoUrl.isNullOrBlank()) {
            val img = el("img", "face event-face")
            img.setAttribute("alt", "")
            img.setAttribute("src", person.photoUrl)
            host.appendChild(img)
        } else {
            host.appendChild(el("span", "face event-face initial", text = person.name.take(1).uppercase()))
        }
    }
    val count = post.attendeeCount.coerceAtLeast(shown.size)
    host.appendChild(el("span", text = if (count > 0) "${Town.t("attendees")} · $count" else Town.t("attendees")))
}

private fun googleHtmlMarker(map: dynamic, position: dynamic, content: HTMLElement): dynamic {
    val overlay = googleOverlay()
    overlay.onAdd = {
        val panes = overlay.getPanes()
        val pane = panes?.overlayMouseTarget ?: panes?.floatPane
        if (pane != null && pane != undefined) pane.appendChild(content)
        val prevent = mapsApi?.OverlayView?.preventMapHitsAndGesturesFrom
            ?: window.asDynamic().google?.maps?.OverlayView?.preventMapHitsAndGesturesFrom
        if (prevent != null && prevent != undefined) prevent(content)
    }
    overlay.draw = {
        val projection = overlay.getProjection()
        if (projection != null && projection != undefined) {
            val point = projection.fromLatLngToDivPixel(position)
            if (point != null && point != undefined) {
                val style = content.asDynamic().style
                style.left = "${num(point.x)}px"
                style.top = "${num(point.y)}px"
            }
        }
    }
    overlay.onRemove = {
        content.asDynamic().remove()
    }
    overlay.setMap(map)
    return overlay
}

private fun googleMarker(opts: dynamic): dynamic {
    val ctor = if (isJsFunction(mapsApi.Marker)) mapsApi.Marker else window.asDynamic().google.maps.Marker
    return js("new ctor(opts)")
}

private fun googleOverlay(): dynamic {
    val ctor = if (isJsFunction(mapsApi.OverlayView)) mapsApi.OverlayView else window.asDynamic().google.maps.OverlayView
    return js("new ctor()")
}

private fun googleBounds(): dynamic {
    val ctor = if (isJsFunction(mapsApi.LatLngBounds)) mapsApi.LatLngBounds else window.asDynamic().google.maps.LatLngBounds
    return js("new ctor()")
}

private fun saigonShiftedDate(millis: Long): dynamic {
    val at = millis.toDouble() + SAIGON_OFFSET.toDouble()
    return js("new Date(at)")
}

private fun eventDayHeading(millis: Long): String {
    val now = num(js("Date.now()")).toLong()
    val day = saigonDay(millis)
    return when (day) {
        saigonDay(now) -> Town.t("today")
        saigonDay(now + 24 * 60 * 60 * 1000L) -> Town.t("tomorrow")
        else -> formatEventDate(millis)
    }
}

private fun saigonDay(millis: Long): String {
    val shifted = saigonShiftedDate(millis)
    val year = num(shifted.getUTCFullYear()).toInt()
    val month = num(shifted.getUTCMonth()).toInt() + 1
    val day = num(shifted.getUTCDate()).toInt()
    return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

private fun formatEventClock(millis: Long): String {
    val shifted = saigonShiftedDate(millis)
    val hour = num(shifted.getUTCHours()).toInt()
    val minute = num(shifted.getUTCMinutes()).toInt()
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

private fun formatEventTime(millis: Long): String = "${formatEventDate(millis)} ${formatEventClock(millis)}"

private fun formatEventDate(millis: Long): String {
    val shifted = saigonShiftedDate(millis)
    val month = num(shifted.getUTCMonth()).toInt() + 1
    val day = num(shifted.getUTCDate()).toInt()
    return "$day/$month"
}

private fun readPosts(array: dynamic): List<PostCard> {
    val posts = mutableListOf<PostCard>()
    if (array == null || array == undefined) return posts
    val length = array.length as Int
    for (i in 0 until length) posts += postFrom(array[i])
    return posts
}
