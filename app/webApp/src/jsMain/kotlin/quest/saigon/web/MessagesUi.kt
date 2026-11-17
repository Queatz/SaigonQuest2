package quest.saigon.web

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import quest.saigon.Limits
import quest.saigon.shared.js.JsSharedHolder
import quest.saigon.shared.js.launchAction
import quest.saigon.shared.js.observeState
import quest.saigon.shared.model.ChatStreamEventDto
import web.html.HTMLElement
import web.window.window

private const val COPY_ICON =
    """<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="8" y="7" width="12" height="14" rx="2" fill="none" stroke="currentColor" stroke-width="2"/><path d="M6 17H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v1" fill="none" stroke="currentColor" stroke-width="2"/></svg>"""
private const val CHAT_ICON =
    """<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 5h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H9l-4 4v-4H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg>"""
private const val PLUS_ICON =
    """<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>"""
private const val MEMBERS_ICON =
    """<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="9" cy="8" r="3" fill="none" stroke="currentColor" stroke-width="2"/><path d="M4 18c0-2.5 2.2-4.5 5-4.5s5 2 5 4.5" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="17" cy="9" r="2.4" fill="none" stroke="currentColor" stroke-width="2"/><path d="M16 18c.2-1.6 1.4-3 3.2-3.4" fill="none" stroke="currentColor" stroke-width="2"/></svg>"""
private const val PHOTO_ICON =
    """<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="8.5" cy="10" r="1.5" fill="currentColor"/><path d="M21 16l-5.5-5.5L9 17" fill="none" stroke="currentColor" stroke-width="2"/></svg>"""
private const val MENU_ICON =
    """<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="6" r="1.6" fill="currentColor"/><circle cx="12" cy="12" r="1.6" fill="currentColor"/><circle cx="12" cy="18" r="1.6" fill="currentColor"/></svg>"""

private var chatLeave: (() -> Unit)? = null
private var messagesSelection: ((String?) -> Unit)? = null

internal fun clearChatScreen() {
    chatLeave?.invoke()
    chatLeave = null
    messagesSelection = null
}

internal object ChatInbox {
    var unread = 0
        internal set
    private var stopObserve: (() -> Unit)? = null
    private var eventsJob: Job? = null
    private val listeners = mutableListOf<(dynamic) -> Unit>()

    fun connect() {
        if (Town.me == null) {
            disconnect()
            return
        }
        val messages = JsSharedHolder.client.messages
        if (stopObserve == null) {
            stopObserve = observeState(JsSharedHolder.scope, messages.state) { state ->
                unread = state.unreadCount.toInt()
                paintBadges()
            }
        }
        if (eventsJob == null || eventsJob?.isActive != true) {
            eventsJob = JsSharedHolder.scope.launch {
                messages.streamEvents.collect { event ->
                    if (event.type == "message" && event.message?.userId != Town.me?.id) {
                        playNotifySound()
                    }
                    val json = JsSharedHolder.client.api.json.encodeToString(ChatStreamEventDto.serializer(), event)
                    notifyListeners(parseJson(json))
                }
            }
        }
        messages.connectStream()
    }

    private fun notifyListeners(event: dynamic) {
        listeners.toList().forEach { listener ->
            try {
                listener(event)
            } catch (_: Throwable) {
            }
        }
    }

    fun disconnect() {
        stopObserve?.invoke()
        stopObserve = null
        eventsJob?.cancel()
        eventsJob = null
        JsSharedHolder.client.messages.disconnectStream()
        unread = 0
        paintBadges()
    }

    fun refresh() {
        if (Town.me == null) return
        launchAction(JsSharedHolder.scope) {
            JsSharedHolder.client.messages.refreshUnread()
        }
    }

    fun listen(handler: (dynamic) -> Unit): () -> Unit {
        listeners += handler
        return {
            listeners.remove(handler)
        }
    }

    fun paintBadges() {
        val nodes = web.dom.document.asDynamic().querySelectorAll(".messages-badge")
        val length = nodes.length as Int
        for (i in 0 until length) {
            val badge = nodes[i]
            badge.textContent = if (unread > 0) unread.toString() else ""
            badge.hidden = unread <= 0
        }
        val buttons = web.dom.document.asDynamic().querySelectorAll(".messages-btn")
        val count = buttons.length as Int
        for (i in 0 until count) {
            val button = buttons[i]
            if (unread > 0) button.classList.add("has-unread") else button.classList.remove("has-unread")
        }
    }
}

internal fun messagesButton(router: HistoryRouter): HTMLElement {
    val button = el("button", "notify-btn messages-btn")
    button.setAttribute("type", "button")
    button.setAttribute("aria-label", Town.t("openMessages"))
    button.asDynamic().innerHTML = CHAT_ICON
    val badge = el("span", "notify-badge messages-badge")
    badge.asDynamic().hidden = ChatInbox.unread <= 0
    badge.textContent = if (ChatInbox.unread > 0) ChatInbox.unread.toString() else ""
    button.appendChild(badge)
    button.onClick { router.navigate("/messages") }
    return button
}

