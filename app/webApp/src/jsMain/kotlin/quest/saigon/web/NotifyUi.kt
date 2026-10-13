package quest.saigon.web

import web.html.HTMLElement
import web.window.window

internal object NotifyInbox {
    var unread = 0
        private set
    private var stopStream: (() -> Unit)? = null

    fun connect() {
        if (Town.me == null) {
            disconnect()
            return
        }
        stopStream?.invoke()
        stopStream = null
        refresh()
        stopStream = openNotificationStream { _ ->
            unread += 1
            paintBadges()
            playNotifySound()
        }
    }

    fun disconnect() {
        stopStream?.invoke()
        stopStream = null
        unread = 0
        paintBadges()
    }

    fun refresh() {
        if (Town.me == null) return
        http("GET", "/api/notifications?limit=1") { status, body ->
            if (status != 200) return@http
            unread = num(parseJson(body).unreadCount).toInt()
            paintBadges()
        }
    }

    fun markRead(id: String, then: () -> Unit = {}) {
        http("POST", "/api/notifications/$id/read", "{}") { status, _ ->
            if (status == 200 && unread > 0) unread -= 1
            paintBadges()
            then()
        }
    }

    fun markAllRead(then: () -> Unit = {}) {
        http("POST", "/api/notifications/read-all", "{}") { status, _ ->
            if (status == 200) unread = 0
            paintBadges()
            then()
        }
    }

    fun paintBadges() {
        val nodes = web.dom.document.asDynamic().querySelectorAll(".notify-badge")
        val length = nodes.length as Int
        for (i in 0 until length) {
            val badge = nodes[i]
            badge.textContent = if (unread > 0) unread.toString() else ""
            badge.hidden = unread <= 0
        }
        val buttons = web.dom.document.asDynamic().querySelectorAll(".notify-btn")
        val count = buttons.length as Int
        for (i in 0 until count) {
            val button = buttons[i]
            if (unread > 0) button.classList.add("has-unread") else button.classList.remove("has-unread")
        }
    }
}

internal fun notificationButton(router: HistoryRouter, host: HTMLElement): HTMLElement {
    val button = el("button", "notify-btn")
    button.setAttribute("type", "button")
    button.setAttribute("aria-label", Town.t("openNotifications"))
    button.asDynamic().innerHTML = """
        <svg viewBox="0 0 24 24" aria-hidden="true">
            <path fill="currentColor" d="M12 22a2.2 2.2 0 0 0 2.2-2.2H9.8A2.2 2.2 0 0 0 12 22zm7-6.2V11a7 7 0 0 0-5-6.7V3.8a2 2 0 1 0-4 0v.5A7 7 0 0 0 5 11v4.8L3 18v1h18v-1l-2-2.2z"/>
        </svg>
    """.trimIndent()
    val badge = el("span", "notify-badge")
    badge.asDynamic().hidden = NotifyInbox.unread <= 0
    badge.textContent = if (NotifyInbox.unread > 0) NotifyInbox.unread.toString() else ""
    button.appendChild(badge)
    button.onClick { openNotifications(router, host) }
    return button
}

