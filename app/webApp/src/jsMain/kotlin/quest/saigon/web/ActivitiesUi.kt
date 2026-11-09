package quest.saigon.web

import quest.saigon.Limits
import web.html.HTMLElement
import web.window.WindowTarget
import web.window._blank
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
    onSaved: ((dynamic) -> Unit)? = null,
) {
    if (Town.me == null) {
        showAuthDialog(host, onSuccess = {
            activityEditorDialog(host, router, locationName, locationId, placeId, lat, lng, activity, onSaved)
        })
        return
    }
    if (host.asDynamic().querySelector(".activity-editor-backdrop") != null) return
    var current: dynamic = activity
    var editing = (current.id as? String)?.isNotBlank() == true
    val backdrop = el("div", "dialog-backdrop activity-editor-backdrop")
    val card = el("form", "dialog activity-editor-dialog")
    val title = el("h2", text = Town.t(if (editing) "editActivity" else "createActivity"))
    card.appendChild(title)
    var emojiTimer: dynamic = null
    var emojiTicket = 0
    var translationPollTimer: dynamic = null
    var translationPollGeneration = 0
    var editorClosed = false
    var saved = false
    fun closeEditor() {
        if (editorClosed) return
        editorClosed = true
        if (emojiTimer != null) window.asDynamic().clearTimeout(emojiTimer)
        emojiTimer = null
        emojiTicket++
        if (translationPollTimer != null) window.asDynamic().clearTimeout(translationPollTimer)
        translationPollTimer = null
        translationPollGeneration++
        backdrop.asDynamic().remove()
        if (saved && onSaved == null) refresh(router)
    }
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
    val translationReview = el("section", "activity-translation-review")
    val englishFields = el("section", "activity-translation-locale")
    englishFields.appendChild(el("h3", text = Town.t("languageEn")))
    val englishName = field(Town.t("activityName"), "text", "")
    englishName.input.setAttribute("maxlength", Limits.ACTIVITY_NAME_MAX.toString())
    val englishDescription = area(Town.t("activityDescription"), "", Limits.ACTIVITY_DESCRIPTION_MAX)
    englishFields.appendChild(englishName.root)
    englishFields.appendChild(englishDescription.root)
    val vietnameseFields = el("section", "activity-translation-locale")
    vietnameseFields.appendChild(el("h3", text = Town.t("languageVi")))
    val vietnameseName = field(Town.t("activityName"), "text", "")
    vietnameseName.input.setAttribute("maxlength", Limits.ACTIVITY_NAME_MAX.toString())
    val vietnameseDescription = area(Town.t("activityDescription"), "", Limits.ACTIVITY_DESCRIPTION_MAX)
    vietnameseFields.appendChild(vietnameseName.root)
    vietnameseFields.appendChild(vietnameseDescription.root)
    val translationStatusHint = el("p", "hint activity-translation-status")
    translationStatusHint.asDynamic().hidden = true
    translationReview.appendChild(englishFields)
    translationReview.appendChild(vietnameseFields)
    translationReview.appendChild(translationStatusHint)
    translationReview.asDynamic().hidden = !editing
    card.appendChild(translationReview)
    val translationInputs = listOf(
        englishName.input,
        englishDescription.input,
        vietnameseName.input,
        vietnameseDescription.input,
    )
    var translationState = ""
    var translationsEdited = false
    fun translationValue(translations: dynamic, locale: String, fieldName: String): String {
        if (translations == null || translations == undefined) return ""
        val localized = translations[locale]
        if (localized == null || localized == undefined) return ""
        return (localized[fieldName] as? String).orEmpty()
    }
    fun applyTranslationState(record: dynamic) {
        val translations = record.translations
        if (translations == null || translations == undefined) {
            translationState = ""
            translationStatusHint.asDynamic().hidden = true
            translationInputs.forEach { it.removeAttribute("disabled") }
            return
        }
        translationState = (translations.status as? String).orEmpty()
        if (translationState == "ready" || translationState == "pending") {
            englishName.input.asDynamic().value = translationValue(translations, "en", "name")
            englishDescription.input.asDynamic().value = translationValue(translations, "en", "description")
            vietnameseName.input.asDynamic().value = translationValue(translations, "vi", "name")
            vietnameseDescription.input.asDynamic().value = translationValue(translations, "vi", "description")
        }
        val pending = translationState == "pending"
        translationInputs.forEach { input ->
            if (pending) input.setAttribute("disabled", "disabled") else input.removeAttribute("disabled")
        }
        when (translationState) {
            "pending" -> {
                translationStatusHint.textContent = Town.t("translating")
                translationStatusHint.asDynamic().hidden = false
            }
            "failed" -> {
                translationStatusHint.textContent = Town.t("translationFailed")
                translationStatusHint.asDynamic().hidden = false
            }
            else -> translationStatusHint.asDynamic().hidden = true
        }
    }
    translationInputs.forEach { input ->
        input.asDynamic().oninput = { translationsEdited = true }
    }
    if (editing) applyTranslationState(current)
    fun pollTranslations(attempt: Int, generation: Int) {
        if (editorClosed || !editing || generation != translationPollGeneration) return
        if (attempt >= 40) {
            translationState = "failed"
            translationStatusHint.textContent = Town.t("translationFailed")
            translationStatusHint.asDynamic().hidden = false
            translationInputs.forEach { it.removeAttribute("disabled") }
            return
        }
        val activityId = current.id as? String ?: return
        http("GET", "/api/activities/${encodeQuery(activityId)}") { status, body ->
            if (editorClosed || generation != translationPollGeneration) return@http
            if (status == 200) {
                current = parseJson(body)
                applyTranslationState(current)
                if (translationState == "pending") {
                    translationPollTimer = window.asDynamic().setTimeout({
                        translationPollTimer = null
                        pollTranslations(attempt + 1, generation)
                    }, 1_500)
                } else {
                    translationPollTimer = null
                }
            } else {
                translationPollTimer = window.asDynamic().setTimeout({
                    translationPollTimer = null
                    pollTranslations(attempt + 1, generation)
                }, 1_500)
            }
        }
    }
    fun stopTranslationPolling() {
        if (translationPollTimer != null) window.asDynamic().clearTimeout(translationPollTimer)
        translationPollTimer = null
        translationPollGeneration++
    }
    fun startTranslationPolling() {
        stopTranslationPolling()
        pollTranslations(0, translationPollGeneration)
    }
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
        button.onClick { closeEditor() }
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
        val reviewVisible = translationReview.asDynamic().hidden != true
        if (reviewVisible && translationState != "pending" && (translationState == "ready" || translationsEdited)) {
            val english = js("({})")
            english.name = englishName.value()
            english.description = englishDescription.value()
            val vietnamese = js("({})")
            vietnamese.name = vietnameseName.value()
            vietnamese.description = vietnameseDescription.value()
            val overrides = js("({})")
            overrides.en = english
            overrides.vi = vietnamese
            payload.translations = overrides
        }
        if (!editing) {
            payload.locationName = locationName
            payload.locationId = locationId.orEmpty()
            payload.placeId = placeId.orEmpty()
            if (lat != null) payload.lat = lat
            if (lng != null) payload.lng = lng
            payload.interested = interested.asDynamic().checked == true
        }
        val creating = !editing
        val url = if (editing) "/api/activities/${current.id}" else "/api/activities"
        http(if (editing) "PATCH" else "POST", url, jsonStringify(payload)) { status, body ->
            if (status == 200 || status == 201) {
                stopEmojiGeneration()
                current = parseJson(body)
                saved = true
                toast(host, Town.t("activitySaved"))
                onSaved?.invoke(current)
                if (creating) {
                    closeEditor()
                    return@http
                }
                emojiManual = true
                name.input.asDynamic().value = (current.name as? String).orEmpty()
                description.input.asDynamic().value = (current.description as? String).orEmpty()
                emoji.input.asDynamic().value = (current.emoji as? String).orEmpty()
                submit.removeAttribute("disabled")
                translationReview.asDynamic().hidden = false
                translationsEdited = false
                applyTranslationState(current)
                if (translationState == "pending") startTranslationPolling() else stopTranslationPolling()
            } else {
                submit.removeAttribute("disabled")
                toast(host, errorText(body, status))
            }
        }
    })
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { closeEditor() }
    host.appendChild(backdrop)
    if (editing && translationState != "ready") startTranslationPolling()
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