internal fun messagesScreen(router: HistoryRouter, chatId: String? = null): HTMLElement {
    val shell = el("section", "shell page-shell messages-shell")
    val listPane = el("section", "messages-list-pane")
    val head = el("header", "page-head has-profile messages-head")
    head.appendChild(el("button", "icon-btn", text = "‹") { button ->
        button.setAttribute("type", "button")
        button.setAttribute("aria-label", Town.t("back"))
        button.onClick { back(router) }
    })
    head.appendChild(el("h1", text = Town.t("messages")))
    val actions = el("div", "messages-head-actions")
    actions.appendChild(el("button", "icon-btn") { button ->
        button.setAttribute("type", "button")
        button.setAttribute("aria-label", Town.t("newGroup"))
        button.asDynamic().innerHTML = PLUS_ICON
        button.onClick { openCreateGroup(shell, router) }
    })
    actions.appendChild(notificationButton(router, shell))
    actions.appendChild(viewerButton(router, toComposer = false, editBadge = true))
    head.appendChild(actions)
    listPane.appendChild(head)
    val list = el("div", "page-body chat-list")
    listPane.appendChild(list)
    shell.appendChild(listPane)
    val chatPane = el("section", "messages-chat-pane")
    shell.appendChild(chatPane)
    var selectedChatId = chatId
    var leaveChat: (() -> Unit)? = null

    fun selectChat(id: String?) {
        selectedChatId = id?.takeIf { it.isNotBlank() }
        shell.asDynamic().classList.toggle("has-selected-chat", selectedChatId != null)
        val rows = list.asDynamic().querySelectorAll(".chat-row")
        val length = rows.length as Int
        for (i in 0 until length) {
            val row = rows[i]
            row.classList.toggle("selected", row.getAttribute("data-chat-id") == selectedChatId)
        }
        leaveChat?.invoke()
        leaveChat = null
        chatPane.clear()
        val selected = selectedChatId
        if (selected == null) {
            chatPane.appendChild(el("p", "messages-empty-chat", text = Town.t("noChatSelected")))
        } else {
            val (content, stop) = chatScreen(router, selected)
            leaveChat = stop
            chatPane.appendChild(content)
        }
    }

    fun paint(chats: List<ChatCard>) {
        list.clear()
        if (chats.isEmpty()) {
            list.appendChild(el("p", "hint", text = Town.t("noChats")))
            return
        }
        chats.forEach { chat -> list.appendChild(chatRow(chat, router, selectedChatId)) }
    }
    fun refreshChats() {
        http("GET", "/api/chats") { status, body ->
            if (shell.asDynamic().isConnected == false) return@http
            if (status != 200) {
                if (list.childElementCount == 0) list.appendChild(el("p", "hint", text = errorText(body, status)))
                return@http
            }
            val parsed = parseJson(body)
            ChatInbox.unread = num(parsed.unreadCount).toInt()
            ChatInbox.paintBadges()
            paint(readChats(parsed.chats))
            http("POST", "/api/chats/notices/read", "{}") { _, _ -> }
        }
    }
    refreshChats()
    val stop = ChatInbox.listen { event ->
        if (shell.asDynamic().isConnected == false) return@listen
        val type = jsText(event.type)
        if (event.unreadCount != null && event.unreadCount != undefined) {
            ChatInbox.unread = num(event.unreadCount).toInt()
            ChatInbox.paintBadges()
        }
        if (type == "reconnect") {
            refreshChats()
            return@listen
        }
        if (type == "chat_deleted") {
            val id = jsText(event.chatId)
            list.asDynamic().querySelector("""[data-chat-id="$id"]""")?.remove()
            if (list.childElementCount == 0) paint(emptyList())
            return@listen
        }
        val chat = event.chat
        if (chat == null || chat == undefined) return@listen
        upsertChatRow(list, chatFrom(chat), router, selectedChatId)
    }
    messagesSelection = { id -> selectChat(id) }
    chatLeave = {
        stop()
        leaveChat?.invoke()
        leaveChat = null
        messagesSelection = null
    }
    selectChat(chatId)
    ChatInbox.connect()
    return shell
}

internal fun selectMessagesChat(chatId: String?) {
    messagesSelection?.invoke(chatId)
}

