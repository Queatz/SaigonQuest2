package quest.saigon.web

import quest.saigon.Limits
import web.html.HTMLElement
import web.window.window

internal fun openActivitySheet(host: HTMLElement, router: HistoryRouter, activityId: String) {
    if (activityId.isBlank() || host.asDynamic().querySelector(".activity-sheet") != null) return
    http("GET", "/api/activities/${encodeQuery(activityId)}") { status, text ->
        if (status != 200) {
            toast(host, errorText(text, status))
            return@http
        }
        showActivitySheet(host, router, parseJson(text))
    }
}

internal fun activityEditorDialog(
    host: HTMLElement,
    router: HistoryRouter,
    locationName: String,
    locationId: String?,
    placeId: String?,
    lat: Double?,
    lng: Double?,
    activity: dynamic,
    onSaved: (() -> Unit)? = null,
) {
    if (Town.me == null) {
        showAuthDialog(host, onSuccess = {
            activityEditorDialog(host, router, locationName, locationId, placeId, lat, lng, activity, onSaved)
        })
        return
    }
    if (host.asDynamic().querySelector(".activity-editor-backdrop") != null) return
    val current: dynamic = activity
    val editing = (current.id as? String)?.isNotBlank() == true
    val backdrop = el("div", "dialog-backdrop activity-editor-backdrop")
    val card = el("form", "dialog activity-editor-dialog")
    card.appendChild(el("h2", text = Town.t(if (editing) "editActivity" else "createActivity")))
    val emoji = field(Town.t("activityEmoji"), "text", "")
    emoji.input.setAttribute("maxlength", "8")
    emoji.input.setAttribute("placeholder", "✨")
    val name = field(Town.t("activityName"), "text", "")
    name.input.setAttribute("maxlength", Limits.ACTIVITY_NAME_MAX.toString())
    val description = area(Town.t("activityDescription"), "", Limits.ACTIVITY_DESCRIPTION_MAX)
    if (editing) {
        emoji.input.asDynamic().value = (current.emoji as? String).orEmpty()
        name.input.asDynamic().value = (current.name as? String).orEmpty()
        description.input.asDynamic().value = (current.description as? String).orEmpty()
    } else {
        name.input.asDynamic().value = (current.name as? String).orEmpty()
    }
    card.appendChild(emoji.root)
    card.appendChild(name.root)
    card.appendChild(description.root)
    var emojiTimer: dynamic = null
    var emojiTicket = 0
    var emojiManual = editing
    fun stopEmojiGeneration() {
        if (emojiTimer != null) window.asDynamic().clearTimeout(emojiTimer)
        emojiTimer = null
        emojiTicket++
        emoji.input.setAttribute("placeholder", "✨")
    }
    fun scheduleEmoji() {
        if (emojiTimer != null) window.asDynamic().clearTimeout(emojiTimer)
        emojiTimer = null
        emojiTicket++
        emoji.input.setAttribute("placeholder", "✨")
        if (emojiManual) return
        val typed = name.value().trim()
        if (typed.length < 2) return
        val ticket = emojiTicket
        emojiTimer = window.asDynamic().setTimeout({
            emojiTimer = null
            if (ticket != emojiTicket || emojiManual) return@setTimeout
            emoji.input.setAttribute("placeholder", "…")
            val payload = js("({})")
            payload.name = typed
            http("POST", "/api/activities/emoji", jsonStringify(payload)) { status, body ->
                if (ticket != emojiTicket) return@http
                emoji.input.setAttribute("placeholder", "✨")
                if (emojiManual || emoji.value().isNotBlank() || status != 200) return@http
                val suggested = parseJson(body).emoji as? String ?: return@http
                if (suggested.isNotBlank()) emoji.input.asDynamic().value = suggested
            }
        }, 220)
    }
    name.input.asDynamic().oninput = { scheduleEmoji() }
    emoji.input.asDynamic().oninput = {
        if (emoji.value().isNotEmpty()) {
            emojiManual = true
            stopEmojiGeneration()
        } else {
            emojiManual = false
            scheduleEmoji()
        }
    }
    if (!editing && name.value().isNotBlank()) scheduleEmoji()

    val schedule = scheduleEditor(current)
    card.appendChild(schedule.root)

    var hostId = (current.hostUserId as? String)?.takeIf { it.isNotBlank() }
    val hostNode: dynamic = current.host
    var hostName = if (hostNode == null || hostNode == undefined) null else hostNode.name as? String
    val hostButton = el("button", "secondary", text = hostLabel(hostId, hostName))
    hostButton.setAttribute("type", "button")
    val clearHost = el("button", "text-button", text = Town.t("clearHost"))
    clearHost.setAttribute("type", "button")
    clearHost.asDynamic().hidden = hostId == null
    fun paintHost() {
        hostButton.textContent = hostLabel(hostId, hostName)
        clearHost.asDynamic().hidden = hostId == null
    }
    hostButton.onClick {
        pickActivityHost(host) { id, name ->
            hostId = id
            hostName = name
            paintHost()
        }
    }
    clearHost.onClick {
        hostId = null
        hostName = null
        paintHost()
    }
    card.appendChild(el("span", "field-label", text = Town.t("activityHostOptional")))
    card.appendChild(hostButton)
    card.appendChild(clearHost)

    val interestedBox = el("label", "switch-row activity-interest-switch")
    val interested = el("input")
    interested.setAttribute("type", "checkbox")
    interested.asDynamic().checked = true
    interestedBox.appendChild(interested)
    interestedBox.appendChild(el("span", "switch-ui"))
    interestedBox.appendChild(el("span", text = Town.t("imInterestedInActivity")))
    if (!editing) card.appendChild(interestedBox)

    val submit = el("button", "primary", text = Town.t(if (editing) "save" else "createActivity"))
    submit.setAttribute("type", "submit")
    card.appendChild(submit)
    card.appendChild(el("button", "text-button", text = Town.t("cancel")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    card.asDynamic().addEventListener("submit", { event: dynamic ->
        event.preventDefault()
        val slots = schedule.slots()
        if (slots == null || slots == undefined) {
            toast(host, Town.t("invalidActivitySchedule"))
            return@addEventListener
        }
        val cleanName = name.value().trim()
        if (cleanName.isEmpty()) {
            toast(host, Town.t("invalidActivityName"))
            return@addEventListener
        }
        submit.setAttribute("disabled", "disabled")
        val payload = js("({})")
        payload.emoji = emoji.value().trim()
        payload.name = cleanName
        payload.description = description.value()
        payload.hostUserId = hostId.orEmpty()
        payload.schedule = slots
        if (!editing) {
            payload.locationName = locationName
            payload.locationId = locationId.orEmpty()
            payload.placeId = placeId.orEmpty()
            if (lat != null) payload.lat = lat
            if (lng != null) payload.lng = lng
            payload.interested = interested.asDynamic().checked == true
        }
        val url = if (editing) "/api/activities/${current.id}" else "/api/activities"
        http(if (editing) "PATCH" else "POST", url, jsonStringify(payload)) { status, body ->
            if (status == 200 || status == 201) {
                backdrop.asDynamic().remove()
                toast(host, Town.t("activitySaved"))
                onSaved?.invoke() ?: refresh(router)
            } else {
                submit.removeAttribute("disabled")
                toast(host, errorText(body, status))
            }
        }
    })
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
}

internal fun activitiesPreview(shell: HTMLElement, router: HistoryRouter): HTMLElement {
    val section = el("section", "products-preview")
    val head = el("div", "section-head")
    head.appendChild(el("h3", text = Town.t("myActivities")))
    head.appendChild(el("button", "see-all", text = Town.t("seeAll")) { button ->
        button.setAttribute("type", "button")
        button.onClick { go(router, "/profile/activities") }
    })
    section.appendChild(head)
    val list = el("div", "activity-list")
    section.appendChild(list)
    http("GET", "/api/me/activities?limit=3") { status, text ->
        if (status != 200) {
            list.appendChild(el("p", "hint", text = Town.t("unavailable")))
            return@http
        }
        val items: dynamic = parseJson(text).activities
        val count = jsLen(items)
        if (count == 0) {
            list.appendChild(el("p", "hint", text = Town.t("noActivities")))
            return@http
        }
        for (i in 0 until count) list.appendChild(activityCard(items[i], shell, router))
    }
    return section
}

internal fun myActivitiesScreen(router: HistoryRouter): HTMLElement {
    val shell = el("section", "shell page-shell")
    shell.appendChild(pageHead(router, Town.t("myActivities"), showProfile = false))
    val body = el("div", "page-body")
    val list = el("div", "activity-list")
    body.appendChild(list)
    shell.appendChild(body)
    loadMyActivities(list, shell, router, null)
    return shell
}

internal fun activityCard(activity: dynamic, host: HTMLElement, router: HistoryRouter, manage: Boolean = false): HTMLElement {
    val card = el("article", if (manage) "activity-card activity-manage-card" else "activity-card")
    val open = {
        val id = activity.id as? String ?: ""
        if (id.isNotBlank()) openActivitySheet(host, router, id)
    }
    card.appendChild(el("span", "activity-emoji", text = (activity.emoji as? String)?.ifBlank { "✨" } ?: "✨"))
    val copy = el("div", "activity-card-copy")
    copy.appendChild(el("strong", text = activity.name as? String ?: ""))
    val description = activity.description as? String ?: ""
    if (description.isNotBlank()) copy.appendChild(el("p", "hint", text = description))
    card.appendChild(copy)
    val interest = interestButton(activity)
    stopClick(interest) { toggleActivityInterest(host, activity, interest, null) }
    card.appendChild(interest)
    card.onClick { open() }
    if (manage) {
        val actions = el("div", "product-actions")
        actions.appendChild(el("button", "tiny", text = Town.t("edit")) { button ->
            button.setAttribute("type", "button")
            stopClick(button) {
                activityEditorDialog(
                    host,
                    router,
                    activity.locationName as? String ?: "",
                    activity.locationId as? String,
                    activity.placeId as? String,
                    if (activity.lat == null || activity.lat == undefined) null else num(activity.lat),
                    if (activity.lng == null || activity.lng == undefined) null else num(activity.lng),
                    activity,
                ) { refresh(router) }
            }
        })
        actions.appendChild(el("button", "tiny danger", text = Town.t("delete")) { button ->
            button.setAttribute("type", "button")
            stopClick(button) {
                confirmDialog(host, Town.t("deleteActivityAsk"), Town.t("deleteActivityHint")) {
                    http("DELETE", "/api/activities/${activity.id}") { status, body ->
                        if (status == 200) {
                            card.asDynamic().remove()
                            if (host.asDynamic().querySelector(".activity-manage-card") == null) refresh(router)
                        } else toast(host, errorText(body, status))
                    }
                }
            }
        })
        card.appendChild(actions)
    }
    return card
}

internal fun loadLocationActivities(
    list: HTMLElement,
    host: HTMLElement,
    router: HistoryRouter,
    locationName: String,
    placeId: String?,
    locationId: String?,
    lat: Double?,
    lng: Double?,
) {
    list.clear()
    val id = if (placeId.isNullOrBlank()) "" else "&placeId=${encodeQuery(placeId)}"
    val customId = locationId?.takeIf { it.isNotBlank() }?.let { "&locationId=${encodeQuery(it)}" }.orEmpty()
    fun appendCreateComposer() {
        val composer = el("div", "activity-create-composer")
        val input = el("input", "activity-create-input")
        input.setAttribute("type", "text")
        input.setAttribute("placeholder", Town.t("whatDoPeopleDoHere"))
        input.setAttribute("maxlength", Limits.ACTIVITY_NAME_MAX.toString())
        val create = el("button", "primary activity-create-button", text = Town.t("createActivity"))
        create.setAttribute("type", "button")
        create.asDynamic().hidden = true
        input.asDynamic().oninput = {
            create.asDynamic().hidden = (input.asDynamic().value as? String).orEmpty().trim().isEmpty()
        }
        create.onClick {
            val activityName = (input.asDynamic().value as? String).orEmpty().trim()
            if (activityName.isEmpty()) return@onClick
            val draft = js("({})")
            draft.name = activityName
            activityEditorDialog(host, router, locationName, locationId, placeId, lat, lng, draft) {
                loadLocationActivities(list, host, router, locationName, placeId, locationId, lat, lng)
            }
        }
        composer.appendChild(input)
        composer.appendChild(create)
        list.appendChild(composer)
    }
    http("GET", "/api/activities?name=${encodeQuery(locationName)}$id$customId&limit=50") { status, text ->
        if (status != 200) {
            list.appendChild(el("p", "hint", text = Town.t("unavailable")))
            appendCreateComposer()
            return@http
        }
        val items: dynamic = parseJson(text).activities
        val count = jsLen(items)
        for (i in 0 until count) list.appendChild(activityCard(items[i], host, router))
        appendCreateComposer()
    }
}

internal fun loadDiscoverActivities(list: HTMLElement, host: HTMLElement, router: HistoryRouter) {
    list.clear()
    list.appendChild(el("p", "hint", text = Town.t("loading")))
    http("GET", "/api/activities?limit=50") { status, text ->
        list.clear()
        if (status != 200) {
            list.appendChild(el("p", "hint", text = Town.t("unavailable")))
            return@http
        }
        val items: dynamic = parseJson(text).activities
        val count = jsLen(items)
        if (count == 0) {
            list.appendChild(el("p", "hint", text = Town.t("noActivities")))
            return@http
        }
        for (i in 0 until count) list.appendChild(activityCard(items[i], host, router))
    }
}

private fun showActivitySheet(host: HTMLElement, router: HistoryRouter, activity: dynamic) {
    if (host.asDynamic().querySelector(".activity-sheet") != null) return
    val name = activity.name as? String ?: ""
    val activityId = activity.id as? String ?: return
    val chrome = SheetChrome("comment-sheet activity-sheet", name)
    val body = el("div", "activity-sheet-body")
    val title = el("div", "product-detail-title-row")
    title.appendChild(el("span", "activity-emoji", text = (activity.emoji as? String)?.ifBlank { "✨" } ?: "✨"))
    title.appendChild(el("h2", "product-detail-name", text = name))
    title.appendChild(copyLinkIconButton(host, "/activity/$activityId", "product-copy-link"))
    body.appendChild(title)
    val locationName = activity.locationName as? String ?: ""
    if (locationName.isNotBlank()) {
        val place = el("button", "text-button activity-location")
        place.setAttribute("type", "button")
        place.textContent = "📍 $locationName"
        place.onClick {
            openLocationSheet(
                host,
                router,
                locationName,
                (activity.placeId as? String)?.takeIf { it.isNotBlank() },
                if (activity.lat == null || activity.lat == undefined) null else num(activity.lat),
                if (activity.lng == null || activity.lng == undefined) null else num(activity.lng),
                customLocationId = (activity.locationId as? String)?.takeIf { it.isNotBlank() },
            )
        }
        body.appendChild(place)
    }
    val description = activity.description as? String ?: ""
    if (description.isNotBlank()) {
        body.appendChild(el("h3", "activity-section-title", text = Town.t("details")))
        body.appendChild(linkedParagraph(description, "product-detail-description"))
    }
    val scheduleSlots = readSchedule(activity.schedule)
    if (scheduleSlots.isNotEmpty()) {
        val schedule = el("section", "activity-schedule")
        schedule.appendChild(el("h3", text = Town.t("activitySchedule")))
        val table = el("table", "activity-schedule-table")
        val rows = el("tbody")
        scheduleSlots.forEach { slot ->
            val row = el("tr")
            row.appendChild(el("th", text = Town.t(weekdayKey(slot.dayOfWeek))))
            row.appendChild(el("td", text = "${formatMinutes(slot.startMinutes)} – ${formatMinutes(slot.endMinutes)}"))
            rows.appendChild(row)
        }
        table.appendChild(rows)
        schedule.appendChild(table)
        body.appendChild(schedule)
    }
    body.appendChild(el("h3", "activity-section-title", text = Town.t("peopleInterested")))
    val interest = interestButton(activity, regularSize = true)
    body.appendChild(interest)
    val people = el("div", "member-list activity-people")
    body.appendChild(people)
    val selected = mutableSetOf<String>()
    val hostId = (activity.hostUserId as? String)?.takeIf { it.isNotBlank() }
    if (hostId != null) selected += hostId
    val dock = el("div", "product-buy-dock")
    val chat = el("button", "primary", text = Town.t("openChat"))
    chat.setAttribute("type", "button")
    fun otherSelected(): List<String> = selected.filter { it != Town.me?.id }
    fun paintChat() {
        val others = otherSelected()
        chat.textContent = Town.t(if (others.size <= 1) "openChat" else "startGroupChat")
        if (others.isEmpty()) chat.setAttribute("disabled", "disabled") else chat.removeAttribute("disabled")
    }
    fun paintPeople() {
        people.clear()
        val items: dynamic = activity.interestedPeople
        val count = jsLen(items)
        for (i in 0 until count) {
            val person = eventPersonFrom(items[i])
            val isHost = hostId != null && person.id == hostId
            val isSelf = person.id == Town.me?.id
            if (isHost) selected += person.id
            else if (isSelf) selected.remove(person.id)
            val row = el("div", if (person.id in selected && (!isSelf || isHost)) "attendee-row selected" else "attendee-row")
            val profile = el("button", "attendee-profile")
            profile.setAttribute("type", "button")
            profile.appendChild(el("span", "face initial", text = person.name.take(1).uppercase()).also { face ->
                if (!person.photoUrl.isNullOrBlank()) {
                    face.asDynamic().className = "face"
                    face.textContent = ""
                    face.asDynamic().style.backgroundImage = "url(\"${person.photoUrl}\")"
                }
            })
            val copy = el("span", "member-copy")
            copy.appendChild(el("strong", text = person.name))
            if (isHost) copy.appendChild(el("small", text = Town.t("host")))
            else if (person.location.isNotBlank()) copy.appendChild(el("small", text = person.location))
            profile.appendChild(copy)
            profile.onClick {
                if (isHost || isSelf) return@onClick
                if (person.id in selected) {
                    selected.remove(person.id)
                    row.asDynamic().classList.remove("selected")
                } else {
                    selected += person.id
                    row.asDynamic().classList.add("selected")
                }
                paintChat()
            }
            row.appendChild(profile)
            val peek = el("button", "attendee-icon")
            peek.setAttribute("type", "button")
            peek.textContent = "👤"
            peek.onClick {
                openMiniProfile(host, person.name, person.photoUrl, person.location, person.intro, person.id, router)
            }
            row.appendChild(peek)
            people.appendChild(row)
        }
        paintChat()
    }
    paintPeople()
    interest.onClick {
        toggleActivityInterest(host, activity, interest) {
            val meId = Town.me?.id
            if (meId != null && meId != hostId) selected.remove(meId)
            paintPeople()
        }
    }
    chat.onClick {
        if (Town.me == null) {
            showAuthDialog(host, onSuccess = { chat.asDynamic().click() })
            return@onClick
        }
        val others = otherSelected()
        if (others.isEmpty()) return@onClick
        chat.setAttribute("disabled", "disabled")
        val payload = js("({})")
        val ids = js("[]")
        others.forEach { ids.push(it) }
        payload.userIds = ids
        http("POST", "/api/activities/$activityId/chat", jsonStringify(payload)) { status, body ->
            if (status == 200 || status == 201) {
                val chatId = parseJson(body).id as? String
                if (!chatId.isNullOrBlank()) go(router, "/messages/$chatId")
            } else {
                chat.removeAttribute("disabled")
                toast(host, errorText(body, status))
            }
        }
    }
    dock.appendChild(chat)
    chrome.sheet.appendChild(body)
    chrome.sheet.appendChild(dock)
    chrome.open(host)
}

private fun loadMyActivities(list: HTMLElement, shell: HTMLElement, router: HistoryRouter, cursor: String?) {
    val suffix = if (cursor.isNullOrBlank()) "" else "&cursor=${encodeQuery(cursor)}"
    http("GET", "/api/me/activities?limit=20$suffix") { status, text ->
        if (status != 200) {
            if (list.childNodes.length == 0) list.appendChild(el("p", "hint", text = Town.t("unavailable")))
            return@http
        }
        val parsed = parseJson(text)
        val items: dynamic = parsed.activities
        val count = jsLen(items)
        if (count == 0 && list.childNodes.length == 0) {
            list.appendChild(el("p", "hint", text = Town.t("noActivities")))
        }
        for (i in 0 until count) list.appendChild(activityCard(items[i], shell, router, manage = true))
        val next = parsed.nextCursor as? String
        if (!next.isNullOrBlank()) {
            list.appendChild(el("button", "text-button", text = Town.t("loadMore")) { button ->
                button.setAttribute("type", "button")
                button.onClick {
                    button.asDynamic().remove()
                    loadMyActivities(list, shell, router, next)
                }
            })
        }
    }
}

private fun interestButton(activity: dynamic, regularSize: Boolean = false): HTMLElement {
    val button = el("button", if (regularSize) "activity-interest-regular" else "tiny")
    button.setAttribute("type", "button")
    paintInterest(button, activity)
    return button
}

private fun paintInterest(button: HTMLElement, activity: dynamic) {
    val count = num(activity.interestedCount).toInt()
    button.textContent = if (activity.interested == true) {
        Town.t("interestedCount", mapOf("count" to count.toString()))
    } else {
        Town.t("imInterested")
    }
    val regularSize = button.asDynamic().classList.contains("activity-interest-regular") == true
    val style = when {
        regularSize && activity.interested == true -> "primary activity-interest-regular"
        regularSize -> "secondary activity-interest-regular"
        activity.interested == true -> "tiny selected"
        else -> "tiny"
    }
    button.setAttribute("class", style)
}

private fun toggleActivityInterest(host: HTMLElement, activity: dynamic, button: HTMLElement, onUpdated: (() -> Unit)? = null) {
    if (Town.me == null) {
        showAuthDialog(host, onSuccess = { toggleActivityInterest(host, activity, button, onUpdated) })
        return
    }
    val id = activity.id as? String ?: return
    val leaving = activity.interested == true
    button.setAttribute("disabled", "disabled")
    http("POST", "/api/activities/$id/${if (leaving) "leave" else "join"}") { status, body ->
        button.removeAttribute("disabled")
        if (status != 200) {
            toast(host, errorText(body, status))
            return@http
        }
        val updated = parseJson(body)
        activity.interested = updated.interested
        activity.interestedCount = updated.interestedCount
        activity.interestedPeople = updated.interestedPeople
        paintInterest(button, activity)
        onUpdated?.invoke()
    }
}

private fun weekdayKey(day: Int): String = when (day) {
    1 -> "weekdayMon"
    2 -> "weekdayTue"
    3 -> "weekdayWed"
    4 -> "weekdayThu"
    5 -> "weekdayFri"
    6 -> "weekdaySat"
    else -> "weekdaySun"
}

private fun formatMinutes(minutes: Int): String {
    val hours = (minutes / 60).coerceIn(0, 23)
    val mins = (minutes % 60).coerceIn(0, 59)
    return hours.toString().padStart(2, '0') + ":" + mins.toString().padStart(2, '0')
}

private fun parseTime(value: String): Int? {
    val parts = value.split(":")
    if (parts.size < 2) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    if (hours !in 0..23 || minutes !in 0..59) return null
    return hours * 60 + minutes
}

private data class ScheduleSlot(val dayOfWeek: Int, val startMinutes: Int, val endMinutes: Int)

private fun readSchedule(raw: dynamic): List<ScheduleSlot> {
    val slots = mutableListOf<ScheduleSlot>()
    if (raw == null || raw == undefined) return slots
    val length = raw.length as? Int ?: return slots
    for (i in 0 until length) {
        val item = raw[i]
        slots += ScheduleSlot(
            dayOfWeek = num(item.dayOfWeek).toInt(),
            startMinutes = num(item.startMinutes).toInt(),
            endMinutes = num(item.endMinutes).toInt(),
        )
    }
    return slots.sortedBy { it.dayOfWeek }
}

private class ScheduleEditor(val root: HTMLElement, val slots: () -> dynamic)

private fun scheduleEditor(activity: dynamic): ScheduleEditor {
    val root = el("div", "activity-schedule-editor")
    val existing = readSchedule(activity.schedule)
    var scheduled = existing.isNotEmpty()
    val always = el("button", if (!scheduled) "tiny selected" else "tiny", text = Town.t("alwaysAvailable"))
    val timed = el("button", if (scheduled) "tiny selected" else "tiny", text = Town.t("scheduled"))
    always.setAttribute("type", "button")
    timed.setAttribute("type", "button")
    val mode = el("div", "activity-schedule-mode")
    mode.appendChild(always)
    mode.appendChild(timed)
    root.appendChild(mode)
    val days = el("div", "activity-schedule-days")
    val masterStart = el("input")
    masterStart.setAttribute("type", "time")
    masterStart.asDynamic().value = "09:00"
    val masterEnd = el("input")
    masterEnd.setAttribute("type", "time")
    masterEnd.asDynamic().value = "17:00"
    val apply = el("button", "tiny", text = Town.t("applyTimesToAllDays"))
    apply.setAttribute("type", "button")
    val master = el("div", "activity-schedule-master")
    master.appendChild(masterStart)
    master.appendChild(masterEnd)
    master.appendChild(apply)
    val built = (1..7).map { day ->
        val slot = existing.firstOrNull { it.dayOfWeek == day }
        val row = el("label", "activity-day-row")
        val check = el("input")
        check.setAttribute("type", "checkbox")
        check.asDynamic().checked = slot != null
        val start = el("input")
        start.setAttribute("type", "time")
        start.asDynamic().value = formatMinutes(slot?.startMinutes ?: 540)
        val end = el("input")
        end.setAttribute("type", "time")
        end.asDynamic().value = formatMinutes(slot?.endMinutes ?: 1020)
        row.appendChild(check)
        row.appendChild(el("span", text = Town.t(weekdayKey(day))))
        row.appendChild(start)
        row.appendChild(end)
        days.appendChild(row)
        Triple(day, check, Pair(start, end))
    }
    fun paintMode() {
        if (scheduled) {
            always.asDynamic().classList.remove("selected")
            timed.asDynamic().classList.add("selected")
            master.asDynamic().hidden = false
            days.asDynamic().hidden = false
        } else {
            always.asDynamic().classList.add("selected")
            timed.asDynamic().classList.remove("selected")
            master.asDynamic().hidden = true
            days.asDynamic().hidden = true
            built.forEach { (_, check, _) -> check.asDynamic().checked = false }
        }
    }
    always.onClick {
        scheduled = false
        paintMode()
    }
    timed.onClick {
        scheduled = true
        paintMode()
    }
    apply.onClick {
        val start = masterStart.asDynamic().value as String
        val end = masterEnd.asDynamic().value as String
        built.forEach { (_, check, times) ->
            check.asDynamic().checked = true
            times.first.asDynamic().value = start
            times.second.asDynamic().value = end
        }
    }
    root.appendChild(master)
    root.appendChild(days)
    paintMode()
    return ScheduleEditor(root) {
        if (!scheduled) return@ScheduleEditor js("[]")
        val out = js("[]")
        built.forEach { (day, check, times) ->
            if (check.asDynamic().checked != true) return@forEach
            val start = parseTime(times.first.asDynamic().value as String)
            val end = parseTime(times.second.asDynamic().value as String)
            if (start == null || end == null || end <= start) return@ScheduleEditor null
            val slot = js("({})")
            slot.dayOfWeek = day
            slot.startMinutes = start
            slot.endMinutes = end
            out.push(slot)
        }
        if (out.length as Int == 0) return@ScheduleEditor null
        out
    }
}

private fun hostLabel(id: String?, name: String?): String {
    if (id.isNullOrBlank()) return Town.t("chooseHost")
    val me = Town.me
    if (me != null && me.id == id) return "${Town.t("activityHost")}: ${Town.t("me")}"
    return "${Town.t("activityHost")}: ${name ?: id}"
}

private fun pickActivityHost(host: HTMLElement, onPick: (String, String) -> Unit) {
    if (host.asDynamic().querySelector(".activity-host-picker") != null) return
    val backdrop = el("div", "dialog-backdrop activity-host-picker")
    val card = el("div", "dialog")
    card.appendChild(el("h2", text = Town.t("chooseHost")))
    val me = Town.me
    if (me != null) {
        card.appendChild(el("button", "chooser-row activity-host-me", text = Town.t("me")) { button ->
            button.setAttribute("type", "button")
            button.onClick {
                onPick(me.id, me.name)
                backdrop.asDynamic().remove()
            }
        })
    }
    val search = el("input")
    search.setAttribute("type", "search")
    search.setAttribute("placeholder", Town.t("searchPeople"))
    card.appendChild(search)
    val results = el("div", "place-results")
    card.appendChild(results)
    card.appendChild(el("button", "text-button", text = Town.t("close")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    var timer: dynamic = null
    search.asDynamic().oninput = {
        val query = (search.asDynamic().value as? String).orEmpty()
        if (timer != null) window.asDynamic().clearTimeout(timer)
        timer = window.asDynamic().setTimeout({
            results.clear()
            if (query.isBlank()) return@setTimeout
            http("GET", "/api/people?q=${encodeQuery(query)}") { status, body ->
                if (status != 200) return@http
                val people = parseJson(body).people
                val length = people?.length as? Int ?: 0
                if (length == 0) {
                    results.appendChild(el("p", "hint", text = Town.t("noPeople")))
                    return@http
                }
                for (i in 0 until length) {
                    val person = people[i]
                    val row = el("button", "chooser-row")
                    row.setAttribute("type", "button")
                    row.appendChild(el("strong", text = person.name as? String ?: ""))
                    row.onClick {
                        onPick(person.id as String, person.name as? String ?: "")
                        backdrop.asDynamic().remove()
                    }
                    results.appendChild(row)
                }
            }
        }, 220)
    }
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
}

private fun stopClick(node: HTMLElement, handler: () -> Unit) {
    node.asDynamic().addEventListener("click", { event: dynamic ->
        event.stopPropagation()
        handler()
    })
}

private fun jsLen(value: dynamic): Int {
    if (value == null || value == undefined) return 0
    return value.length as? Int ?: 0
}
