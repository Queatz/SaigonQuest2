package quest.saigon.web

import quest.saigon.Limits
import web.html.HTMLElement
import web.window.window

internal data class CommunityCard(
    val id: String,
    val name: String,
    val emoji: String,
    val memberCount: Int = 0,
    val joined: Boolean = false,
    val description: String = "",
)

internal class CommunityChoice {
    var communityId: String? = null
    var exclusive: Boolean = false
}

internal fun communityFrom(node: dynamic): CommunityCard = CommunityCard(
    id = (node.id as? String).orEmpty(),
    name = (node.name as? String).orEmpty(),
    emoji = (node.emoji as? String).orEmpty().ifBlank { "✨" },
    memberCount = num(node.memberCount).toInt(),
    joined = node.joined == true,
    description = (node.description as? String).orEmpty(),
)

internal fun readCommunities(node: dynamic): List<CommunityCard> {
    val items = mutableListOf<CommunityCard>()
    if (node == null || node == undefined) return items
    val length = node.length as? Int ?: return items
    for (i in 0 until length) {
        val card = communityFrom(node[i])
        if (card.id.isNotBlank()) items += card
    }
    return items
}

internal fun communityLabel(community: CommunityCard): String =
    "${community.emoji} ${community.name}".trim()

internal fun communityChip(
    router: HistoryRouter?,
    community: CommunityCard,
    before: () -> Unit = {},
): HTMLElement {
    val chip = el("button", "community-chip", text = communityLabel(community))
    chip.setAttribute("type", "button")
    chip.onClick {
        before()
        router?.navigate("/c/${community.id}")
    }
    return chip
}

internal fun communityPicker(host: HTMLElement, message: HTMLElement): Pair<HTMLElement, CommunityChoice> {
    val choice = CommunityChoice()
    val box = el("div", "community-picker")
    val open = el("button", "community-open", text = "👥 ${Town.t("chooseCommunity")}")
    open.setAttribute("type", "button")
    open.setAttribute("aria-haspopup", "dialog")
    val suggest = el("button", "community-suggest")
    suggest.setAttribute("type", "button")
    suggest.asDynamic().hidden = true
    val exclusive = el("input")
    exclusive.setAttribute("type", "checkbox")
    val exclusiveRow = el("label", "switch-row community-only")
    exclusiveRow.appendChild(exclusive)
    exclusiveRow.appendChild(el("span", "switch-ui"))
    exclusiveRow.appendChild(el("span", text = Town.t("communityOnly")))
    val hint = el("p", "hint community-only-hint", text = Town.t("communityOnlyHint"))
    box.appendChild(open)
    box.appendChild(suggest)
    box.appendChild(exclusiveRow)
    box.appendChild(hint)

    var selected: CommunityCard? = null
    var suggestedId: String? = null
    var suggestedName = ""
    var suggestedEmoji = "✨"
    var ticket = 0
    var timer: dynamic = null

    fun paint() {
        val current = selected
        open.textContent = if (current == null) Town.t("chooseCommunity") else communityLabel(current)
        choice.communityId = current?.id
        val picked = current != null
        exclusiveRow.asDynamic().hidden = !picked
        hint.asDynamic().hidden = !picked
        if (!picked) exclusive.asDynamic().checked = false
        choice.exclusive = picked && exclusive.asDynamic().checked == true
        suggest.asDynamic().hidden = suggestedName.isBlank() || choice.communityId == suggestedId
    }

    fun select(community: CommunityCard?) {
        selected = community
        paint()
    }

    fun showSuggestion(emoji: String, name: String, id: String?) {
        suggestedEmoji = emoji
        suggestedName = name
        suggestedId = id
        suggest.textContent = "${Town.t("suggestCommunity")}: $emoji $name"
        suggest.asDynamic().hidden = choice.communityId != null && choice.communityId == id
    }

    fun requestSuggestion() {
        val text = (message.asDynamic().value as? String).orEmpty().trim()
        if (text.length < 8) {
            suggest.asDynamic().hidden = true
            return
        }
        val mine = ++ticket
        val payload = js("({})")
        payload.text = text
        http("POST", "/api/communities/suggest", jsonStringify(payload)) { status, body ->
            if (mine != ticket || status != 200) return@http
            val parsed = parseJson(body)
            val name = parsed.name as? String ?: return@http
            showSuggestion((parsed.emoji as? String).orEmpty().ifBlank { "✨" }, name, parsed.communityId as? String)
        }
    }

    exclusive.asDynamic().onchange = {
        choice.exclusive = exclusive.asDynamic().checked == true && selected != null
    }
    open.onClick { openCommunityChooser(host, selected) { picked -> select(picked) } }
    suggest.onClick {
        val existing = suggestedId
        if (!existing.isNullOrBlank()) {
            select(CommunityCard(existing, suggestedName, suggestedEmoji, joined = true))
            suggest.asDynamic().hidden = true
            return@onClick
        }
        if (suggestedName.isBlank()) return@onClick
        suggest.setAttribute("disabled", "disabled")
        suggest.textContent = Town.t("creatingCommunity")
        createCommunity(host, suggestedName, suggestedEmoji) { created ->
            suggest.removeAttribute("disabled")
            if (created == null) {
                showSuggestion(suggestedEmoji, suggestedName, null)
                return@createCommunity
            }
            suggestedId = created.id
            select(created)
            suggest.asDynamic().hidden = true
        }
    }
    message.asDynamic().addEventListener("input", {
        if (timer != null) window.asDynamic().clearTimeout(timer)
        timer = window.asDynamic().setTimeout({ requestSuggestion() }, 700)
    })
    exclusiveRow.asDynamic().hidden = true
    hint.asDynamic().hidden = true
    return box to choice
}