private fun chatScreen(router: HistoryRouter, chatId: String): Pair<HTMLElement, () -> Unit> {
    val shell = el("section", "chat-shell")
    var chatType = "direct"
    var owner = false
    val head = el("header", "page-head chat-head")
    head.appendChild(el("button", "icon-btn", text = "‹") { button ->
        button.setAttribute("type", "button")
        button.setAttribute("aria-label", Town.t("back"))
        button.onClick { router.navigate("/messages") }
    })
    val title = el("h1", "chat-title", text = Town.t("messages"))
    head.appendChild(title)
    val tools = el("div", "messages-head-actions")
    val membersBtn = el("button", "icon-btn")
    membersBtn.setAttribute("type", "button")
    membersBtn.setAttribute("aria-label", Town.t("chatMembers"))
    membersBtn.asDynamic().innerHTML = MEMBERS_ICON
    membersBtn.asDynamic().hidden = true
    tools.appendChild(membersBtn)
    tools.appendChild(el("button", "icon-btn") { button ->
        button.setAttribute("type", "button")
        button.setAttribute("aria-label", Town.t("chatMenu"))
        button.asDynamic().innerHTML = MENU_ICON
        button.onClick {
            openChatMenu(shell, router, chatId, title.textContent.orEmpty(), chatType == "group", owner) { name ->
                title.textContent = name
            }
        }
    })
    head.appendChild(tools)
    shell.appendChild(head)
    val thread = el("div", "chat-thread")
    val typing = el("div", "chat-typing")
    typing.asDynamic().hidden = true
    val composer = el("div", "chat-composer")
    val photoBtn = el("button", "icon-btn composer-photo")
    photoBtn.setAttribute("type", "button")
    photoBtn.setAttribute("aria-label", Town.t("sendPhoto"))
    photoBtn.asDynamic().innerHTML = PHOTO_ICON
    val picker = el("input")
    picker.setAttribute("type", "file")
    picker.setAttribute("accept", "image/*")
    picker.setAttribute("multiple", "multiple")
    picker.asDynamic().hidden = true
    val field = el("textarea", "chat-input")
    field.setAttribute("rows", "1")
    field.setAttribute("maxlength", Limits.CHAT_MESSAGE_MAX.toString())
    val send = el("button", "primary chat-send", text = Town.t("send"))
    send.setAttribute("type", "button")
    composer.appendChild(photoBtn)
    composer.appendChild(picker)
    composer.appendChild(field)
    composer.appendChild(send)
    shell.appendChild(thread)
    shell.appendChild(typing)
    shell.appendChild(composer)
    var cursor: String? = null
    var loading = false
    var atBottom = true
    var defaults = listOf("❤️", "👍", "😂", "😮", "😢")
    val pending = mutableListOf<dynamic>()
    val typingPeople = mutableMapOf<String, Pair<String, String?>>()
    val typingTimers = mutableMapOf<String, dynamic>()

    fun stick() {
        thread.asDynamic().scrollTop = thread.asDynamic().scrollHeight
    }

    fun paintTyping() {
        typing.clear()
        if (typingPeople.isEmpty()) {
            typing.asDynamic().hidden = true
            return
        }
        typing.asDynamic().hidden = false
        typingPeople.values.take(2).forEach { (name, photo) ->
            typing.appendChild(faceNode(name, photo, "face tiny"))
        }
        typing.appendChild(el("span", "typing-dots", text = "•••"))
    }

    fun stopTyping(userId: String) {
        val timer = typingTimers.remove(userId)
        if (timer != null && timer != undefined) window.asDynamic().clearTimeout(timer)
        typingPeople.remove(userId)
    }

    fun appendMessage(message: ChatMessage, prepend: Boolean = false) {
        val existing = thread.asDynamic().querySelector("""[data-message-id="${message.id}"]""")
        if (existing != null && existing != undefined) {
            applyMessageReactions(thread, chatId, message.id, message.reactions)
            return
        }
        val node = messageBubble(message, shell, chatId, owner, defaults, router)
        if (prepend) thread.asDynamic().insertBefore(node, thread.firstChild)
        else thread.appendChild(node)
    }

    fun loadLatestMessages(markRead: Boolean = true) {
        http("GET", "/api/chats/$chatId/messages?limit=${Limits.CHAT_MESSAGE_PAGE_SIZE}") { messageStatus, messageBody ->
            if (messageStatus != 200 || shell.asDynamic().isConnected == false) return@http
            val page = parseJson(messageBody)
            cursor = page.nextCursor as? String
            readMessages(page.messages).forEach { appendMessage(it) }
            if (atBottom) stick()
            if (markRead) {
                http("POST", "/api/chats/$chatId/read", "{}") { _, readBody ->
                    if (readBody.isNotBlank()) {
                        ChatInbox.unread = num(parseJson(readBody).unreadCount).toInt()
                        ChatInbox.paintBadges()
                    }
                }
            }
        }
    }

    fun loadOlder() {
        val next = cursor
        if (next.isNullOrBlank() || loading) return
        loading = true
        val previousHeight = num(thread.asDynamic().scrollHeight)
        http("GET", "/api/chats/$chatId/messages?limit=${Limits.CHAT_MESSAGE_PAGE_SIZE}&cursor=${encodeQuery(next)}") { status, body ->
            loading = false
            if (status != 200) return@http
            val parsed = parseJson(body)
            cursor = parsed.nextCursor as? String
            readMessages(parsed.messages).asReversed().forEach { appendMessage(it, prepend = true) }
            thread.asDynamic().scrollTop = num(thread.asDynamic().scrollHeight) - previousHeight
        }
    }

    fun sendMessage(text: String, blobs: List<dynamic>) {
        if (text.isBlank() && blobs.isEmpty()) return
        send.asDynamic().disabled = true
        if (blobs.isEmpty()) {
            val payload = js("({})")
            payload.text = text
            http("POST", "/api/chats/$chatId/messages", jsonStringify(payload)) { status, body ->
                send.removeAttribute("disabled")
                if (status !in 200..299) {
                    toast(shell, errorText(body, status))
                    return@http
                }
                field.asDynamic().value = ""
                appendMessage(messageFrom(parseJson(body)))
                stick()
            }
            return
        }
        val form = js("new FormData()")
        form.append("text", text)
        blobs.forEachIndexed { index, blob -> form.append("photo", blob, "photo$index.jpg") }
        http("POST", "/api/chats/$chatId/messages", form = form) { status, body ->
            send.removeAttribute("disabled")
            if (status !in 200..299) {
                toast(shell, errorText(body, status))
                return@http
            }
            field.asDynamic().value = ""
            pending.clear()
            appendMessage(messageFrom(parseJson(body)))
            stick()
        }
    }

    membersBtn.onClick { openChatMembers(shell, router, chatId, owner) }
    photoBtn.onClick { picker.asDynamic().click() }
    picker.asDynamic().onchange = {
        val files = picker.asDynamic().files
        val length = files?.length as? Int ?: 0
        val chosen = mutableListOf<dynamic>()
        var left = length.coerceAtMost(Limits.PHOTOS_MAX)
        if (left > 0) {
            for (i in 0 until length.coerceAtMost(Limits.PHOTOS_MAX)) {
                preparePhoto(files[i], false, { blob ->
                    chosen.add(blob)
                    left -= 1
                    if (left == 0) sendMessage((field.asDynamic().value as? String).orEmpty().trim(), chosen)
                }, {
                    left -= 1
                    if (left == 0 && chosen.isNotEmpty()) sendMessage((field.asDynamic().value as? String).orEmpty().trim(), chosen)
                })
            }
        }
        picker.asDynamic().value = ""
    }
    send.onClick {
        sendMessage((field.asDynamic().value as? String).orEmpty().trim(), emptyList())
    }
    field.asDynamic().onkeydown = { event: dynamic ->
        if (event.key == "Enter" && event.shiftKey != true) {
            event.preventDefault()
            send.asDynamic().click()
        }
    }
    var lastTyped = 0.0
    field.asDynamic().oninput = {
        val now = num(js("Date.now()"))
        if (now - lastTyped > 1500) {
            lastTyped = now
            http("POST", "/api/chats/$chatId/typing", "{}") { _, _ -> }
        }
    }
    thread.asDynamic().addEventListener("scroll", {
        val top = num(thread.asDynamic().scrollTop)
        val height = num(thread.asDynamic().scrollHeight) - num(thread.asDynamic().clientHeight)
        atBottom = height - top < 48
        if (top < 48) loadOlder()
    })

    http("GET", "/api/chats/$chatId") { status, body ->
        if (status != 200) {
            thread.appendChild(el("p", "hint", text = errorText(body, status)))
            return@http
        }
        val parsed = parseJson(body)
        val chat = chatFrom(parsed)
        title.textContent = chat.name
        chatType = chat.type
        owner = chat.owner
        membersBtn.asDynamic().hidden = chat.type != "group"
        defaults = readStringList(parsed.defaultReactions).ifEmpty { defaults }
        http("POST", "/api/chats/$chatId/read", "{}") { _, readBody ->
            if (readBody.isNotBlank()) {
                ChatInbox.unread = num(parseJson(readBody).unreadCount).toInt()
                ChatInbox.paintBadges()
            }
        }
        loadLatestMessages(markRead = false)
    }

    val stop = ChatInbox.listen { event ->
        if (shell.asDynamic().isConnected == false) return@listen
        val type = jsText(event.type)
        if (type == "reconnect") {
            loadLatestMessages()
            return@listen
        }
        if (type == "chat_deleted" && jsText(event.chatId) == chatId) {
            toast(shell, Town.t("chatDeleted"))
            router.navigate("/messages")
            return@listen
        }
        val incomingChat = event.chat
        if (incomingChat != null && incomingChat != undefined && jsText(incomingChat.id) == chatId) {
            val chat = chatFrom(incomingChat)
            title.textContent = chat.name
            chatType = chat.type
            owner = chat.owner
            membersBtn.asDynamic().hidden = chat.type != "group"
        }
        if (type == "typing" && jsText(event.chatId) == chatId) {
            val userId = jsText(event.userId)
            if (userId.isBlank() || userId == Town.me?.id) return@listen
            typingPeople[userId] = jsText(event.name) to jsText(event.photoUrl).takeIf { it.isNotBlank() }?.let(::prefixServerOrigin)
            paintTyping()
            val timer = typingTimers.remove(userId)
            if (timer != null && timer != undefined) window.asDynamic().clearTimeout(timer)
            typingTimers[userId] = window.asDynamic().setTimeout({
                typingTimers.remove(userId)
                typingPeople.remove(userId)
                paintTyping()
            }, 3000)
            return@listen
        }
        if (type == "message_deleted" && jsText(event.chatId) == chatId) {
            val id = jsText(event.messageId)
            thread.asDynamic().querySelector("""[data-message-id="$id"]""")?.remove()
            return@listen
        }
        if (type == "reaction") {
            val incomingMessage = event.message
            val messageChatId = if (incomingMessage == null || incomingMessage == undefined) "" else jsText(incomingMessage.chatId)
            val incomingChatId = jsText(event.chatId).ifBlank { messageChatId }
            if (incomingChatId == chatId) {
                val messageId = jsText(event.messageId).ifBlank {
                    if (incomingMessage == null || incomingMessage == undefined) "" else jsText(incomingMessage.id)
                }
                val reactions = readReactions(event.reactions).ifEmpty {
                    if (incomingMessage == null || incomingMessage == undefined) emptyList()
                    else messageFrom(incomingMessage).reactions
                }
                applyMessageReactions(thread, chatId, messageId, reactions)
            }
            return@listen
        }
        if (type == "message") {
            val message = event.message
            if (message == null || message == undefined) return@listen
            if (jsText(message.chatId) != chatId) return@listen
            val senderId = jsText(message.userId)
            if (senderId.isNotBlank()) {
                stopTyping(senderId)
                paintTyping()
            }
            appendMessage(messageFrom(message))
            if (atBottom || senderId != Town.me?.id) stick()
            http("POST", "/api/chats/$chatId/read", "{}") { _, readBody ->
                if (readBody.isNotBlank()) {
                    ChatInbox.unread = num(parseJson(readBody).unreadCount).toInt()
                    ChatInbox.paintBadges()
                }
            }
        }
    }
    val leave = {
        stop()
        typingTimers.values.forEach { timer -> window.asDynamic().clearTimeout(timer) }
        typingTimers.clear()
    }
    window.asDynamic().setTimeout({ field.asDynamic().focus() }, 30)
    return shell to leave
}

