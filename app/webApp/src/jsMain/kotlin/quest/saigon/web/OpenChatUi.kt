package quest.saigon.web

import quest.saigon.Limits
import web.html.HTMLElement
import kotlin.math.ceil

internal class ComposerOpenChat {
    var enabled = false
    var createNew = false
    var chatId: String? = null
    var name = ""
    var hours = Limits.OPEN_CHAT_HOURS_DEFAULT
    var remaining: Int? = null
    val groups = mutableListOf<Pair<String, String>>()
    var groupsLoaded = false
    var groupLoadError: String? = null
    var refreshChooser: (() -> Unit)? = null
    lateinit var root: HTMLElement
}

internal fun composerOpenChatFields(host: HTMLElement): ComposerOpenChat {
    val state = ComposerOpenChat()
    val box = el("div", "open-chat-composer")
    val toggle = el("input")
    toggle.setAttribute("type", "checkbox")
    val row = el("label", "switch-row")
    row.appendChild(toggle)
    row.appendChild(el("span", "switch-ui"))
    row.appendChild(el("span", text = "💬 ${Town.t("addOpenChat")}"))

    val fields = el("div", "open-chat-fields")
    fields.asDynamic().hidden = true

    val chatPicker = el("button", "open-chat-picker community-open", text = Town.t("selectGroupChat"))
    chatPicker.setAttribute("type", "button")
    chatPicker.setAttribute("aria-label", Town.t("selectGroupChat"))
    chatPicker.setAttribute("aria-haspopup", "dialog")
    val chatField = el("label", "field")
    chatField.appendChild(el("span", "field-label", text = Town.t("groupChat")))
    chatField.appendChild(chatPicker)

    val createToggle = el("input")
    createToggle.setAttribute("type", "checkbox")
    val createRow = el("label", "open-chat-create switch-row")
    createRow.appendChild(createToggle)
    createRow.appendChild(el("span", "switch-ui"))
    createRow.appendChild(el("span", text = Town.t("createGroupChat")))
    val createSection = el("div", "open-chat-create-section")
    val nameField = field(Town.t("groupName"), "text", Town.t("groupNameHint"))
    nameField.root.asDynamic().hidden = true
    nameField.input.asDynamic().oninput = { state.name = nameField.value() }
    createSection.appendChild(createRow)
    createSection.appendChild(nameField.root)

    val hoursField = openChatNumberField(
        label = Town.t("openChatExpiration"),
        min = Limits.OPEN_CHAT_HOURS_MIN,
        max = Limits.OPEN_CHAT_HOURS_MAX,
        initial = Limits.OPEN_CHAT_HOURS_DEFAULT,
        suffix = Town.t("hoursUnit"),
        onChange = { state.hours = it ?: Limits.OPEN_CHAT_HOURS_DEFAULT },
    )
    val remainingField = openChatNumberField(
        label = Town.t("openChatRemaining"),
        min = Limits.OPEN_CHAT_REMAINING_MIN,
        max = Limits.OPEN_CHAT_REMAINING_MAX,
        initial = null,
        optional = true,
        emptyLabel = Town.t("unlimitedOpenChatJoins"),
        onChange = { state.remaining = it },
    )

    fun updateChatPicker() {
        chatPicker.textContent = state.chatId?.let { id -> state.groups.firstOrNull { it.first == id }?.second }
            ?: Town.t("selectGroupChat")
    }

    fun openGroupChooser() {
        if (host.asDynamic().querySelector(".open-chat-chooser") != null) return
        val backdrop = el("div", "dialog-backdrop open-chat-chooser")
        val dialog = el("div", "dialog community-dialog")
        dialog.setAttribute("role", "dialog")
        dialog.setAttribute("aria-modal", "true")
        dialog.appendChild(el("h2", text = Town.t("selectGroupChat")))
        val search = el("input")
        search.setAttribute("type", "search")
        search.setAttribute("placeholder", Town.t("searchGroups"))
        search.setAttribute("aria-label", Town.t("searchGroups"))
        val results = el("div", "community-list chooser-list open-chat-results")

        fun close() {
            state.refreshChooser = null
            backdrop.asDynamic().remove()
        }

        fun renderResults() {
            results.clear()
            if (!state.groupsLoaded) {
                results.appendChild(el("p", "hint", text = Town.t("loading")))
                return
            }
            if (state.groups.isEmpty()) {
                results.appendChild(el("p", "hint", text = state.groupLoadError ?: Town.t("noGroups")))
                return
            }
            state.groupLoadError?.let { results.appendChild(el("p", "hint", text = it)) }
            val query = (search.asDynamic().value as? String).orEmpty().trim()
            val matching = state.groups.filter { (_, name) -> name.contains(query, ignoreCase = true) }
            if (matching.isEmpty()) {
                results.appendChild(el("p", "hint", text = Town.t("noGroupMatches")))
                return
            }
            matching.forEach { (id, name) ->
                val rowClass = if (id == state.chatId) "chooser-row selected" else "chooser-row"
                val row = el("button", rowClass)
                row.setAttribute("type", "button")
                row.appendChild(el("strong", text = name))
                row.onClick {
                    state.chatId = id
                    updateChatPicker()
                    close()
                }
                results.appendChild(row)
            }
        }

        state.refreshChooser = ::renderResults
        search.asDynamic().oninput = { renderResults() }
        dialog.appendChild(search)
        dialog.appendChild(results)
        dialog.appendChild(el("button", "text-button", text = Town.t("close")) { button ->
            button.setAttribute("type", "button")
            button.onClick { close() }
        })
        backdrop.appendChild(dialog)
        backdrop.closeOnOutsideClick { close() }
        host.appendChild(backdrop)
        renderResults()
        search.asDynamic().focus()
    }

    fun repaint() {
        state.createNew = createToggle.asDynamic().checked == true
        chatField.asDynamic().hidden = state.createNew
        nameField.root.asDynamic().hidden = !state.createNew
        if (state.createNew) {
            state.chatId = null
            updateChatPicker()
        }
    }

    toggle.asDynamic().onchange = {
        state.enabled = toggle.asDynamic().checked == true
        fields.asDynamic().hidden = !state.enabled
    }
    createToggle.asDynamic().onchange = { repaint() }
    chatPicker.onClick { openGroupChooser() }

    fields.appendChild(chatField)
    fields.appendChild(createSection)
    fields.appendChild(hoursField)
    fields.appendChild(remainingField)
    box.appendChild(row)
    box.appendChild(fields)
    state.root = box

    fun loadGroups(cursor: String? = null) {
        val more = if (cursor.isNullOrBlank()) "" else "&cursor=${encodeQuery(cursor)}"
        http("GET", "/api/chats?type=group&limit=50$more") { status, text ->
            if (status !in 200..299) {
                state.groupLoadError = errorText(text, status)
                state.groupsLoaded = true
                state.refreshChooser?.invoke()
                return@http
            }
            val page = parseJson(text)
            val chats = page.chats
            val count = chats?.length as? Int ?: 0
            for (index in 0 until count) {
                val chat = chats[index]
                val id = chat.id as? String ?: continue
                if (id.isBlank() || state.groups.any { it.first == id }) continue
                val name = (chat.name as? String).orEmpty().ifBlank { Town.t("unnamedGroupChat") }
                state.groups += id to name
            }
            val nextCursor = page.nextCursor as? String
            if (!nextCursor.isNullOrBlank()) {
                loadGroups(nextCursor)
            } else {
                state.groupsLoaded = true
                state.refreshChooser?.invoke()
            }
        }
    }
    loadGroups()
    return state
}