private fun openCommunityChooser(
    host: HTMLElement,
    current: CommunityCard?,
    onPick: (CommunityCard?) -> Unit,
) {
    if (host.asDynamic().querySelector(".community-chooser") != null) return
    val backdrop = el("div", "dialog-backdrop community-chooser")
    val card = el("div", "dialog community-dialog")
    card.setAttribute("role", "dialog")
    card.appendChild(el("h2", text = Town.t("chooseCommunity")))
    val search = el("input")
    search.setAttribute("type", "search")
    search.setAttribute("placeholder", Town.t("searchCommunities"))
    val results = el("div", "community-list chooser-list")
    fun close() {
        backdrop.asDynamic().remove()
    }
    fun choose(community: CommunityCard?) {
        close()
        onPick(community)
    }
    card.appendChild(search)
    card.appendChild(results)
    card.appendChild(communityCreateToggle(host) { created -> choose(created) })
    card.appendChild(el("button", "text-button", text = Town.t("close")) { button ->
        button.setAttribute("type", "button")
        button.onClick { close() }
    })
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { close() }
    host.appendChild(backdrop)

    var timer: dynamic = null
    fun load(query: String) {
        val path = if (query.isEmpty()) "/api/communities?limit=40" else "/api/communities?q=${encodeQuery(query)}"
        http("GET", path) { status, body ->
            val found = if (status == 200) readCommunities(parseJson(body).communities) else emptyList()
            if (query.isNotEmpty()) {
                paintChooser(results, found, current, allowNone = false, onPick = ::choose)
                return@http
            }
            http("GET", "/api/me/communities") { mineStatus, mineBody ->
                val mine = if (mineStatus == 200) readCommunities(parseJson(mineBody).communities) else emptyList()
                val merged = (mine + found).distinctBy { it.id }
                paintChooser(results, merged, current, allowNone = true, onPick = ::choose)
            }
        }
    }
    search.asDynamic().oninput = {
        if (timer != null) window.asDynamic().clearTimeout(timer)
        timer = window.asDynamic().setTimeout({
            load((search.asDynamic().value as? String).orEmpty().trim())
        }, 250)
    }
    load("")
    window.asDynamic().setTimeout({ search.asDynamic().focus() }, 30)
}

private fun paintChooser(
    results: HTMLElement,
    items: List<CommunityCard>,
    current: CommunityCard?,
    allowNone: Boolean,
    onPick: (CommunityCard?) -> Unit,
) {
    results.clear()
    if (allowNone) {
        val none = el("button", if (current == null) "chooser-row selected" else "chooser-row", text = Town.t("noCommunity"))
        none.setAttribute("type", "button")
        none.onClick { onPick(null) }
        results.appendChild(none)
    }
    if (items.isEmpty()) {
        results.appendChild(el("p", "hint", text = Town.t("noCommunities")))
        return
    }
    items.forEach { community ->
        results.appendChild(
            communityChooserRow(community, selected = community.id == current?.id) { onPick(it) },
        )
    }
}

