package quest.saigon.web

import quest.saigon.Limits
import web.html.HTMLElement

internal fun composerPollFields(): ComposerPoll {
    val state = ComposerPoll()
    val box = el("div", "poll-composer")
    val toggle = el("input")
    toggle.setAttribute("type", "checkbox")
    val row = el("label", "switch-row")
    row.appendChild(toggle)
    row.appendChild(el("span", "switch-ui"))
    row.appendChild(el("span", text = Town.t("addPoll")))
    val fields = el("div", "poll-fields")
    fields.asDynamic().hidden = true
    val optionsBox = el("div", "poll-option-fields")
    val add = el("button", "text-button", text = Town.t("addPollOption"))
    add.setAttribute("type", "button")
    fun paint() {
        optionsBox.clear()
        state.labels.forEachIndexed { index, value ->
            val option = field(Town.t("pollOption", mapOf("n" to (index + 1).toString())), "text", "")
            option.input.setAttribute("maxlength", Limits.POLL_OPTION_MAX.toString())
            option.input.setAttribute("autocomplete", "off")
            option.input.asDynamic().value = value
            option.input.asDynamic().oninput = {
                state.labels[index] = option.value()
            }
            val row = el("div", "poll-option-row")
            row.appendChild(option.root)
            if (index >= Limits.POLL_OPTIONS_MIN) {
                val remove = el("button", "poll-option-remove", text = "×")
                remove.setAttribute("type", "button")
                remove.setAttribute("aria-label", Town.t("delete"))
                remove.onClick {
                    if (state.labels.size <= Limits.POLL_OPTIONS_MIN) return@onClick
                    state.labels.removeAt(index)
                    paint()
                }
                row.appendChild(remove)
            }
            optionsBox.appendChild(row)
        }
        add.asDynamic().hidden = state.labels.size >= Limits.POLL_OPTIONS_MAX
    }
    add.onClick {
        if (state.labels.size >= Limits.POLL_OPTIONS_MAX) return@onClick
        state.labels += ""
        paint()
    }
    toggle.asDynamic().onchange = {
        val on = toggle.asDynamic().checked == true
        fields.asDynamic().hidden = !on
        state.enabled = on
    }
    fields.appendChild(optionsBox)
    fields.appendChild(add)
    box.appendChild(row)
    box.appendChild(fields)
    state.root = box
    paint()
    return state
}

internal class ComposerPoll {
    var enabled: Boolean = false
    val labels: MutableList<String> = mutableListOf("", "")
    lateinit var root: HTMLElement
    fun options(): List<String> = labels.map { it.trim() }.filter { it.isNotEmpty() }
}

internal fun pollCard(
    post: PostCard,
    host: HTMLElement,
    router: HistoryRouter,
    onUpdate: (PostCard) -> Unit,
): HTMLElement {
    val card = el("div", "poll-card")
    fun paint(current: PostCard) {
        card.clear()
        card.appendChild(el("p", "poll-title", text = Town.t("poll")))
        val total = current.pollOptions.sumOf { it.voteCount }.coerceAtLeast(0)
        current.pollOptions.forEach { option ->
            val choice = el("div", "poll-choice")
            val button = el("button", if (option.voted) "poll-option voted" else "poll-option")
            button.setAttribute("type", "button")
            val fill = el("span", "poll-option-fill")
            val share = if (total > 0) option.voteCount.toDouble() / total.toDouble() else 0.0
            fill.asDynamic().style.width = "${(share * 100.0).coerceIn(0.0, 100.0)}%"
            button.appendChild(fill)
            button.appendChild(el("span", "poll-option-copy", text = option.text))
            if (option.voters.isNotEmpty()) {
                val faces = el("span", "poll-faces")
                fillVoterFaces(faces, option)
                faces.asDynamic().addEventListener("click", { event: dynamic ->
                    event.stopPropagation()
                    openVoters(current, host, router)
                })
                button.appendChild(faces)
            }
            button.onClick {
                if (Town.me == null) {
                    showAuthDialog(host, onSuccess = { button.asDynamic().click() })
                    return@onClick
                }
                button.setAttribute("disabled", "disabled")
                http("POST", "/api/posts/${current.id}/poll/${option.index}", "{}") { status, body ->
                    button.removeAttribute("disabled")
                    if (status != 200) {
                        toast(host, errorText(body, status))
                        return@http
                    }
                    val next = postFrom(parseJson(body))
                    onUpdate(next)
                    paint(next)
                }
            }
            choice.appendChild(button)
            card.appendChild(choice)
        }
    }
    paint(post)
    return card
}

private fun fillVoterFaces(host: HTMLElement, option: PollChoice) {
    val shown = option.voters.take(5)
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
}

private fun openVoters(post: PostCard, host: HTMLElement, router: HistoryRouter) {
    if (host.asDynamic().querySelector(".voter-sheet") != null) return
    val backdrop = el("div", "dialog-backdrop voter-sheet")
    val card = el("div", "dialog")
    card.appendChild(el("h2", text = Town.t("pollVoters")))
    val list = el("div", "member-list")
    card.appendChild(list)
    card.appendChild(el("button", "text-button", text = Town.t("close")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
    http("GET", "/api/posts/${post.id}/voters") { status, body ->
        if (status != 200) {
            list.appendChild(el("p", "hint", text = errorText(body, status)))
            return@http
        }
        val options = readPollOptions(parseJson(body).options)
            .sortedWith(compareByDescending<PollChoice> { it.voteCount }.thenBy { it.index })
        if (options.isEmpty()) {
            list.appendChild(el("p", "hint", text = Town.t("noMembers")))
            return@http
        }
        options.forEach { option ->
            val heading = el("h3", "poll-voter-group")
            heading.textContent = "${option.text} · ${option.voteCount}"
            list.appendChild(heading)
            if (option.voters.isEmpty()) {
                list.appendChild(el("p", "hint", text = Town.t("noMembers")))
            } else {
                option.voters.forEach { person ->
                    list.appendChild(voterRow(person, host, router))
                }
            }
        }
    }
}

private fun voterRow(person: EventPerson, host: HTMLElement, router: HistoryRouter): HTMLElement {
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