internal fun appendOpenChatForm(state: ComposerOpenChat, form: dynamic) {
    if (!state.enabled) return
    if (state.createNew) {
        form.append("addOpenChat", "true")
        form.append("openChatNew", "true")
        if (state.name.isNotBlank()) form.append("openChatName", state.name.trim())
    } else {
        val chatId = state.chatId ?: return
        form.append("addOpenChat", "true")
        form.append("openChatId", chatId)
    }
    form.append("openChatHours", state.hours.toString())
    state.remaining?.let { form.append("openChatRemaining", it.toString()) }
}

internal fun openChatCard(
    post: PostCard,
    host: HTMLElement,
    router: HistoryRouter,
    onUpdate: (PostCard) -> Unit,
): HTMLElement {
    val card = el("div", "open-chat-card")
    val details = el("div", "open-chat-details")
    details.appendChild(el("p", "open-chat-name", text = post.openChatName.ifBlank { Town.t("unnamedGroupChat") }))
    post.openChatExpiresAt?.let { expiresAt ->
        val remainingMillis = expiresAt - num(js("Date.now()")).toLong()
        val label = if (remainingMillis <= 0) {
            Town.t("openChatExpired")
        } else {
            val hours = ceil(remainingMillis / 3_600_000.0).toInt().coerceAtLeast(1)
            val duration = Town.t(if (hours == 1) "oneHour" else "hoursCount", mapOf("count" to hours.toString()))
            "${Town.t("openChatExpiration")} $duration"
        }
        details.appendChild(el("p", "open-chat-meta", text = label))
    }
    post.openChatRemaining?.let { remaining ->
        details.appendChild(
            el("p", "open-chat-meta", text = Town.t("openChatSlotsRemaining", mapOf("count" to remaining.toString()))),
        )
    }
    card.appendChild(details)
    val button = el("button", "open-chat-action", text = Town.t("openChat"))
    button.setAttribute("type", "button")
    fun activate() {
        if (Town.me == null) {
            showAuthDialog(host, onSuccess = { activate() })
            return
        }
        button.setAttribute("disabled", "disabled")
        http("POST", "/api/posts/${encodeQuery(post.id)}/open-chat") { status, text ->
            button.removeAttribute("disabled")
            if (status == 200) {
                val response = parseJson(text)
                val chatId = (response.id as? String).orEmpty()
                if (chatId.isBlank()) {
                    toast(host, Town.t("errorGeneric"))
                    return@http
                }
                val remaining = if (response.openChatRemaining == null || response.openChatRemaining == undefined) {
                    null
                } else {
                    num(response.openChatRemaining).toLong()
                }
                onUpdate(post.copy(openChatJoined = true, openChatRemaining = remaining))
                router.navigate("/messages/$chatId")
            } else {
                toast(host, errorText(text, status))
            }
        }
    }
    button.onClick { activate() }
    card.appendChild(button)
    return card
}