private fun communityCreateToggle(host: HTMLElement, onCreated: (CommunityCard) -> Unit): HTMLElement {
    val section = el("div", "community-create-section")
    val openCreate = el("button", "community-open", text = Town.t("newCommunity"))
    openCreate.setAttribute("type", "button")
    lateinit var create: HTMLElement
    fun collapse() {
        openCreate.asDynamic().hidden = false
        create.asDynamic().hidden = true
    }
    create = communityCreateForm(
        host,
        onCreated = { created ->
            collapse()
            onCreated(created)
        },
        onCancel = { collapse() },
    )
    create.asDynamic().hidden = true
    openCreate.onClick {
        openCreate.asDynamic().hidden = true
        create.asDynamic().hidden = false
    }
    section.appendChild(openCreate)
    section.appendChild(create)
    return section
}

private fun communityCreateForm(
    host: HTMLElement,
    onCreated: (CommunityCard) -> Unit,
    onCancel: () -> Unit,
): HTMLElement {
    val form = el("div", "community-create")
    val heading = el("div", "community-create-heading")
    heading.appendChild(el("span", "community-create-label", text = Town.t("newCommunity")))
    val cancel = el("button", "text-button community-cancel", text = Town.t("cancel"))
    cancel.setAttribute("type", "button")
    heading.appendChild(cancel)
    form.appendChild(heading)
    val row = el("div", "community-create-row")
    val emoji = el("input", "emoji-input")
    emoji.setAttribute("type", "text")
    emoji.setAttribute("maxlength", "8")
    emoji.setAttribute("placeholder", "✨")
    emoji.setAttribute("aria-label", Town.t("communityEmoji"))
    emoji.setAttribute("autocomplete", "off")
    val name = el("input", "name-input")
    name.setAttribute("type", "text")
    name.setAttribute("maxlength", "32")
    name.setAttribute("placeholder", Town.t("communityName"))
    name.setAttribute("aria-label", Town.t("communityName"))
    name.setAttribute("autocomplete", "off")
    val button = el("button", "create-community", text = Town.t("createCommunity"))
    button.setAttribute("type", "button")
    val error = el("p", "hint")
    error.asDynamic().hidden = true
    var emojiTimer: dynamic = null
    var emojiTicket = 0
    var emojiManual = false

    fun emojiText(): String = (emoji.asDynamic().value as? String).orEmpty()

    fun stopEmojiGeneration() {
        if (emojiTimer != null) window.asDynamic().clearTimeout(emojiTimer)
        emojiTimer = null
        emojiTicket++
        emoji.setAttribute("placeholder", "✨")
    }

    fun resetEmojiState() {
        emojiManual = false
        stopEmojiGeneration()
        emoji.asDynamic().value = ""
    }

    fun requestEmoji(text: String) {
        if (emojiManual) return
        val mine = emojiTicket
        emoji.asDynamic().value = ""
        emoji.setAttribute("placeholder", "…")
        val payload = js("({})")
        payload.name = text
        http("POST", "/api/communities/emoji", jsonStringify(payload)) { status, body ->
            if (mine != emojiTicket) return@http
            emoji.setAttribute("placeholder", "✨")
            if (emojiManual) return@http
            if (status != 200) return@http
            val generated = (parseJson(body).emoji as? String).orEmpty()
            if (generated.isBlank()) return@http
            emoji.asDynamic().value = generated
        }
    }

    fun scheduleEmoji() {
        if (emojiTimer != null) window.asDynamic().clearTimeout(emojiTimer)
        emojiTimer = null
        emojiTicket++
        emoji.setAttribute("placeholder", "✨")
        if (emojiManual) return
        val text = (name.asDynamic().value as? String).orEmpty().trim()
        if (text.isEmpty()) return
        emojiTimer = window.asDynamic().setTimeout({ requestEmoji(text) }, 2000)
    }

    name.asDynamic().addEventListener("input", { scheduleEmoji() })
    emoji.asDynamic().addEventListener("input", {
        if (emojiText().isNotEmpty()) {
            emojiManual = true
            stopEmojiGeneration()
        } else {
            emojiManual = false
            scheduleEmoji()
        }
    })
    button.onClick {
        val rawName = (name.asDynamic().value as? String).orEmpty().trim()
        if (rawName.isEmpty()) {
            error.textContent = Town.t("invalidCommunity")
            error.asDynamic().hidden = false
            return@onClick
        }
        button.setAttribute("disabled", "disabled")
        val rawEmoji = emojiText().ifBlank { "✨" }
        createCommunity(host, rawName, rawEmoji) { created ->
            button.removeAttribute("disabled")
            if (created == null) return@createCommunity
            name.asDynamic().value = ""
            resetEmojiState()
            error.asDynamic().hidden = true
            onCreated(created)
        }
    }
    cancel.onClick {
        name.asDynamic().value = ""
        resetEmojiState()
        error.asDynamic().hidden = true
        onCancel()
    }
    row.appendChild(emoji)
    row.appendChild(name)
    row.appendChild(button)
    form.appendChild(row)
    form.appendChild(error)
    return form
}

