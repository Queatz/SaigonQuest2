package quest.saigon.web

import quest.saigon.Copy
import quest.saigon.Limits
import web.html.HTMLElement
import web.window.window

private val chatTimers = mutableListOf<dynamic>()

internal fun clearChatTimers() {
    chatTimers.forEach { timer ->
        window.asDynamic().clearInterval(timer)
    }
    chatTimers.clear()
}

internal fun repliesFor(post: PostCard): List<String> =
    post.quickReplies.filter { it.isNotBlank() }.ifEmpty { Copy.quickReplies(Town.language) }.take(Limits.QUICK_REPLY_COUNT)

internal fun collapsedChat(
    post: PostCard,
    host: HTMLElement,
    router: HistoryRouter,
    onUpdate: (PostCard) -> Unit,
): HTMLElement {
    var current = post
    var draft = ""
    var replyTo: CommentCard? = null
    val box = el("div", "collapsed-chat")
    val preview = el("button", "collapsed-preview")
    preview.setAttribute("type", "button")
    val face = el("span", "collapsed-face")
    face.setAttribute("hidden", "hidden")
    val previewText = el("span", "collapsed-preview-text")
    val count = el("span", "comment-count")
    preview.appendChild(face)
    preview.appendChild(previewText)
    preview.appendChild(count)
    val chips = el("div", "quick-replies")
    val row = el("div", "card-actions")
    val input = el("button", "comment-input")
    input.setAttribute("type", "button")
    row.appendChild(input)
    if (post.authorId.isNotBlank() && post.authorId != Town.me?.id) {
        row.appendChild(messagePostButton(post, host, router))
    }
    row.appendChild(shareButton(post, host))
    box.appendChild(preview)
    box.appendChild(chips)
    box.appendChild(row)

    fun paintFace(comment: CommentCard?) {
        face.clear()
        if (comment == null) {
            face.setAttribute("hidden", "hidden")
            return
        }
        face.removeAttribute("hidden")
        if (!comment.authorPhotoUrl.isNullOrBlank()) {
            val img = el("img")
            img.setAttribute("alt", "")
            img.setAttribute("src", comment.authorPhotoUrl)
            face.appendChild(img)
        } else {
            face.textContent = comment.authorName.take(1).uppercase()
        }
    }

    fun paintPreview(index: Int, fade: Boolean) {
        val comments = current.previewComments
        count.textContent = if (current.commentCount > 0) current.commentCount.toString() else ""
        val comment = comments.getOrNull(if (comments.isEmpty()) -1 else index % comments.size)
        fun apply() {
            paintFace(comment)
            previewText.clear()
            if (comment == null) {
                previewText.textContent = Town.t("beFirstComment", mapOf("name" to current.authorName))
            } else {
                previewText.appendChild(el("strong", text = comment.authorName))
                previewText.appendChild(el("span", text = " ${comment.text}"))
            }
            face.asDynamic().style.opacity = "1"
            previewText.asDynamic().style.opacity = "1"
        }
        if (!fade) {
            apply()
            return
        }
        face.asDynamic().style.opacity = "0"
        previewText.asDynamic().style.opacity = "0"
        window.asDynamic().setTimeout({ apply() }, 160)
    }

    fun paintPlaceholder(index: Int) {
        if (draft.isNotBlank()) {
            input.textContent = draft
            return
        }
        val replies = repliesFor(current)
        input.textContent = replies[index % replies.size]
    }

    var paintChips: () -> Unit = {}
    fun openComments(focusInput: Boolean) {
        if (focusInput && Town.me == null) {
            showAuthDialog(host, onSuccess = {
                openCommentsSheet(current, host, router, draft, replyTo, focusInput) { next ->
                    current = next
                    onUpdate(next)
                    paintPreview(0, false)
                    paintChips()
                }
            })
            return
        }
        openCommentsSheet(current, host, router, draft, replyTo, focusInput) { next ->
            current = next
            onUpdate(next)
            paintPreview(0, false)
            paintChips()
        }
    }
    paintChips = {
        chips.clear()
        repliesFor(current).forEach { reply ->
            chips.appendChild(el("button", "quick-chip", text = reply) { chip ->
                chip.setAttribute("type", "button")
                chip.onClick {
                    draft = reply
                    input.textContent = reply
                    openComments(focusInput = true)
                }
            })
        }
    }

    var commentIndex = 0
    var replyIndex = 0
    paintPreview(0, false)
    paintPlaceholder(0)
    paintChips()
    val timer = window.asDynamic().setInterval({
        val comments = current.previewComments
        if (comments.size > 1) {
            commentIndex = (commentIndex + 1) % comments.size
            paintPreview(commentIndex, true)
        }
        if (draft.isBlank()) {
            val replies = repliesFor(current)
            if (replies.isNotEmpty()) {
                replyIndex = (replyIndex + 1) % replies.size
                paintPlaceholder(replyIndex)
            }
        }
    }, Limits.COMMENT_CYCLE_MILLIS)
    chatTimers.add(timer)
    preview.onClick { openComments(focusInput = false) }
    input.onClick { openComments(focusInput = true) }
    return box
}