internal fun miniProfileActivities(userId: String, router: HistoryRouter, host: HTMLElement): HTMLElement {
    val section = el("section", "mini-activities")
    section.asDynamic().hidden = true
    http("GET", "/api/users/${encodeQuery(userId)}/activities?limit=${Limits.MINI_PROFILE_ACTIVITIES}") { status, text ->
        if (status != 200 || section.asDynamic().isConnected == false) return@http
        val parsed = parseJson(text)
        val items: dynamic = parsed.activities
        val count = jsLen(items)
        if (count == 0) return@http
        section.asDynamic().hidden = false
        section.appendChild(
            el(
                "h3",
                "profile-sheet-count",
                text = Town.t("activityCount", mapOf("count" to count.toString())),
            ),
        )
        val list = el("div", "activity-list mini-activity-list")
        for (i in 0 until count) list.appendChild(activityCard(items[i], host, router))
        section.appendChild(list)
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

internal fun activityCard(activity: dynamic, host: HTMLElement, router: HistoryRouter, manage: Boolean = false, showLocation: Boolean = true): HTMLElement {
    val card = el("article", if (manage) "activity-card activity-manage-card" else "activity-card")
    val open = {
        val id = activity.id as? String ?: ""
        if (id.isNotBlank()) openActivitySheet(host, router, id)
    }
    card.appendChild(el("span", "activity-emoji", text = (activity.emoji as? String)?.ifBlank { "✨" } ?: "✨"))
    val copy = el("div", "activity-card-copy")
    copy.appendChild(el("strong", text = localizedActivityText(activity, "name")))
    val locationName = activity.locationName as? String ?: ""
    if (showLocation && locationName.isNotBlank()) {
        copy.appendChild(el("p", "activity-location", text = "📍 $locationName"))
    }
    val hostNode: dynamic = activity.host
    if (hostNode != null && hostNode != undefined) {
        val hostName = (hostNode.name as? String)?.takeIf { it.isNotBlank() }
        if (hostName != null) {
            copy.appendChild(el("p", "activity-card-host", text = hostLabel(hostNode.id as? String, hostName)))
        }
    }
    val description = localizedActivityText(activity, "description")
    if (description.isNotBlank()) copy.appendChild(el("p", "hint", text = description))
    activityScheduleSummary(activity)?.let { summary ->
        copy.appendChild(el("p", "activity-schedule-summary", text = summary))
    }
    card.appendChild(copy)
    val interest = interestButton(activity)
    stopClick(interest) {
        toggleActivityInterest(host, activity, interest) {
            card.asDynamic().replaceWith(activityCard(activity, host, router, manage, showLocation))
        }
    }
    card.appendChild(interest)
    activityCardInterested(activity)?.let(card::appendChild)
    activityCardPhotos(activity)?.let(card::appendChild)
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
                ) { updated ->
                    card.asDynamic().replaceWith(activityCard(updated, host, router, manage, showLocation))
                }
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

private fun activityCardInterested(activity: dynamic): HTMLElement? {
    val people: dynamic = activity.interestedPeople
    val count = jsLen(people)
    if (count == 0) return null
    val row = el("div", "activity-card-interested")
    val faces = el("div", "activity-card-interested-faces")
    val shownCount = minOf(count, Limits.ACTIVITY_CARD_INTERESTED_LIMIT)
    for (i in 0 until shownCount) {
        val person = people[i]
        val name = person.name as? String ?: ""
        val photoUrl = person.photoUrl as? String
        if (!photoUrl.isNullOrBlank()) {
            val face = el("img", "face")
            face.setAttribute("src", photoUrl)
            face.setAttribute("alt", "")
            faces.appendChild(face)
        } else {
            faces.appendChild(el("span", "face initial", text = name.take(1).uppercase()))
        }
    }
    row.appendChild(faces)
    val names = (0 until minOf(count, 2)).mapNotNull { people[it].name as? String }
    val totalInterested = maxOf(num(activity.interestedCount).toInt(), count)
    val summary = when {
        names.size == 2 && totalInterested > 2 -> Town.t(
            "peopleNamesAndMore",
            mapOf("first" to names[0], "second" to names[1], "count" to (totalInterested - 2).toString()),
        )
        else -> names.joinToString(", ")
    }
    if (summary.isNotBlank()) row.appendChild(el("span", "activity-card-interested-names", text = summary))
    return row
}

private fun activityCardPhotos(activity: dynamic): HTMLElement? {
    val photos: dynamic = activity.photoUrls
    val count = minOf(jsLen(photos), Limits.ACTIVITY_CARD_PHOTO_LIMIT)
    if (count == 0) return null
    val row = el("div", "activity-card-photos")
    for (i in 0 until count) {
        val photo = el("img", "activity-card-photo")
        photo.setAttribute("src", photos[i] as? String ?: continue)
        photo.setAttribute("alt", "")
        photo.setAttribute("loading", "lazy")
        row.appendChild(photo)
    }
    return row
}

private fun activityScheduleSummary(activity: dynamic): String? {
    val slots = readSchedule(activity.schedule)
    if (slots.isEmpty()) return null
    return slots.groupBy { it.startMinutes to it.endMinutes }.entries.joinToString(" · ") { (time, days) ->
        val dayNames = days.joinToString(", ") { Town.t(weekdayKey(it.dayOfWeek)) }
        "$dayNames ${formatMinutes(time.first)}–${formatMinutes(time.second)}"
    }
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
            activityEditorDialog(host, router, locationName, locationId, placeId, lat, lng, draft) { _ ->
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
        for (i in 0 until count) list.appendChild(activityCard(items[i], host, router, showLocation = false))
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
    val name = localizedActivityText(activity, "name")
    val activityId = activity.id as? String ?: return
    val emoji = (activity.emoji as? String)?.ifBlank { "✨" } ?: "✨"
    val chrome = SheetChrome("comment-sheet activity-sheet", "$emoji $name")
    val body = el("div", "activity-sheet-body")
    val locationName = activity.locationName as? String ?: ""
    val placeId = (activity.placeId as? String)?.takeIf { it.isNotBlank() }
    val locationId = (activity.locationId as? String)?.takeIf { it.isNotBlank() }
    val lat = if (activity.lat == null || activity.lat == undefined) null else num(activity.lat)
    val lng = if (activity.lng == null || activity.lng == undefined) null else num(activity.lng)
    val mapBox = if (locationName.isNotBlank() && lat != null && lng != null) {
        el("div", "activity-location-map")
    } else {
        null
    }
    val description = localizedActivityText(activity, "description")
    val detailsHeader = el("div", "activity-details-header")
    detailsHeader.appendChild(el("h3", "activity-section-title", text = Town.t("details")))
    detailsHeader.appendChild(copyLinkIconButton(host, "/activity/$activityId", "product-link-copy"))
    body.appendChild(detailsHeader)
    if (description.isNotBlank()) {
        body.appendChild(linkedParagraph(description, "product-detail-description"))
    }
    body.appendChild(
        userPhotosSection(
            host,
            "/api/activities/$activityId/photos",
            "/api/activities/$activityId/photos",
            emptyMap(),
        ) { photoId -> "/api/activities/$activityId/photos/${encodeQuery(photoId)}" },
    )
    if (locationName.isNotBlank()) {
        val section = el("section", "activity-location-section")
        section.appendChild(el("h3", "activity-section-title", text = Town.t("location")))
        val row = el("div", "activity-location-row")
        if (mapBox != null) row.appendChild(mapBox)
        val copy = el("div", "activity-location-copy")
        val place = el("button", "text-button activity-location", text = locationName)
        place.setAttribute("type", "button")
        place.onClick {
            openLocationSheet(
                host,
                router,
                locationName,
                placeId,
                lat,
                lng,
                customLocationId = locationId,
            )
        }
        copy.appendChild(place)
        val directions = el("button", "primary activity-location-directions", text = Town.t("directions"))
        directions.setAttribute("type", "button")
        directions.onClick { window.open(mapsUrl(locationName, placeId, lat, lng), WindowTarget._blank) }
        copy.appendChild(directions)
        row.appendChild(copy)
        section.appendChild(row)
        body.appendChild(section)
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
    chrome.open(host) {
        if (mapBox != null) mountPlaceMap(mapBox, lat, lng, locationName, placeId, zoomControl = false)
    }
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
    fun paintDayRow(check: HTMLElement, start: HTMLElement, end: HTMLElement) {
        val enabled = check.asDynamic().checked == true
        if (enabled) {
            start.removeAttribute("disabled")
            end.removeAttribute("disabled")
            start.asDynamic().style.opacity = "1"
            end.asDynamic().style.opacity = "1"
        } else {
            start.setAttribute("disabled", "disabled")
            end.setAttribute("disabled", "disabled")
            start.asDynamic().style.opacity = "0.35"
            end.asDynamic().style.opacity = "0.35"
        }
    }
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
        check.asDynamic().onchange = { paintDayRow(check, start, end) }
        paintDayRow(check, start, end)
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
            built.forEach { (_, check, times) ->
                check.asDynamic().checked = false
                paintDayRow(check, times.first, times.second)
            }
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
            paintDayRow(check, times.first, times.second)
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