private fun createCommunity(
    host: HTMLElement,
    name: String,
    emoji: String,
    onDone: (CommunityCard?) -> Unit,
) {
    if (Town.me == null) {
        showAuthDialog(host, onSuccess = { createCommunity(host, name, emoji, onDone) })
        return
    }
    val payload = js("({})")
    payload.name = name
    payload.emoji = emoji
    http("POST", "/api/communities", jsonStringify(payload)) { status, body ->
        if (status != 200 && status != 201) {
            toast(host, errorText(body, status))
            onDone(null)
            return@http
        }
        onDone(communityFrom(parseJson(body)))
    }
}

internal fun communitiesPanel(host: HTMLElement, router: HistoryRouter): HTMLElement {
    val box = el("div", "community-panel")
    val joined = el("div", "community-list")
    val search = el("input")
    search.setAttribute("type", "search")
    search.setAttribute("placeholder", Town.t("searchCommunities"))
    val results = el("div", "community-list")
    fun loadJoined() {
        http("GET", "/api/me/communities") { status, body ->
            joined.clear()
            if (status != 200) {
                joined.appendChild(el("p", "hint", text = Town.t("unavailable")))
                return@http
            }
            val items = readCommunities(parseJson(body).communities)
            if (items.isEmpty()) {
                joined.appendChild(el("p", "hint", text = Town.t("noCommunities")))
                return@http
            }
            items.forEach { community ->
                joined.appendChild(communityActionRow(host, router, community, leaving = true, onDone = { loadJoined() }))
            }
        }
    }
    box.appendChild(joined)
    box.appendChild(communityCreateToggle(host) { loadJoined() })
    box.appendChild(search)
    box.appendChild(results)

    var timer: dynamic = null
    search.asDynamic().oninput = {
        if (timer != null) window.asDynamic().clearTimeout(timer)
        timer = window.asDynamic().setTimeout({
            val query = (search.asDynamic().value as? String).orEmpty().trim()
            results.clear()
            if (query.isEmpty()) return@setTimeout
            http("GET", "/api/communities?q=${encodeQuery(query)}") { status, body ->
                results.clear()
                if (status != 200) return@http
                val items = readCommunities(parseJson(body).communities)
                if (items.isEmpty()) {
                    results.appendChild(el("p", "hint", text = Town.t("noCommunities")))
                    return@http
                }
                items.forEach { community ->
                    results.appendChild(
                        communityActionRow(host, router, community, leaving = community.joined) {
                            loadJoined()
                            search.asDynamic().dispatchEvent(js("new Event('input')"))
                        },
                    )
                }
            }
        }, 300)
    }
    loadJoined()
    return box
}

internal fun bindCommunityBar(
    host: HTMLElement,
    router: HistoryRouter,
    communityId: String,
    title: HTMLElement,
    members: HTMLElement,
    join: HTMLElement,
    onReady: () -> Unit,
    onMissing: () -> Unit,
) {
    http("GET", "/api/communities/${encodeQuery(communityId)}") { status, body ->
        if (status != 200) {
            onMissing()
            return@http
        }
        val community = communityFrom(parseJson(body))
        paintCommunityBar(title, members, join, community)
        members.onClick {
            http("GET", "/api/communities/${encodeQuery(communityId)}") { status, fresh ->
                val current = if (status == 200) communityFrom(parseJson(fresh)) else community
                openMembersDialog(host, router, current)
            }
        }
        join.onClick { toggleMembership(host, communityId, title, members, join) }
        onReady()
    }
}