internal fun openCommentsSheet(
    post: PostCard,
    host: HTMLElement,
    router: HistoryRouter,
    initialDraft: String = "",
    initialReply: CommentCard? = null,
    focusInput: Boolean = false,
    onUpdate: (PostCard) -> Unit,
) {
    if (host.asDynamic().querySelector(".comment-sheet") != null) return
    var current = post
    var cursor: String? = null
    var loading = false
    var done = false
    var draft = initialDraft
    var replyTo: CommentCard? = initialReply
    var placeholderIndex = 0
    var placeholderTimer: dynamic = null
    val comments = current.previewComments.toMutableList()
    fun stopPlaceholder() {
        if (placeholderTimer != null) {
            window.asDynamic().clearInterval(placeholderTimer)
            placeholderTimer = null
        }
    }
    val chrome = SheetChrome("comment-sheet", Town.t("comments")) { stopPlaceholder() }
    val sheet = chrome.sheet
    val list = el("div", "comment-list")
    val composer = el("div", "comment-composer")
    val chips = el("div", "quick-replies sheet-replies")
    val replyBar = el("div", "reply-target")
    replyBar.setAttribute("hidden", "hidden")
    val row = el("div", "comment-input-row")
    val field = el("input", "comment-field")
    field.setAttribute("type", "text")
    field.setAttribute("maxlength", Limits.COMMENT_MAX.toString())
    if (draft.isNotBlank()) field.asDynamic().value = draft
    val send = el("button", "comment-send", text = Town.t("sendComment"))
    send.setAttribute("type", "button")
    row.appendChild(field)
    row.appendChild(send)
    composer.appendChild(chips)
    composer.appendChild(replyBar)
    composer.appendChild(row)
    sheet.appendChild(list)
    sheet.appendChild(composer)

    fun paintPlaceholder() {
        val typed = (field.asDynamic().value as? String).orEmpty()
        if (typed.isNotBlank() || field.asDynamic() == window.asDynamic().document.activeElement) return
        val replies = repliesFor(current)
        if (replies.isEmpty()) return
        field.setAttribute("placeholder", replies[placeholderIndex % replies.size])
    }

    fun startPlaceholder() {
        stopPlaceholder()
        paintPlaceholder()
        placeholderTimer = window.asDynamic().setInterval({
            val replies = repliesFor(current)
            if (replies.isNotEmpty()) {
                placeholderIndex = (placeholderIndex + 1) % replies.size
                paintPlaceholder()
            }
        }, Limits.COMMENT_CYCLE_MILLIS)
    }

    fun paintReplyTarget() {
        replyBar.clear()
        val target = replyTo
        if (target == null) {
            replyBar.setAttribute("hidden", "hidden")
            chips.removeAttribute("hidden")
            return
        }
        chips.setAttribute("hidden", "hidden")
        replyBar.removeAttribute("hidden")
        val quote = el("div", "reply-target-quote")
        quote.appendChild(el("strong", text = Town.t("replyingTo", mapOf("name" to target.authorName))))
        quote.appendChild(el("span", text = target.text))
        replyBar.appendChild(quote)
        replyBar.appendChild(el("button", "reply-target-clear", text = "×") { button ->
            button.setAttribute("type", "button")
            button.setAttribute("aria-label", Town.t("cancel"))
            button.onClick {
                replyTo = null
                paintReplyTarget()
            }
        })
    }

    fun paintEmpty() {
        val existing = list.asDynamic().querySelector(".comment-empty")
        if (comments.isEmpty()) {
            if (existing == null) {
                list.appendChild(el("p", "comment-empty", text = Town.t("emptyComments")))
            }
        } else {
            existing?.remove()
        }
    }

    fun canDelete(comment: CommentCard): Boolean {
        val me = Town.me ?: return false
        return me.id == comment.userId || me.id == current.authorId || me.admin
    }

    fun forget(commentId: String) {
        comments.removeAll { it.id == commentId }
        current = current.copy(
            commentCount = (current.commentCount - 1).coerceAtLeast(0),
            previewComments = comments.take(Limits.COMMENT_PREVIEW),
        )
        onUpdate(current)
        list.asDynamic().querySelector("[data-comment='$commentId']")?.remove()
        paintEmpty()
        if (replyTo?.id == commentId) {
            replyTo = null
            paintReplyTarget()
        }
    }

    fun askDelete(comment: CommentCard) {
        confirmDialog(host, Town.t("deleteCommentAsk"), Town.t("deleteCommentHint")) {
            http("DELETE", "/api/posts/${current.id}/comments/${comment.id}") { status, body ->
                if (status == 200) forget(comment.id)
                else toast(host, errorText(body, status))
            }
        }
    }

    fun renderItem(comment: CommentCard): HTMLElement {
        val item = el("div", "comment-item")
        val face = if (!comment.authorPhotoUrl.isNullOrBlank()) {
            val img = el("img", "comment-face")
            img.setAttribute("alt", "")
            img.setAttribute("src", comment.authorPhotoUrl)
            img
        } else {
            el("span", "comment-face initial", text = comment.authorName.take(1).uppercase())
        }
        face.setAttribute("role", "button")
        face.setAttribute("tabindex", "0")
        face.asDynamic().addEventListener("click", { event: dynamic ->
            event.stopPropagation()
            openMiniProfile(
                host,
                comment.authorName,
                comment.authorPhotoUrl,
                comment.authorLocation,
                comment.authorIntro,
                comment.userId,
                router,
            )
        })
        item.appendChild(face)
        val body = el("div", "comment-body")
        val meta = el("div", "comment-meta")
        meta.appendChild(el("strong", text = comment.authorName))
        meta.appendChild(el("span", "comment-time", text = relativeTime(comment.createdAt)))
        body.appendChild(meta)
        if (!comment.replyToText.isNullOrBlank()) {
            val quote = el("div", "comment-quote")
            appendLinkedText(quote, "@${comment.replyToAuthorName}: ${comment.replyToText}")
            body.appendChild(quote)
        }
        body.appendChild(linkedParagraph(comment.text, "comment-text"))
        item.appendChild(body)
        item.appendChild(el("button", "comment-reply", text = Town.t("reply")) { button ->
            button.setAttribute("type", "button")
            button.onClick {
                if (Town.me == null) {
                    showAuthDialog(host, onSuccess = { field.asDynamic().focus() })
                    return@onClick
                }
                replyTo = comment
                paintReplyTarget()
                field.asDynamic().focus()
            }
        })
        if (canDelete(comment)) {
            item.asDynamic().addEventListener("click", { event: dynamic ->
                val tag = (event.target.tagName as? String)?.lowercase()
                if (tag == "button" || tag == "img" || tag == "a") return@addEventListener
                askDelete(comment)
            })
        }
        return item
    }

    fun paintList(reset: Boolean) {
        if (reset) list.clear()
        comments.forEach { comment ->
            if (list.asDynamic().querySelector("[data-comment='${comment.id}']") == null) {
                val node = renderItem(comment)
                node.setAttribute("data-comment", comment.id)
                list.appendChild(node)
            }
        }
        paintEmpty()
    }

    fun remember(comment: CommentCard) {
        if (comments.none { it.id == comment.id }) comments.add(0, comment)
        current = current.copy(
            commentCount = current.commentCount + 1,
            previewComments = comments.take(Limits.COMMENT_PREVIEW),
        )
        onUpdate(current)
    }

    fun sendText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (Town.me == null) {
            showAuthDialog(host, onSuccess = { sendText(trimmed) })
            return
        }
        send.setAttribute("disabled", "disabled")
        val payload = js("({})")
        payload.text = trimmed
        val quotedId = replyTo?.id
        if (!quotedId.isNullOrBlank()) payload.replyToId = quotedId
        http("POST", "/api/posts/${current.id}/comments", jsonStringify(payload)) { status, body ->
            send.removeAttribute("disabled")
            if (status == 201) {
                val comment = commentFrom(parseJson(body))
                remember(comment)
                val node = renderItem(comment)
                node.setAttribute("data-comment", comment.id)
                if (list.firstChild != null) list.insertBefore(node, list.firstChild)
                else list.appendChild(node)
                paintEmpty()
                field.asDynamic().value = ""
                draft = ""
                replyTo = null
                paintReplyTarget()
                startPlaceholder()
            } else {
                toast(host, errorText(body, status))
            }
        }
    }

    fun loadPage() {
        if (loading || done) return
        loading = true
        val extra = if (cursor.isNullOrBlank()) "" else "&cursor=${encodeQuery(cursor!!)}"
        http("GET", "/api/posts/${current.id}/comments?limit=${Limits.COMMENT_PAGE_SIZE}$extra") { status, body ->
            loading = false
            if (status != 200) return@http
            val parsed = parseJson(body)
            val page = readComments(parsed.comments)
            if (cursor == null) {
                comments.clear()
                comments.addAll(page)
                current = current.copy(
                    commentCount = num(parsed.total).toInt(),
                    previewComments = comments.take(Limits.COMMENT_PREVIEW),
                )
                onUpdate(current)
                paintList(true)
            } else {
                page.forEach { comment ->
                    if (comments.none { it.id == comment.id }) {
                        comments += comment
                        val node = renderItem(comment)
                        node.setAttribute("data-comment", comment.id)
                        list.appendChild(node)
                    }
                }
            }
            cursor = parsed.nextCursor as? String
            if (cursor.isNullOrBlank()) done = true
        }
    }

    repliesFor(current).forEach { reply ->
        chips.appendChild(el("button", "quick-chip", text = reply) { chip ->
            chip.setAttribute("type", "button")
            chip.onClick {
                field.asDynamic().value = reply
                draft = reply
                field.asDynamic().focus()
            }
        })
    }
    send.onClick {
        sendText((field.asDynamic().value as? String).orEmpty())
    }
    field.asDynamic().addEventListener("keydown", { event: dynamic ->
        if (event.key == "Enter") {
            event.preventDefault()
            sendText((field.asDynamic().value as? String).orEmpty())
        }
    })
    field.asDynamic().addEventListener("focus", {
        if (Town.me == null) {
            field.asDynamic().blur()
            showAuthDialog(host, onSuccess = { field.asDynamic().focus() })
        } else {
            stopPlaceholder()
        }
    })
    field.asDynamic().addEventListener("blur", { startPlaceholder() })
    list.asDynamic().addEventListener("scroll", {
        val top = num(list.asDynamic().scrollTop)
        val height = num(list.asDynamic().clientHeight)
        val full = num(list.asDynamic().scrollHeight)
        if (top + height > full - 80) loadPage()
    })

    paintList(true)
    paintReplyTarget()
    loadPage()
    startPlaceholder()
    chrome.open(host) {
        if (focusInput) window.asDynamic().setTimeout({ field.asDynamic().focus() }, 80)
    }
}

