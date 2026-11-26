package quest.saigon.web

import web.html.HTMLElement

internal data class WantCardData(
    val id: String,
    val name: String,
    val photoUrl: String? = null,
    val memberCount: Int = 0,
    val joined: Boolean = false,
    val members: List<DiscoverCardPerson> = emptyList(),
)

internal fun parseWantCardData(node: dynamic): WantCardData {
    val members = mutableListOf<DiscoverCardPerson>()
    val rawMembers = node.members
    val count = jsLen(rawMembers)
    for (i in 0 until count) {
        val m = rawMembers[i]
        members += DiscoverCardPerson(
            name = (m.name as? String).orEmpty(),
            photoUrl = (m.photoUrl as? String)?.takeIf { it.isNotBlank() },
        )
    }
    return WantCardData(
        id = (node.id as? String).orEmpty(),
        name = (node.name as? String).orEmpty(),
        photoUrl = (node.photoUrl as? String)?.takeIf { it.isNotBlank() },
        memberCount = num(node.memberCount).toInt(),
        joined = node.joined == true,
        members = members,
    )
}

internal fun wantNotebookCardElement(
    host: HTMLElement,
    want: WantCardData,
    router: HistoryRouter?,
    leaveOnly: Boolean = false,
    extraClass: String = "",
    onUpdated: ((WantCardData) -> Unit)? = null,
    onLeave: (((() -> Unit)) -> Unit)? = null,
    onRequireAuth: (() -> Unit)? = null,
    canClickCard: () -> Boolean = { true },
): HTMLElement {
    val classes = listOf("community-row", "want-notebook", extraClass)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    val row = el("div", classes)
    row.setAttribute("role", "button")
    row.setAttribute("tabindex", "0")
    row.asDynamic().addEventListener("click", { event: dynamic ->
        if (!canClickCard()) return@addEventListener
        if (event.target.closest("button") != null) return@addEventListener
        router?.let { openWantSheet(host, want.id, it, initialName = want.name, initialPhotoUrl = want.photoUrl) }
    })

    if (!want.photoUrl.isNullOrBlank()) {
        val photo = el("img", "want-notebook-photo")
        photo.setAttribute("src", prefixServerOrigin(want.photoUrl))
        photo.setAttribute("alt", "")
        photo.setAttribute("loading", "lazy")
        photo.setAttribute("draggable", "false")
        row.appendChild(photo)
    }

    val content = el("div", "want-notebook-content")
    val header = el("div", "community-row-header")
    val info = el("div", "community-row-info")
    info.appendChild(el("span", "want-notebook-name", text = want.name))

    val button = if (leaveOnly) {
        el("button", "tiny danger community-action", text = Town.t("leaveWant"))
    } else {
        el(
            "button",
            if (want.joined) "tiny selected community-action" else "tiny community-action",
            text = Town.t(if (want.joined) "wantJoined" else "wantJoin"),
        )
    }
    button.setAttribute("type", "button")
    button.asDynamic().addEventListener("click", { event: dynamic -> event.stopPropagation() })
    button.onClick {
        if (leaveOnly) {
            confirmDialog(
                host,
                Town.t("leaveWantAsk", mapOf("want" to want.name)),
                Town.t("leaveWantHint"),
                Town.t("leaveWant"),
            ) {
                button.setAttribute("disabled", "disabled")
                onLeave?.invoke { button.removeAttribute("disabled") }
            }
            return@onClick
        }
        if (Town.me == null) {
            showAuthDialog(host, onSuccess = { onRequireAuth?.invoke() })
            return@onClick
        }
        fun performToggle() {
            button.setAttribute("disabled", "disabled")
            val action = if (want.joined) "leave" else "join"
            http("POST", "/api/wants/${encodeQuery(want.id)}/$action") { status, body ->
                if (status == 200) {
                    onUpdated?.invoke(parseWantCardData(parseJson(body)))
                } else {
                    button.removeAttribute("disabled")
                    toast(host, errorText(body, status))
                }
            }
        }
        if (want.joined) {
            confirmDialog(
                host,
                Town.t("leaveWantAsk", mapOf("want" to want.name)),
                Town.t("leaveWantHint"),
                Town.t("leaveWant"),
            ) { performToggle() }
        } else {
            performToggle()
        }
    }

    header.appendChild(info)
    header.appendChild(button)
    content.appendChild(header)
    if (want.memberCount >= 2 && want.members.isNotEmpty()) {
        content.appendChild(discoverPeopleFaces(want.members))
    }
    row.appendChild(content)
    return row
}

internal fun openWantSheet(
    host: HTMLElement,
    wantId: String,
    router: HistoryRouter,
    initialName: String? = null,
    initialPhotoUrl: String? = null,
) {
    if (wantId.isBlank() || host.asDynamic().querySelector(".want-sheet") != null) return
    val chrome = SheetChrome(
        "comment-sheet activity-sheet want-sheet",
        initialName?.takeIf { it.isNotBlank() } ?: Town.t("wantAttachment"),
    )
    val body = el("div", "activity-sheet-body")
    val cover = el("div", "want-sheet-cover")
    cover.asDynamic().hidden = true
    body.appendChild(cover)

    fun setCover(url: String?) {
        val clean = url?.trim().orEmpty()
        cover.clear()
        if (clean.isEmpty()) {
            cover.asDynamic().hidden = true
        } else {
            val img = el("img", "want-sheet-cover-photo")
            img.setAttribute("src", prefixServerOrigin(clean))
            img.setAttribute("alt", initialName.orEmpty())
            cover.appendChild(img)
            cover.asDynamic().hidden = false
        }
    }
    setCover(initialPhotoUrl)

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
        setCover(want.photoUrl as? String)
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