internal fun openMembersDialog(host: HTMLElement, router: HistoryRouter, community: CommunityCard) {
    if (host.asDynamic().querySelector(".members-backdrop") != null) return
    val backdrop = el("div", "dialog-backdrop members-backdrop")
    val card = el("div", "dialog")
    card.appendChild(el("h2", text = communityLabel(community)))
    card.appendChild(communityDescriptionEditor(host, community))
    card.appendChild(el("h3", text = Town.t("communityMembers")))
    val list = el("div", "member-list")
    card.appendChild(list)
    card.appendChild(el("button", "text-button", text = Town.t("close")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
    loadMembers(list, host, router, community.id, null)
}

private fun loadMembers(
    list: HTMLElement,
    host: HTMLElement,
    router: HistoryRouter,
    communityId: String,
    cursor: String?,
) {
    val query = if (cursor.isNullOrBlank()) {
        "/api/communities/${encodeQuery(communityId)}/members?limit=30"
    } else {
        "/api/communities/${encodeQuery(communityId)}/members?limit=30&cursor=${encodeQuery(cursor)}"
    }
    http("GET", query) { status, body ->
        if (status != 200) {
            if (list.childNodes.length == 0) list.appendChild(el("p", "hint", text = Town.t("unavailable")))
            return@http
        }
        val parsed = parseJson(body)
        val members = parsed.members
        if (members == null || members == undefined) {
            if (list.childNodes.length == 0) list.appendChild(el("p", "hint", text = Town.t("noMembers")))
            return@http
        }
        val length = members.length as Int
        if (length == 0 && list.childNodes.length == 0) {
            list.appendChild(el("p", "hint", text = Town.t("noMembers")))
            return@http
        }
        for (i in 0 until length) {
            val member = members[i]
            val row = el("button", "member-row")
            row.setAttribute("type", "button")
            val name = (member.name as? String).orEmpty()
            val photo = member.photoUrl as? String
            if (!photo.isNullOrBlank()) {
                val face = el("img", "face")
                face.setAttribute("alt", "")
                face.setAttribute("src", prefixServerOrigin(photo))
                row.appendChild(face)
            } else {
                row.appendChild(el("span", "face initial", text = name.take(1).uppercase()))
            }
            val copy = el("span", "member-copy")
            copy.appendChild(el("strong", text = name))
            val place = member.location as? String
            if (!place.isNullOrBlank()) copy.appendChild(el("small", text = place))
            row.appendChild(copy)
            val userId = member.id as? String
            row.onClick {
                openMiniProfile(
                    host,
                    name,
                    photo?.let(::prefixServerOrigin),
                    (member.location as? String).orEmpty(),
                    (member.intro as? String).orEmpty(),
                    userId.orEmpty(),
                    router,
                )
            }
            list.appendChild(row)
        }
        val next = parsed.nextCursor as? String
        if (!next.isNullOrBlank()) {
            list.appendChild(el("button", "text-button", text = Town.t("loadMore")) { button ->
                button.setAttribute("type", "button")
                button.onClick {
                    button.asDynamic().remove()
                    loadMembers(list, host, router, communityId, next)
                }
            })
        }
    }
}

private fun toggleMembership(
    host: HTMLElement,
    communityId: String,
    title: HTMLElement,
    members: HTMLElement,
    join: HTMLElement,
) {
    if (Town.me == null) {
        showAuthDialog(host, onSuccess = { toggleMembership(host, communityId, title, members, join) })
        return
    }
    val leaving = join.getAttribute("data-joined") == "1"
    join.setAttribute("disabled", "disabled")
    val action = if (leaving) "leave" else "join"
    http("POST", "/api/communities/${encodeQuery(communityId)}/$action") { status, body ->
        join.removeAttribute("disabled")
        if (status != 200) {
            toast(host, errorText(body, status))
            return@http
        }
        paintCommunityBar(title, members, join, communityFrom(parseJson(body)))
    }
}

private fun paintCommunityBar(
    title: HTMLElement,
    members: HTMLElement,
    join: HTMLElement,
    community: CommunityCard,
) {
    title.textContent = communityLabel(community)
    members.textContent = if (community.memberCount > 0) {
        "${Town.t("communityMembers")} · ${community.memberCount}"
    } else {
        Town.t("communityMembers")
    }
    join.textContent = Town.t(if (community.joined) "leaveCommunity" else "joinCommunity")
    join.setAttribute("data-joined", if (community.joined) "1" else "0")
}

private fun communityDescriptionEditor(host: HTMLElement, initial: CommunityCard): HTMLElement {
    val box = el("div", "community-about")
    var community = initial
    var editing = false
    fun paint() {
        box.clear()
        box.asDynamic().hidden = false
        if (editing) {
            val input = el("textarea", "community-description-input")
            input.setAttribute("maxlength", Limits.COMMUNITY_DESCRIPTION_MAX.toString())
            input.setAttribute("placeholder", Town.t("communityDescriptionHint"))
            input.setAttribute("aria-label", Town.t("communityDescription"))
            input.asDynamic().value = community.description
            val actions = el("div", "community-description-actions")
            val save = el("button", "primary", text = Town.t("save"))
            save.setAttribute("type", "button")
            val cancel = el("button", "text-button", text = Town.t("cancel"))
            cancel.setAttribute("type", "button")
            save.onClick {
                val payload = js("{}")
                payload.description = (input.asDynamic().value as? String).orEmpty()
                save.setAttribute("disabled", "disabled")
                http("PATCH", "/api/communities/${encodeQuery(community.id)}", jsonStringify(payload)) { status, body ->
                    save.removeAttribute("disabled")
                    if (status != 200) {
                        toast(host, errorText(body, status))
                        return@http
                    }
                    community = communityFrom(parseJson(body))
                    editing = false
                    paint()
                }
            }
            cancel.onClick {
                editing = false
                paint()
            }
            actions.appendChild(save)
            actions.appendChild(cancel)
            box.appendChild(input)
            box.appendChild(actions)
            window.asDynamic().setTimeout({ input.asDynamic().focus() }, 30)
            return
        }
        if (community.description.isNotBlank()) {
            box.appendChild(linkedParagraph(community.description, "community-description"))
        }
        if (community.joined) {
            val label = Town.t(if (community.description.isBlank()) "addDescription" else "edit")
            box.appendChild(el("button", "text-button", text = label) { button ->
                button.setAttribute("type", "button")
                button.onClick {
                    editing = true
                    paint()
                }
            })
        }
        box.asDynamic().hidden = box.childNodes.length == 0
    }
    paint()
    return box
}

private fun communityChooserRow(
    community: CommunityCard,
    selected: Boolean = false,
    onPick: (CommunityCard) -> Unit,
): HTMLElement {
    val row = el("button", if (selected) "chooser-row selected" else "chooser-row")
    row.setAttribute("type", "button")
    val copy = el("div", "chooser-copy")
    copy.appendChild(el("span", text = communityLabel(community)))
    if (community.description.isNotBlank()) {
        copy.appendChild(linkedParagraph(community.description, "community-description"))
    }
    row.appendChild(copy)
    if (community.memberCount > 0) {
        row.appendChild(el("small", text = community.memberCount.toString()))
    }
    row.onClick { onPick(community) }
    return row
}

internal fun communityActionRow(
    host: HTMLElement,
    router: HistoryRouter,
    community: CommunityCard,
    leaving: Boolean,
    onDone: () -> Unit,
): HTMLElement {
    val row = el("div", "community-row")
    row.appendChild(
        communityChooserRow(community) { router.navigate("/c/${community.id}") },
    )
    val button = el(
        "button",
        if (leaving) "tiny danger community-action" else "tiny community-action",
        text = Town.t(if (leaving) "leaveCommunity" else "joinCommunity"),
    )
    button.setAttribute("type", "button")
    button.onClick {
        if (Town.me == null) {
            showAuthDialog(host, onSuccess = onDone)
            return@onClick
        }
        button.setAttribute("disabled", "disabled")
        val action = if (leaving) "leave" else "join"
        http("POST", "/api/communities/${community.id}/$action") { status, body ->
            button.removeAttribute("disabled")
            if (status == 200) onDone() else toast(host, errorText(body, status))
        }
    }
    row.appendChild(button)
    return row
}