internal fun messagePostButton(post: PostCard, host: HTMLElement, router: HistoryRouter): HTMLElement =
    el("button", "zalo-btn") { button ->
        button.setAttribute("type", "button")
        button.setAttribute("aria-label", Town.t("message"))
        button.asDynamic().innerHTML = """
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <path fill="currentColor" d="M4.2 4.2h15.6A1.8 1.8 0 0 1 21.6 6v9.2a1.8 1.8 0 0 1-1.8 1.8H8.4L4 21.2V6a1.8 1.8 0 0 1 1.8-1.8H4.2z"/>
            </svg>
        """.trimIndent()
        button.onClick { openDirectChat(host, router, post.authorId, post.authorName) }
    }

internal fun relativeTime(createdAt: Long): String {
    val delta = (num(js("Date.now()")) - createdAt.toDouble()).coerceAtLeast(0.0)
    val minutes = (delta / 60000.0).toInt()
    return when {
        minutes < 1 -> Town.t("justNow")
        minutes < 60 -> Town.t("minutesAgo", mapOf("count" to minutes.toString()))
        minutes < 60 * 24 -> Town.t("hoursAgo", mapOf("count" to (minutes / 60).toString()))
        else -> Town.t("daysAgo", mapOf("count" to (minutes / (60 * 24)).toString()))
    }
}