internal fun openDirectChat(
    host: HTMLElement,
    router: HistoryRouter,
    userId: String,
    name: String,
) {
    if (userId.isBlank() || userId == Town.me?.id) return
    if (Town.me == null) {
        showAuthDialog(host, onSuccess = { openDirectChat(host, router, userId, name) })
        return
    }
    http("GET", "/api/chats/direct/${encodeQuery(userId)}") { status, body ->
        if (status == 200) {
            val id = parseJson(body).id as String
            router.navigate("/messages/$id")
            return@http
        }
        openComposeDirect(host, router, userId, name)
    }
}

internal fun openAddToGroup(host: HTMLElement, userId: String, name: String) {
    if (userId.isBlank() || userId == Town.me?.id) return
    if (Town.me == null) {
        showAuthDialog(host, onSuccess = { openAddToGroup(host, userId, name) })
        return
    }
    if (host.asDynamic().querySelector(".add-to-group-dialog") != null) return
    val backdrop = el("div", "dialog-backdrop add-to-group-dialog")
    val card = el("div", "dialog")
    card.setAttribute("role", "dialog")
    card.setAttribute("aria-modal", "true")
    card.appendChild(el("h2", text = Town.t("addToGroup", mapOf("name" to name))))
    val search = el("input")
    search.setAttribute("type", "search")
    search.setAttribute("placeholder", Town.t("searchGroups"))
    search.setAttribute("aria-label", Town.t("searchGroups"))
    card.appendChild(search)
    val results = el("div", "place-results")
    card.appendChild(results)
    val actions = el("div", "dialog-actions")
    actions.appendChild(el("button", "text-button", text = Town.t("cancel")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    card.appendChild(actions)
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)

    val groups = mutableListOf<Pair<String, String>>()
    var groupsLoaded = false
    var loadError: String? = null

    fun render() {
        results.clear()
        if (!groupsLoaded) {
            results.appendChild(el("p", "hint", text = Town.t("loading")))
            return
        }
        if (groups.isEmpty()) {
            results.appendChild(el("p", "hint", text = loadError ?: Town.t("noGroups")))
            return
        }
        loadError?.let { results.appendChild(el("p", "hint", text = it)) }
        val query = (search.asDynamic().value as? String).orEmpty().trim()
        val matching = groups.filter { it.second.contains(query, ignoreCase = true) }
        if (matching.isEmpty()) {
            results.appendChild(el("p", "hint", text = Town.t("noGroupMatches")))
            return
        }
        matching.forEach { (chatId, groupName) ->
            val row = el("button", "chooser-row")
            row.setAttribute("type", "button")
            row.appendChild(el("strong", text = groupName))
            row.onClick {
                row.asDynamic().disabled = true
                val payload = js("({})")
                payload.userId = userId
                http("POST", "/api/chats/$chatId/members", jsonStringify(payload)) { status, body ->
                    if (status !in 200..299) {
                        row.asDynamic().disabled = false
                        val error = try {
                            parseJson(body).error as? String
                        } catch (_: Throwable) {
                            null
                        }
                        if (error == "already_member") {
                            toast(host, Town.t("alreadyInGroup", mapOf("name" to name)))
                        } else {
                            toast(host, errorText(body, status))
                        }
                        return@http
                    }
                    backdrop.asDynamic().remove()
                    toast(host, Town.t("addedToGroup", mapOf("name" to name, "group" to groupName)))
                }
            }
            results.appendChild(row)
        }
    }

    fun loadPage(cursor: String?) {
        val more = if (cursor.isNullOrBlank()) "" else "&cursor=${encodeQuery(cursor)}"
        http("GET", "/api/chats?type=group&limit=50$more") { status, body ->
            if (status !in 200..299) {
                loadError = errorText(body, status)
                groupsLoaded = true
                render()
                return@http
            }
            val page = parseJson(body)
            val chats = page.chats
            val length = chats?.length as? Int ?: 0
            for (i in 0 until length) {
                val chat = chats[i]
                val id = chat.id as? String ?: ""
                val groupName = chat.name as? String ?: ""
                if (id.isNotBlank()) groups += id to groupName
            }
            val nextCursor = page.nextCursor as? String
            if (!nextCursor.isNullOrBlank()) {
                loadPage(nextCursor)
            } else {
                groupsLoaded = true
                render()
            }
        }
    }

    search.asDynamic().oninput = { render() }
    render()
    loadPage(null)
    window.asDynamic().setTimeout({ search.asDynamic().focus() }, 30)
}

private fun openComposeDirect(host: HTMLElement, router: HistoryRouter, userId: String, name: String) {
    if (host.asDynamic().querySelector(".compose-direct") != null) return
    val backdrop = el("div", "dialog-backdrop compose-direct")
    val card = el("div", "dialog")
    card.setAttribute("role", "dialog")
    card.appendChild(el("h2", text = Town.t("messageUser", mapOf("name" to name))))
    card.appendChild(el("p", "hint", text = Town.t("firstDirectMessage", mapOf("name" to name))))
    val field = el("textarea")
    field.setAttribute("maxlength", Limits.CHAT_MESSAGE_MAX.toString())
    card.appendChild(field)
    val actions = el("div", "dialog-actions")
    actions.appendChild(el("button", "text-button", text = Town.t("cancel")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    val send = el("button", "primary", text = Town.t("send"))
    send.setAttribute("type", "button")
    send.onClick {
        val text = (field.asDynamic().value as? String).orEmpty().trim()
        if (text.isEmpty()) return@onClick
        send.asDynamic().disabled = true
        val payload = js("({})")
        payload.userId = userId
        payload.text = text
        http("POST", "/api/chats", jsonStringify(payload)) { status, body ->
            send.removeAttribute("disabled")
            if (status !in 200..299) {
                toast(host, errorText(body, status))
                return@http
            }
            backdrop.asDynamic().remove()
            router.navigate("/messages/${parseJson(body).id as String}")
        }
    }
    actions.appendChild(send)
    card.appendChild(actions)
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
    field.asDynamic().focus()
}

private fun openCreateGroup(host: HTMLElement, router: HistoryRouter) {
    if (host.asDynamic().querySelector(".create-group") != null) return
    val backdrop = el("div", "dialog-backdrop create-group")
    val card = el("div", "dialog")
    card.appendChild(el("h2", text = Town.t("newGroup")))
    val name = field(Town.t("groupName"), "text", Town.t("groupNameHint"))
    card.appendChild(name.root)
    val actions = el("div", "dialog-actions")
    actions.appendChild(el("button", "text-button", text = Town.t("cancel")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    val create = el("button", "primary", text = Town.t("createGroup"))
    create.setAttribute("type", "button")
    create.onClick {
        val payload = js("({})")
        payload.name = name.value()
        http("POST", "/api/chats", jsonStringify(payload)) { status, body ->
            if (status !in 200..299) {
                toast(host, errorText(body, status))
                return@http
            }
            backdrop.asDynamic().remove()
            router.navigate("/messages/${parseJson(body).id as String}")
        }
    }
    actions.appendChild(create)
    card.appendChild(actions)
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
    window.asDynamic().setTimeout({ name.input.asDynamic().focus() }, 30)
}

private fun openChatMenu(
    host: HTMLElement,
    router: HistoryRouter,
    chatId: String,
    title: String,
    group: Boolean,
    owner: Boolean,
    onRenamed: (String) -> Unit = {},
) {
    if (host.asDynamic().querySelector(".chat-menu") != null) return
    val backdrop = el("div", "dialog-backdrop chat-menu")
    val card = el("div", "dialog")
    card.appendChild(el("h2", text = title.ifBlank { Town.t("messages") }))
    card.appendChild(el("button", "text-button", text = Town.t("leaveChat")) { button ->
        button.setAttribute("type", "button")
        button.onClick {
            confirmAction(host, Town.t("confirmLeaveChat")) {
                http("POST", "/api/chats/$chatId/leave", "{}") { status, body ->
                    if (status != 200) {
                        toast(host, errorText(body, status))
                        return@http
                    }
                    backdrop.asDynamic().remove()
                    router.navigate("/messages")
                }
            }
        }
    })
    if (group && owner) {
        card.appendChild(el("button", "text-button", text = Town.t("renameGroup")) { button ->
            button.setAttribute("type", "button")
            button.onClick {
                backdrop.asDynamic().remove()
                openRenameGroup(host, chatId, title, onRenamed)
            }
        })
        card.appendChild(el("button", "text-button danger", text = Town.t("deleteGroup")) { button ->
            button.setAttribute("type", "button")
            button.onClick {
                confirmAction(host, Town.t("confirmDeleteGroup")) {
                    http("DELETE", "/api/chats/$chatId") { status, body ->
                        if (status != 200) {
                            toast(host, errorText(body, status))
                            return@http
                        }
                        backdrop.asDynamic().remove()
                        router.navigate("/messages")
                    }
                }
            }
        })
    }
    card.appendChild(el("button", "text-button", text = Town.t("close")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
}

private fun openRenameGroup(host: HTMLElement, chatId: String, current: String, onRenamed: (String) -> Unit = {}) {
    if (host.asDynamic().querySelector(".rename-group") != null) return
    val backdrop = el("div", "dialog-backdrop rename-group")
    val card = el("div", "dialog")
    card.appendChild(el("h2", text = Town.t("renameGroup")))
    val name = field(Town.t("groupName"), "text", Town.t("groupNameHint"))
    name.input.asDynamic().value = current
    name.input.setAttribute("maxlength", Limits.CHAT_NAME_MAX.toString())
    card.appendChild(name.root)
    val actions = el("div", "dialog-actions")
    actions.appendChild(el("button", "text-button", text = Town.t("cancel")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    val save = el("button", "primary", text = Town.t("okay"))
    save.setAttribute("type", "button")
    save.onClick {
        val payload = js("({})")
        payload.name = name.value()
        http("PATCH", "/api/chats/$chatId", jsonStringify(payload)) { status, body ->
            if (status != 200) {
                toast(host, errorText(body, status))
                return@http
            }
            onRenamed(chatFrom(parseJson(body)).name)
            backdrop.asDynamic().remove()
        }
    }
    actions.appendChild(save)
    card.appendChild(actions)
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
    window.asDynamic().setTimeout({ name.input.asDynamic().focus() }, 30)
}

private fun openChatMembers(host: HTMLElement, router: HistoryRouter, chatId: String, owner: Boolean) {
    if (host.asDynamic().querySelector(".chat-members") != null) return
    val backdrop = el("div", "dialog-backdrop chat-members")
    val card = el("div", "dialog")
    card.appendChild(el("h2", text = Town.t("chatMembers")))
    val search = el("input")
    search.setAttribute("type", "search")
    search.setAttribute("placeholder", Town.t("searchPeople"))
    card.appendChild(search)
    val results = el("div", "place-results")
    card.appendChild(results)
    val list = el("div", "member-list")
    card.appendChild(list)
    card.appendChild(el("button", "text-button", text = Town.t("close")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
    fun loadMembers() {
        http("GET", "/api/chats/$chatId/members") { status, body ->
            list.clear()
            if (status != 200) {
                list.appendChild(el("p", "hint", text = errorText(body, status)))
                return@http
            }
            val people = parseJson(body).members
            val length = people?.length as? Int ?: 0
            for (i in 0 until length) list.appendChild(chatMemberRow(people[i], host, router, chatId, owner, ::loadMembers))
        }
    }
    var timer: dynamic = null
    search.asDynamic().oninput = {
        val query = (search.asDynamic().value as? String).orEmpty()
        if (timer != null) window.asDynamic().clearTimeout(timer)
        timer = window.asDynamic().setTimeout({
            results.clear()
            if (query.isBlank()) return@setTimeout
            http("GET", "/api/chats/$chatId/people?q=${encodeQuery(query)}") { status, body ->
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
                    row.appendChild(el("strong", text = Town.t("addUser", mapOf("name" to (person.name as? String).orEmpty()))))
                    row.onClick {
                        val payload = js("({})")
                        payload.userId = person.id
                        http("POST", "/api/chats/$chatId/members", jsonStringify(payload)) { addStatus, addBody ->
                            if (addStatus !in 200..299) {
                                toast(host, errorText(addBody, addStatus))
                                return@http
                            }
                            search.asDynamic().value = ""
                            results.clear()
                            loadMembers()
                        }
                    }
                    results.appendChild(row)
                }
            }
        }, 220)
    }
    loadMembers()
}

private fun chatMemberRow(
    node: dynamic,
    host: HTMLElement,
    router: HistoryRouter,
    chatId: String,
    canManage: Boolean,
    reload: () -> Unit,
): HTMLElement {
    val person = eventPersonFrom(node)
    val row = el("div", "attendee-row")
    val profile = el("button", "attendee-profile")
    profile.setAttribute("type", "button")
    profile.appendChild(faceNode(person.name, person.photoUrl, "face"))
    val copy = el("span", "member-copy")
    copy.appendChild(el("strong", text = person.name))
    if (node.owner == true) copy.appendChild(el("small", text = Town.t("owner")))
    else if (person.location.isNotBlank()) copy.appendChild(el("small", text = person.location))
    profile.appendChild(copy)
    profile.onClick {
        openMiniProfile(host, person.name, person.photoUrl, person.location, person.intro, person.id, router)
    }
    row.appendChild(profile)
    if (!person.phone.isNullOrBlank()) {
        row.appendChild(
            iconAction(Town.t("copyPhone"), COPY_ICON) {
                try {
                    window.asDynamic().navigator.clipboard.writeText(person.phone)
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
    if (canManage && person.id != Town.me?.id) {
        val menu = el("button", "attendee-icon member-menu")
        menu.setAttribute("type", "button")
        menu.setAttribute("aria-label", Town.t("chatMenu"))
        menu.asDynamic().innerHTML = MENU_ICON
        menu.onClick { openMemberMenu(host, chatId, person.id, node.owner == true, reload) }
        row.appendChild(menu)
    }
    return row
}

private fun openMemberMenu(host: HTMLElement, chatId: String, userId: String, isOwner: Boolean, reload: () -> Unit) {
    if (host.asDynamic().querySelector(".member-menu-dialog") != null) return
    val backdrop = el("div", "dialog-backdrop member-menu-dialog")
    val card = el("div", "dialog")
    if (!isOwner) {
        card.appendChild(el("button", "text-button", text = Town.t("makeOwner")) { button ->
            button.setAttribute("type", "button")
            button.onClick {
                http("POST", "/api/chats/$chatId/members/$userId/owner", "{}") { status, body ->
                    if (status != 200) toast(host, errorText(body, status)) else {
                        backdrop.asDynamic().remove()
                        reload()
                    }
                }
            }
        })
    }
    card.appendChild(el("button", "text-button danger", text = Town.t("removeMember")) { button ->
        button.setAttribute("type", "button")
        button.onClick {
            http("DELETE", "/api/chats/$chatId/members/$userId") { status, body ->
                if (status != 200) toast(host, errorText(body, status)) else {
                    backdrop.asDynamic().remove()
                    reload()
                }
            }
        }
    })
    card.appendChild(el("button", "text-button", text = Town.t("close")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
}

private fun chatRow(chat: ChatCard, router: HistoryRouter, selectedChatId: String?): HTMLElement {
    val unread = chat.unreadCount > 0
    val classes = buildList {
        add("chat-row")
        if (unread) add("unread")
        if (chat.id == selectedChatId) add("selected")
    }.joinToString(" ")
    val row = el("button", classes)
    row.setAttribute("type", "button")
    row.setAttribute("data-chat-id", chat.id)
    row.appendChild(chatFaces(chat))
    val copy = el("span", "chat-row-copy")
    copy.appendChild(el(if (unread) "strong" else "span", "chat-row-name", text = chat.name))
    val preview = buildString {
        if (chat.lastMessageSenderName.isNotBlank()) {
            append(chat.lastMessageSenderName)
            append(": ")
        }
        append(chat.lastMessageText)
    }
    copy.appendChild(el("span", "chat-row-preview", text = preview))
    row.appendChild(copy)
    val meta = el("span", "chat-row-meta")
    meta.appendChild(el("em", if (unread) "chat-row-time unread" else "chat-row-time", text = relativeTime(chat.lastMessageAt)))
    if (unread) meta.appendChild(el("span", "chat-unread-count", text = chat.unreadCount.toString()))
    row.appendChild(meta)
    row.onClick { router.navigate("/messages/${chat.id}") }
    return row
}

private fun upsertChatRow(list: HTMLElement, chat: ChatCard, router: HistoryRouter, selectedChatId: String?) {
    list.asDynamic().querySelector(".hint")?.remove()
    list.asDynamic().querySelector("""[data-chat-id="${chat.id}"]""")?.remove()
    val row = chatRow(chat, router, selectedChatId)
    if (list.firstChild == null) list.appendChild(row) else list.asDynamic().insertBefore(row, list.firstChild)
}

private fun messageBubble(
    message: ChatMessage,
    host: HTMLElement,
    chatId: String,
    owner: Boolean,
    defaults: List<String>,
    router: HistoryRouter,
): HTMLElement {
    val mine = message.userId == Town.me?.id
    val bubble = el("div", if (mine) "chat-bubble mine" else "chat-bubble")
    bubble.setAttribute("data-message-id", message.id)
    if (!mine) {
        val meta = el("button", "chat-author")
        meta.setAttribute("type", "button")
        meta.appendChild(faceNode(message.authorName, message.authorPhotoUrl, "face tiny"))
        meta.appendChild(el("span", text = message.authorName))
        meta.asDynamic().addEventListener("click", { event: dynamic ->
            event.stopPropagation()
            openMiniProfile(host, message.authorName, message.authorPhotoUrl, "", "", message.userId)
        })
        bubble.appendChild(meta)
    }
    if (message.text.isNotBlank()) bubble.appendChild(linkedParagraph(message.text, "chat-text"))
    message.attachment?.let { attachment ->
        val card = el("button", if (attachment.type == "activity") "chat-product-attachment chat-activity-attachment" else "chat-product-attachment")
        card.setAttribute("type", "button")
        if (attachment.type == "activity") {
            card.setAttribute("aria-label", Town.t("viewActivity"))
            card.appendChild(el("span", "activity-emoji", text = attachment.emoji.ifBlank { "✨" }))
            val copy = el("span", "chat-product-attachment-copy")
            copy.appendChild(el("small", text = Town.t("activityAttachment")))
            copy.appendChild(el("strong", text = attachment.name))
            if (attachment.description.isNotBlank()) copy.appendChild(el("span", text = attachment.description))
            card.appendChild(copy)
            card.asDynamic().addEventListener("click", { event: dynamic ->
                event.stopPropagation()
                openActivitySheet(host, router, attachment.activityId)
            })
        } else {
            card.setAttribute("aria-label", Town.t("viewProduct"))
            if (!attachment.photoUrl.isNullOrBlank()) {
                val image = el("img", "chat-product-attachment-photo")
                image.setAttribute("src", attachment.photoUrl)
                image.setAttribute("alt", "")
                card.appendChild(image)
            }
            val copy = el("span", "chat-product-attachment-copy")
            copy.appendChild(el("small", text = Town.t("productAttachment")))
            copy.appendChild(el("strong", text = attachment.name))
            copy.appendChild(el("span", text = formatVnd(attachment.price)))
            card.appendChild(copy)
            card.asDynamic().addEventListener("click", { event: dynamic ->
                event.stopPropagation()
                http("GET", "/api/products/${encodeQuery(attachment.productId)}") { status, body ->
                    if (status == 200) openProductDetails(host, router, parseJson(body))
                    else toast(host, Town.t("unavailable"))
                }
            })
        }
        bubble.appendChild(card)
    }
    if (message.photoUrls.isNotEmpty()) {
        val photos = el("div", "chat-photos")
        message.photoUrls.forEach { url ->
            val img = el("img", "chat-photo")
            img.setAttribute("alt", "")
            img.setAttribute("src", url)
            photos.appendChild(img)
        }
        bubble.appendChild(photos)
    }
    val reactions = el("div", "chat-reactions")
    paintReactionRow(reactions, message.reactions, chatId, message.id, host)
    bubble.appendChild(reactions)
    bubble.onClick {
        openMessageMenu(host, chatId, message, owner || mine, defaults)
    }
    return bubble
}

private fun applyMessageReactions(
    root: HTMLElement,
    chatId: String,
    messageId: String,
    reactions: List<ReactionChip>,
) {
    if (messageId.isBlank()) return
    val selector = """[data-message-id="$messageId"]"""
    val bubble = root.asDynamic().querySelector(selector)
        ?: web.dom.document.asDynamic().querySelector(selector)
        ?: return
    paintReactionRow(bubble.querySelector(".chat-reactions"), reactions, chatId, messageId, root)
}

private fun paintReactionRow(
    host: dynamic,
    reactions: List<ReactionChip>,
    chatId: String,
    messageId: String,
    root: HTMLElement,
) {
    if (host == null || host == undefined) return
    host.innerHTML = ""
    reactions.forEach { reaction ->
        val chip = el("button", if (reaction.mine) "reaction-chip mine" else "reaction-chip")
        chip.setAttribute("type", "button")
        chip.textContent = "${reaction.emoji} ${reaction.count}"
        chip.asDynamic().addEventListener("click", { event: dynamic ->
            event.stopPropagation()
            openReactionsDialog(root, chatId, messageId)
        })
        host.appendChild(chip)
    }
}

private fun openMessageMenu(
    host: HTMLElement,
    chatId: String,
    message: ChatMessage,
    canDelete: Boolean,
    defaults: List<String>,
) {
    if (host.asDynamic().querySelector(".message-menu") != null) return
    val backdrop = el("div", "dialog-backdrop message-menu")
    val card = el("div", "dialog")
    val row = el("div", "reaction-defaults")
    fun postReaction(emoji: String) {
        val payload = js("({})")
        payload.emoji = emoji
        http("POST", "/api/chats/$chatId/messages/${message.id}/reactions", jsonStringify(payload)) { status, body ->
            if (status != 200) {
                toast(host, errorText(body, status))
                return@http
            }
            applyMessageReactions(host, chatId, message.id, readReactions(parseJson(body).reactions))
            backdrop.asDynamic().remove()
        }
    }
    defaults.forEach { emoji ->
        row.appendChild(el("button", "reaction-pick", text = emoji) { button ->
            button.setAttribute("type", "button")
            button.onClick { postReaction(emoji) }
        })
    }
    val customWrap = el("div", "reaction-custom")
    customWrap.asDynamic().hidden = true
    val custom = field(Town.t("customReaction"), "text", Town.t("reactionHint"))
    custom.input.setAttribute("maxlength", Limits.CHAT_REACTION_MAX.toString())
    customWrap.appendChild(custom.root)
    customWrap.appendChild(el("button", "primary", text = Town.t("send")) { button ->
        button.setAttribute("type", "button")
        button.onClick {
            val emoji = custom.value().trim()
            if (emoji.isEmpty()) return@onClick
            postReaction(emoji)
        }
    })
    row.appendChild(el("button", "reaction-pick") { button ->
        button.setAttribute("type", "button")
        button.setAttribute("aria-label", Town.t("customReaction"))
        button.asDynamic().innerHTML = PLUS_ICON
        button.onClick {
            customWrap.asDynamic().hidden = false
            button.asDynamic().hidden = true
            window.asDynamic().setTimeout({ custom.input.asDynamic().focus() }, 30)
        }
    })
    card.appendChild(row)
    card.appendChild(customWrap)
    if (canDelete) {
        card.appendChild(el("button", "text-button danger", text = Town.t("delete")) { button ->
            button.setAttribute("type", "button")
            button.onClick {
                backdrop.asDynamic().remove()
                confirmAction(host, Town.t("confirmRemoveMessage")) {
                    http("DELETE", "/api/chats/$chatId/messages/${message.id}") { status, body ->
                        if (status != 200) toast(host, errorText(body, status))
                    }
                }
            }
        })
    }
    card.appendChild(el("button", "text-button", text = Town.t("close")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
}

private fun openReactionsDialog(host: HTMLElement, chatId: String, messageId: String) {
    if (host.asDynamic().querySelector(".reactions-dialog") != null) return
    val backdrop = el("div", "dialog-backdrop reactions-dialog")
    val card = el("div", "dialog")
    card.appendChild(el("h2", text = Town.t("reactions")))
    val list = el("div", "member-list")
    card.appendChild(list)
    card.appendChild(el("button", "text-button", text = Town.t("close")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
    http("GET", "/api/chats/$chatId/messages/$messageId/reactions") { status, body ->
        if (status != 200) {
            list.appendChild(el("p", "hint", text = errorText(body, status)))
            return@http
        }
        val reactions = parseJson(body).reactions
        val length = reactions?.length as? Int ?: 0
        if (length == 0) {
            list.appendChild(el("p", "hint", text = Town.t("noPeople")))
            return@http
        }
        for (i in 0 until length) {
            val item = reactions[i]
            val row = el("div", "attendee-row")
            row.appendChild(faceNode((item.name as? String).orEmpty(), (item.photoUrl as? String)?.let(::prefixServerOrigin), "face"))
            val copy = el("span", "member-copy")
            copy.appendChild(el("strong", text = "${item.emoji} ${(item.name as? String).orEmpty()}"))
            row.appendChild(copy)
            if (item.mine == true) {
                row.appendChild(el("button", "text-button", text = Town.t("removeReaction")) { button ->
                    button.setAttribute("type", "button")
                    button.onClick {
                        val payload = js("({})")
                        payload.emoji = item.emoji
                        http("DELETE", "/api/chats/$chatId/messages/$messageId/reactions", jsonStringify(payload)) { status, body ->
                            if (status == 200) {
                                applyMessageReactions(host, chatId, messageId, readReactions(parseJson(body).reactions))
                            }
                            backdrop.asDynamic().remove()
                        }
                    }
                })
            }
            list.appendChild(row)
        }
    }
}

private fun confirmAction(host: HTMLElement, message: String, onYes: () -> Unit) {
    if (host.asDynamic().querySelector(".confirm-dialog") != null) return
    val backdrop = el("div", "dialog-backdrop confirm-dialog")
    val card = el("div", "dialog")
    card.appendChild(el("p", text = message))
    val actions = el("div", "dialog-actions")
    actions.appendChild(el("button", "text-button", text = Town.t("cancel")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    actions.appendChild(el("button", "primary", text = Town.t("okay")) { button ->
        button.setAttribute("type", "button")
        button.onClick {
            backdrop.asDynamic().remove()
            onYes()
        }
    })
    card.appendChild(actions)
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
}

private fun chatFaces(chat: ChatCard): HTMLElement {
    val stack = el("span", if (chat.photos.size >= 2) "chat-faces pair" else "chat-faces")
    chat.photos.take(2).forEach { face ->
        stack.appendChild(faceNode(face.name, face.photoUrl, "face"))
    }
    if (chat.photos.isEmpty()) stack.appendChild(el("span", "face initial", text = chat.name.take(1).uppercase()))
    return stack
}

private fun faceNode(name: String, photoUrl: String?, className: String): HTMLElement {
    return if (!photoUrl.isNullOrBlank()) {
        el("span", className) { face ->
            val escaped = photoUrl.replace("\\", "\\\\").replace("\"", "\\\"")
            face.asDynamic().style.backgroundImage = "url(\"$escaped\")"
        }
    } else {
        el("span", "$className initial", text = name.take(1).uppercase())
    }
}

private fun iconAction(label: String, svg: String, onClick: () -> Unit): HTMLElement {
    val button = el("button", "attendee-icon")
    button.setAttribute("type", "button")
    button.setAttribute("aria-label", label)
    button.asDynamic().innerHTML = svg
    button.onClick(onClick)
    return button
}

private data class ChatCard(
    val id: String,
    val type: String,
    val name: String,
    val photos: List<ChatFaceCard>,
    val lastMessageSenderName: String,
    val lastMessageText: String,
    val lastMessageAt: Long,
    val unreadCount: Int,
    val owner: Boolean,
)

private data class ChatFaceCard(val userId: String, val name: String, val photoUrl: String?)

private data class ChatMessage(
    val id: String,
    val chatId: String,
    val userId: String,
    val authorName: String,
    val authorPhotoUrl: String?,
    val text: String,
    val photoUrls: List<String>,
    val createdAt: Long,
    val reactions: List<ReactionChip>,
    val attachment: ChatAttachmentCard?,
)

private data class ChatAttachmentCard(
    val type: String,
    val productId: String = "",
    val name: String = "",
    val price: Long = 0,
    val photoUrl: String? = null,
    val activityId: String = "",
    val emoji: String = "",
    val description: String = "",
)

private data class ReactionChip(val emoji: String, val count: Int, val mine: Boolean)

private fun chatFrom(node: dynamic) = ChatCard(
    id = (node.id as? String).orEmpty(),
    type = (node.type as? String).orEmpty(),
    name = (node.name as? String).orEmpty(),
    photos = readFaces(node.photos),
    lastMessageSenderName = (node.lastMessageSenderName as? String).orEmpty(),
    lastMessageText = (node.lastMessageText as? String).orEmpty(),
    lastMessageAt = num(node.lastMessageAt).toLong(),
    unreadCount = num(node.unreadCount).toInt(),
    owner = node.owner == true,
)

private fun readChats(node: dynamic): List<ChatCard> {
    val items = mutableListOf<ChatCard>()
    if (node == null || node == undefined) return items
    val length = node.length as? Int ?: return items
    for (i in 0 until length) items += chatFrom(node[i])
    return items
}

private fun readFaces(node: dynamic): List<ChatFaceCard> {
    val items = mutableListOf<ChatFaceCard>()
    if (node == null || node == undefined) return items
    val length = node.length as? Int ?: return items
    for (i in 0 until length) {
        val face = node[i]
        items += ChatFaceCard(
            userId = (face.userId as? String).orEmpty(),
            name = (face.name as? String).orEmpty(),
            photoUrl = (face.photoUrl as? String)?.let(::prefixServerOrigin),
        )
    }
    return items
}

private fun messageFrom(node: dynamic) = ChatMessage(
    id = (node.id as? String).orEmpty(),
    chatId = (node.chatId as? String).orEmpty(),
    userId = (node.userId as? String).orEmpty(),
    authorName = (node.authorName as? String).orEmpty(),
    authorPhotoUrl = (node.authorPhotoUrl as? String)?.let(::prefixServerOrigin),
    text = (node.text as? String).orEmpty(),
    photoUrls = readStringList(node.photoUrls).map(::prefixServerOrigin),
    createdAt = num(node.createdAt).toLong(),
    reactions = readReactions(node.reactions),
    attachment = chatAttachmentFrom(node.attachment),
)

private fun chatAttachmentFrom(node: dynamic): ChatAttachmentCard? {
    if (node == null || node == undefined) return null
    val type = node.type as? String ?: return null
    return when (type) {
        "activity" -> ChatAttachmentCard(
            type = type,
            name = (node.activityName as? String).orEmpty(),
            activityId = (node.activityId as? String).orEmpty(),
            emoji = (node.activityEmoji as? String).orEmpty(),
            description = (node.activityDescription as? String).orEmpty(),
        )
        "product" -> ChatAttachmentCard(
            type = type,
            productId = (node.productId as? String).orEmpty(),
            name = (node.productName as? String).orEmpty(),
            price = num(node.price).toLong(),
            photoUrl = (node.photoUrl as? String)?.let(::prefixServerOrigin),
        )
        else -> null
    }
}

private fun readMessages(node: dynamic): List<ChatMessage> {
    val items = mutableListOf<ChatMessage>()
    if (node == null || node == undefined) return items
    val length = node.length as? Int ?: return items
    for (i in 0 until length) items += messageFrom(node[i])
    return items
}

private fun readReactions(node: dynamic): List<ReactionChip> {
    val items = mutableListOf<ReactionChip>()
    if (node == null || node == undefined) return items
    val length = num(node.length).toInt()
    if (length <= 0) return items
    for (i in 0 until length) {
        val item = node[i]
        items += ReactionChip(
            emoji = jsText(item.emoji),
            count = num(item.count).toInt(),
            mine = item.mine == true,
        )
    }
    return items
}

private fun jsText(value: dynamic): String {
    if (value == null || value == undefined) return ""
    val text = value as? String
    if (text != null) return text
    return (js("String")(value) as? String).orEmpty()
}