private fun openChatNumberField(
    label: String,
    min: Int,
    max: Int,
    initial: Int?,
    optional: Boolean = false,
    emptyLabel: String? = null,
    suffix: String? = null,
    onChange: (Int?) -> Unit,
): HTMLElement {
    val root = el("label", "field")
    root.appendChild(el("span", "field-label", text = label))
    val control = el("div", "open-chat-number-control")
    val decrease = el("button", "open-chat-step", text = "−")
    decrease.setAttribute("type", "button")
    decrease.setAttribute("aria-label", Town.t("decreaseNumber"))
    val input = el("input", "open-chat-number-input")
    input.setAttribute("type", "number")
    input.setAttribute("min", min.toString())
    input.setAttribute("max", max.toString())
    input.setAttribute("step", "1")
    input.setAttribute("aria-label", label)
    emptyLabel?.let { input.setAttribute("placeholder", it) }
    if (!optional) input.setAttribute("required", "required")
    input.asDynamic().value = initial?.toString().orEmpty()
    val increase = el("button", "open-chat-step", text = "+")
    increase.setAttribute("type", "button")
    increase.setAttribute("aria-label", Town.t("increaseNumber"))
    control.appendChild(decrease)
    control.appendChild(input)
    control.appendChild(increase)
    suffix?.let { control.appendChild(el("span", "open-chat-number-suffix", text = it)) }

    fun setValue(value: Int?) {
        if (value == null && optional) {
            input.asDynamic().value = ""
            onChange(null)
            return
        }
        val bounded = (value ?: min).coerceIn(min, max)
        input.asDynamic().value = bounded.toString()
        onChange(bounded)
    }

    fun step(delta: Int) {
        val value = (input.asDynamic().value as? String)?.toIntOrNull()
        if (optional && value == null) {
            if (delta > 0) setValue(min)
            return
        }
        if (optional && delta < 0 && value != null && value <= min) {
            setValue(null)
            return
        }
        setValue(((value ?: min) + delta).coerceIn(min, max))
    }

    input.asDynamic().oninput = {
        val raw = (input.asDynamic().value as? String).orEmpty()
        if (raw.isBlank() && optional) onChange(null)
        else raw.toIntOrNull()?.takeIf { it in min..max }?.let(onChange)
    }
    input.asDynamic().onchange = {
        val raw = (input.asDynamic().value as? String).orEmpty()
        if (raw.isBlank() && optional) setValue(null)
        else setValue(raw.toIntOrNull())
    }
    decrease.onClick { step(-1) }
    increase.onClick { step(1) }
    root.appendChild(control)
    return root
}