internal fun openNotifications(router: HistoryRouter, host: HTMLElement) {
    if (host.asDynamic().querySelector(".notify-sheet") != null) return
    val backdrop = el("div", "dialog-backdrop notify-sheet")
    val card = el("div", "dialog notify-dialog")
    val head = el("div", "notify-head")
    head.appendChild(el("h2", text = Town.t("notifications")))
    val markAll = el("button", "text-button mark-all-read", text = Town.t("markAllRead"))
    markAll.setAttribute("type", "button")
    markAll.setAttribute("hidden", "hidden")
    head.appendChild(markAll)
    card.appendChild(head)
    val list = el("div", "notify-list")
    card.appendChild(list)
    card.appendChild(el("button", "text-button", text = Town.t("close")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
    http("GET", "/api/notifications") { status, body ->
        if (status != 200) {
            list.appendChild(el("p", "hint", text = errorText(body, status)))
            return@http
        }
        val parsed = parseJson(body)
        NotifyInbox.refresh()
        val notes = parsed.notifications
        val length = notes.length as Int
        var hasUnread = false
        for (i in 0 until length) {
            if (notes[i].read != true) hasUnread = true
        }
        if (hasUnread) {
            markAll.removeAttribute("hidden")
            markAll.onClick {
                NotifyInbox.markAllRead {
                    backdrop.asDynamic().remove()
                    openNotifications(router, host)
                }
            }
        }
        if (length == 0) {
            list.appendChild(el("p", "hint", text = Town.t("noNotifications")))
            return@http
        }
        for (i in 0 until length) {
            val note = notes[i]
            val read = note.read == true
            val item = el("button", if (read) "notify-item" else "notify-item unread")
            item.setAttribute("type", "button")
            val actor = (note.actorName as? String).orEmpty()
            val preview = (note.preview as? String).orEmpty()
            val title = when (note.type as? String) {
                "thread_comment" -> Town.t("notificationThreadComment", mapOf("name" to actor))
                "event_joined" -> Town.t("notificationEventJoined", mapOf("name" to actor))
                "event_left" -> Town.t("notificationEventLeft", mapOf("name" to actor))
                "event_friend_joined" -> Town.t("notificationEventFriendJoined", mapOf("name" to actor))
                "event_starting_soon" -> Town.t("notificationEventStartingSoon", mapOf("event" to preview))
                "event_starting_now" -> Town.t("notificationEventStartingNow", mapOf("event" to preview))
                else -> Town.t("notificationPostComment", mapOf("name" to actor))
            }
            item.appendChild(el("strong", text = title))
            item.appendChild(el("span", text = (note.preview as? String).orEmpty()))
            item.appendChild(el("em", text = relativeTime(num(note.createdAt).toLong())))
            item.onClick {
                val id = note.id as String
                val postId = note.postId as String
                NotifyInbox.markRead(id) {
                    backdrop.asDynamic().remove()
                    router.navigate("/p/$postId")
                }
            }
            list.appendChild(item)
        }
    }
}

internal fun playNotifySound() {
    try {
        val ctx = js("new (window.AudioContext || window.webkitAudioContext)()")
        val osc = ctx.createOscillator()
        val gain = ctx.createGain()
        osc.type = "sine"
        osc.frequency.value = 880
        gain.gain.setValueAtTime(0.0001, ctx.currentTime)
        gain.gain.exponentialRampToValueAtTime(0.08, ctx.currentTime + 0.02)
        gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.16)
        osc.connect(gain)
        gain.connect(ctx.destination)
        osc.start()
        osc.stop(ctx.currentTime + 0.18)
    } catch (_: Throwable) {
    }
}

internal fun openNotificationStream(onEvent: (dynamic) -> Unit): () -> Unit {
    var stopped = false
    var abort = js("new AbortController()")
    var retryTimer: dynamic = null
    var start: () -> Unit = {}
    fun clearRetry() {
        if (retryTimer != null && retryTimer != undefined) {
            window.asDynamic().clearTimeout(retryTimer)
        }
        retryTimer = null
    }
    fun retry() {
        if (stopped) return
        clearRetry()
        retryTimer = window.asDynamic().setTimeout({
            retryTimer = null
            start()
        }, 1200)
    }
    start = start@{
        if (stopped) return@start
        abort = js("new AbortController()")
        val init = js("({})")
        init.method = "GET"
        init.credentials = "include"
        init.signal = abort.signal
        val headers = js("({})")
        headers["Accept"] = "text/event-stream"
        init.headers = headers
        window.asDynamic().fetch(prefixServerOrigin("/api/notifications/stream"), init).then({ response: dynamic ->
            if (stopped) return@then
            if (response.ok != true) {
                retry()
                return@then
            }
            val reader = response.body?.getReader()
            if (reader == null || reader == undefined) {
                retry()
                return@then
            }
            val decoder = js("new TextDecoder()")
            var buffer = ""
            fun pump() {
                if (stopped) return
                reader.read().then({ chunk: dynamic ->
                    if (stopped) return@then
                    if (chunk.done == true) {
                        retry()
                        return@then
                    }
                    buffer += decoder.decode(chunk.value) as String
                    val parts = buffer.split("\n\n")
                    buffer = parts.last()
                    for (i in 0 until parts.size - 1) {
                        val block = parts[i]
                        val line = block.lines().firstOrNull { it.startsWith("data:") } ?: continue
                        val json = line.removePrefix("data:").trim()
                        if (json.isBlank() || json == "{}") continue
                        try {
                            onEvent(parseJson(json))
                        } catch (_: Throwable) {
                        }
                    }
                    pump()
                }, { _: dynamic -> retry() })
            }
            pump()
        }, { _: dynamic -> retry() })
    }
    start()
    return {
        stopped = true
        clearRetry()
        try {
            abort.abort()
        } catch (_: Throwable) {
        }
    }
}
