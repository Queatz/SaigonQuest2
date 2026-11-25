package quest.saigon.web

import web.html.HTMLElement

internal fun openWantSheet(host: HTMLElement, wantId: String, router: HistoryRouter) {
    if (wantId.isBlank() || host.asDynamic().querySelector(".want-sheet") != null) return
    val chrome = SheetChrome("comment-sheet activity-sheet want-sheet", Town.t("wantAttachment"))
    val body = el("div", "activity-sheet-body")
    body.appendChild(el("h3", "activity-section-title", text = Town.t("peopleWantThis")))
    val people = el("div", "member-list activity-people")
    body.appendChild(people)
    val selected = mutableSetOf<String>()
    val dock = el("div", "product-buy-dock")
    val chat = el("button", "primary", text = Town.t("openChat"))
    chat.setAttribute("type", "button")
    dock.appendChild(chat)

    fun paintChat() {
        val others = selected.filter { it != Town.me?.id }
        chat.textContent = Town.t(if (others.size <= 1) "openChat" else "startGroupChat")
        if (others.isEmpty()) chat.setAttribute("disabled", "disabled") else chat.removeAttribute("disabled")
    }

    chat.onClick {
        if (Town.me == null) {
            showAuthDialog(host, onSuccess = { chat.asDynamic().click() })
            return@onClick
        }
        val others = selected.filter { it != Town.me?.id }
        if (others.isEmpty()) return@onClick
        chat.setAttribute("disabled", "disabled")
        val payload = js("({})")
        val ids = js("[]")
        others.forEach { ids.push(it) }
        payload.userIds = ids
        http("POST", "/api/wants/${encodeQuery(wantId)}/chat", jsonStringify(payload)) { status, response ->
            if (status == 200 || status == 201) {
                val chatId = parseJson(response).id as? String
                if (!chatId.isNullOrBlank()) go(router, "/messages/$chatId")
            } else {
                chat.removeAttribute("disabled")
                toast(host, errorText(response, status))
            }
        }
    }
    paintChat()
    chrome.sheet.appendChild(body)
    chrome.sheet.appendChild(dock)
    chrome.open(host)

    people.appendChild(el("p", "hint", text = Town.t("loading")))
    http("GET", "/api/wants/${encodeQuery(wantId)}") { status, response ->
        if (chrome.sheet.asDynamic().isConnected == false) return@http
        if (status != 200) {
            people.clear()
            people.appendChild(el("p", "hint", text = Town.t("unavailable")))
            return@http
        }
        val want = parseJson(response)
        val name = (want.name as? String).orEmpty()
        val title = chrome.sheet.asDynamic().querySelector(".comment-bar h2")
        if (title != null && title != undefined) title.textContent = name
        val members = want.members
        val count = jsLen(members)
        people.clear()
        if (count == 0) {
            people.appendChild(el("p", "hint", text = Town.t("noPeople")))
            return@http
        }
        for (i in 0 until count) {
            val person = eventPersonFrom(members[i])
            val isSelf = person.id == Town.me?.id
            val row = el("div", if (person.id in selected) "attendee-row selected" else "attendee-row")
            val profile = el("button", "attendee-profile")
            profile.setAttribute("type", "button")
            val face = if (person.photoUrl.isNullOrBlank()) {
                el("span", "face initial", text = person.name.take(1).uppercase())
            } else {
                el("img", "face")
            }
            if (face.tagName.equals("IMG", ignoreCase = true)) {
                face.setAttribute("src", prefixServerOrigin(person.photoUrl.orEmpty()))
                face.setAttribute("alt", person.name)
                face.setAttribute("loading", "lazy")
            }
            profile.appendChild(face)
            val copy = el("span", "member-copy")
            copy.appendChild(el("strong", text = person.name))
            if (person.location.isNotBlank()) copy.appendChild(el("small", text = person.location))
            profile.appendChild(copy)
            profile.onClick {
                if (isSelf) return@onClick
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
            people.appendChild(row)
        }
        paintChat()
    }
}

private fun jsLen(value: dynamic): Int {
    if (value == null || value == undefined) return 0
    return value.length as? Int ?: 0
}