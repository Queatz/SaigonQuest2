package quest.saigon.web

import quest.saigon.Limits
import quest.saigon.feedSlideMotion
import web.dom.document
import web.html.HTMLElement
import web.window.window
import kotlin.math.abs

private var feedKeyHandler: ((String) -> Unit)? = null
private var feedResizeHandler: (() -> Unit)? = null
private var keysInstalled = false
private var resizeInstalled = false
private var swipeHintTimer: dynamic = null
private var swipeHintDone = false

internal fun clearFeedKeys() {
    feedKeyHandler = null
    feedResizeHandler = null
    stopSwipeHint()
    stopFeedAudio()
    clearChatTimers()
}

internal fun feedScreen(router: HistoryRouter, focusPostId: String? = null): HTMLElement {
    val shell = el("section", "shell feed-shell")
    shell.asDynamic().style.setProperty("--avatar-enter", "${Limits.AVATAR_ENTER_PX}px")
    val stage = el("div", "stage")
    val location = el("button", "location")
    location.setAttribute("type", "button")
    val brand = el("div", "feed-brand")
    brand.appendChild(location)
    brand.appendChild(el("div", "feed-app-name", text = Town.t("appName")))
    val top = el("div", "topbar")
    top.appendChild(brand)
    val actions = el("div", "top-actions")
    if (Town.me != null) actions.appendChild(notificationButton(router, shell))
    actions.appendChild(viewerButton(router, toComposer = true))
    top.appendChild(actions)
    shell.appendChild(stage)
    shell.appendChild(top)
    http("GET", "/api/posts?limit=${Limits.FEED_PAGE_SIZE}") { status, body ->
        if (status != 200) {
            stage.appendChild(emptyState(Town.t("unavailable"), Town.t("emptyFeedHint")))
            return@http
        }
        val parsed = parseJson(body)
        val posts = readPosts(parsed.posts)
        val pending = Town.pending
        if (pending != null && posts.none { it.id == pending.id }) posts.add(0, pending)
        Town.pending = null
        fun start(cursor: String?) {
            if (posts.isEmpty()) {
                stage.appendChild(emptyState(Town.t("emptyFeed"), Town.t("emptyFeedHint")))
                return
            }
            mountPager(stage, location, shell, router, posts, cursor, focusPostId)
        }
        if (!focusPostId.isNullOrBlank() && posts.none { it.id == focusPostId }) {
            http("GET", "/api/posts/${encodeQuery(focusPostId)}") { postStatus, postBody ->
                if (postStatus == 200) {
                    val focused = postFrom(parseJson(postBody))
                    if (posts.none { it.id == focused.id }) posts.add(0, focused)
                }
                start(parsed.nextCursor as? String)
            }
        } else {
            start(parsed.nextCursor as? String)
        }
    }
    return shell
}

private fun mountPager(
    stage: HTMLElement,
    location: HTMLElement,
    host: HTMLElement,
    router: HistoryRouter,
    posts: MutableList<PostCard>,
    initialCursor: String?,
    focusPostId: String? = null,
) {
    var index = focusPostId?.let { id -> posts.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
    var openedFocus = false
    var cursor = initialCursor
    var loading = false
    var drag = 0.0
    var dragging = false
    var animating = false
    var wheelLock = false
    var startY = 0.0
    var lastY = 0.0
    var lastTime = 0.0
    var velocity = 0.0
    var cancelType: () -> Unit = {}
    var viewedId: String? = null
    val slides = mutableMapOf<Int, HTMLElement>()

    fun stageHeight(): Double {
        val height = num(stage.asDynamic().clientHeight)
        return if (height > 40.0) height else 640.0
    }

    fun layout(offset: Double, showAvatar: Boolean) {
        val height = stageHeight()
        location.textContent = "📍 " + posts.getOrNull(index)?.location.orEmpty()
        for (i in (index - 1)..(index + 1)) {
            val slide = slides[i] ?: continue
            val motion = feedSlideMotion(offset, i - index, height)
            slide.asDynamic().style.transform = "translate3d(0, ${motion.photoOffset}px, 0)"
            val card = slide.asDynamic().querySelector(".card")
            if (card != null) {
                card.style.transform = "translate3d(0, ${motion.cardOffset - motion.photoOffset}px, 0)"
            }
            val avatar = slide.asDynamic().querySelector(".avatar")
            if (avatar != null) avatar.className = if (showAvatar && i == index) "avatar shown" else "avatar"
        }
    }

    fun playCurrent() {
        cancelType()
        val post = posts.getOrNull(index) ?: return
        syncFeedAudio(post)
        val message = slides[index]?.asDynamic()?.querySelector(".message") ?: return
        if (viewedId != post.id) {
            viewedId = post.id
            http("POST", "/api/posts/${post.id}/view", "{}") { _, _ -> }
        }
        cancelType = typewrite(message, post.message)
    }

    fun revealAvatar() {
        layout(0.0, false)
        window.asDynamic().requestAnimationFrame {
            window.asDynamic().requestAnimationFrame {
                layout(0.0, true)
                playCurrent()
            }
        }
    }

    fun ensureSlide(i: Int) {
        if (i !in posts.indices || slides.containsKey(i)) return
        val slide = buildSlide(posts[i], host, router, { next -> posts[i] = next }) { playCurrent() }
        stage.appendChild(slide)
        slides[i] = slide
    }

    fun rebuild() {
        val stale = slides.keys.filter { it < index - 1 || it > index + 1 }
        stale.forEach { key ->
            slides.remove(key)?.asDynamic()?.remove()
        }
        ensureSlide(index - 1)
        ensureSlide(index)
        ensureSlide(index + 1)
        revealAvatar()
        if (!openedFocus && !focusPostId.isNullOrBlank() && posts.getOrNull(index)?.id == focusPostId) {
            openedFocus = true
            window.asDynamic().setTimeout({
                openCommentsSheet(posts[index], host, router, focusInput = false) { next ->
                    posts[index] = next
                }
            }, 280)
        }
    }

    fun loadMore(then: () -> Unit = {}) {
        val next = cursor
        if (next.isNullOrBlank() || loading) {
            then()
            return
        }
        loading = true
        http("GET", "/api/posts?limit=${Limits.FEED_PAGE_SIZE}&cursor=${encodeQuery(next)}") { status, body ->
            loading = false
            if (status == 200) {
                val parsed = parseJson(body)
                posts.addAll(readPosts(parsed.posts))
                cursor = parsed.nextCursor as? String
            }
            then()
        }
    }

    fun finish(delta: Int) {
        index = (index + delta).coerceIn(0, posts.lastIndex)
        drag = 0.0
        animating = false
        if (index >= 1) dismissSwipeHint(host)
        if (index >= posts.lastIndex - 1) loadMore()
        rebuild()
    }

    fun animate(target: Double, delta: Int) {
        animating = true
        val origin = drag
        val started = now()
        fun frame() {
            val t = ((now() - started) / 280.0).coerceIn(0.0, 1.0)
            val eased = 1 - (1 - t) * (1 - t)
            drag = origin + (target - origin) * eased
            layout(drag, false)
            if (t < 1.0) {
                window.asDynamic().requestAnimationFrame { frame() }
            } else if (delta == 0) {
                animating = false
                drag = 0.0
                revealAvatar()
            } else {
                finish(delta)
            }
        }
        frame()
    }

    fun commit(direction: Int) {
        if (animating) return
        val height = stageHeight()
        if (direction < 0 && index == 0) {
            animate(0.0, 0)
            return
        }
        if (direction > 0 && index >= posts.lastIndex) {
            if (!cursor.isNullOrBlank()) {
                loadMore {
                    if (index < posts.lastIndex) animate(-height, 1) else animate(0.0, 0)
                }
            } else {
                animate(0.0, 0)
            }
            return
        }
        animate(if (direction > 0) -height else height, direction)
    }

    fun resisted(raw: Double): Double = when {
        raw > 0 && index == 0 -> raw * 0.35
        raw < 0 && index >= posts.lastIndex && cursor.isNullOrBlank() -> raw * 0.35
        else -> raw
    }

    stage.asDynamic().addEventListener("pointerdown", { event: dynamic ->
        if (animating || interactive(event.target)) return@addEventListener
        dragging = true
        startY = num(event.clientY)
        lastY = startY
        lastTime = num(event.timeStamp)
        velocity = 0.0
        if (stage.asDynamic().setPointerCapture != undefined) stage.asDynamic().setPointerCapture(event.pointerId)
    })
    stage.asDynamic().addEventListener("pointermove", { event: dynamic ->
        if (!dragging) return@addEventListener
        val y = num(event.clientY)
        val time = num(event.timeStamp)
        velocity = (y - lastY) / (time - lastTime).coerceAtLeast(1.0)
        lastY = y
        lastTime = time
        drag = resisted(y - startY)
        layout(drag, false)
        event.preventDefault()
    })
    val endPointer = { _: dynamic ->
        if (dragging) {
            dragging = false
            val height = stageHeight()
            val flung = abs(velocity) > 0.45
            val far = abs(drag) > height * 0.18
            if (flung || far) commit(if (velocity < -0.45 || (!flung && drag < 0)) 1 else -1) else animate(0.0, 0)
        }
    }
    stage.asDynamic().addEventListener("pointerup", endPointer)
    stage.asDynamic().addEventListener("pointercancel", endPointer)
    stage.asDynamic().addEventListener("wheel", { event: dynamic ->
        event.preventDefault()
        if (animating || dragging || wheelLock) return@addEventListener
        val delta = num(event.deltaY)
        if (abs(delta) < 24) return@addEventListener
        wheelLock = true
        commit(if (delta > 0) 1 else -1)
        window.asDynamic().setTimeout({ wheelLock = false }, 520)
    }, js("({passive:false})"))

    ensureKeys()
    feedKeyHandler = { key ->
        if (!feedDialogOpen()) {
            when (key) {
                "ArrowDown", "PageDown" -> commit(1)
                "ArrowUp", "PageUp" -> commit(-1)
            }
        }
    }
    location.onClick {
        val place = posts.getOrNull(index)?.location.orEmpty().trim()
        if (place.isBlank()) return@onClick
        val query = encodeQuery("$place ở gần Sài Gòn")
        window.asDynamic().open("https://www.google.com/maps/search/?api=1&query=$query", "_blank")
    }
    ensureResize()
    feedResizeHandler = {
        if (!animating && !dragging) layout(0.0, true)
        else layout(drag, false)
    }
    rebuild()
    startSwipeHint(host) { index < 1 }
}

private fun buildSlide(
    post: PostCard,
    host: HTMLElement,
    router: HistoryRouter,
    onUpdate: (PostCard) -> Unit,
    @Suppress("UNUSED_PARAMETER") replay: () -> Unit,
): HTMLElement {
    val slide = el("article", "slide")
    val photo = el("div", "photo")
    photo.asDynamic().style.backgroundImage = "url(\"${post.photoUrl}\")"
    slide.appendChild(photo)
    val stack = el("div", "card-stack")
    if (!post.avatarUrl.isNullOrBlank()) {
        val avatar = el("img", "avatar")
        avatar.setAttribute("alt", "")
        cropAvatarToContent(avatar)
        avatar.setAttribute("src", post.avatarUrl)
        stack.appendChild(avatar)
    }
    if (!post.musicName.isNullOrBlank()) stack.appendChild(musicLine(post))
    val card = el("div", "card")
    val name = el("button", if (post.authorPhotoUrl.isNullOrBlank()) "name-chip" else "name-chip has-face")
    name.setAttribute("type", "button")
    if (!post.authorPhotoUrl.isNullOrBlank()) {
        val face = el("img", "name-face")
        face.setAttribute("alt", "")
        face.setAttribute("src", post.authorPhotoUrl)
        name.appendChild(face)
    }
    name.appendChild(el("span", text = post.authorName))
    name.onClick { openMiniProfile(host, post.authorName, post.authorPhotoUrl, post.authorLocation, post.authorIntro) }
    card.appendChild(name)
    val message = el("p", "message")
    card.appendChild(message)
    card.appendChild(collapsedChat(post, host, router, onUpdate))
    stack.appendChild(card)
    slide.appendChild(stack)
    return slide
}

internal fun showVisitorWelcome(host: HTMLElement) {
    if (host.asDynamic().querySelector(".dialog-backdrop") != null) return
    val backdrop = el("div", "dialog-backdrop")
    val card = el("div", "dialog visitor-welcome")
    card.setAttribute("role", "dialog")
    card.setAttribute("aria-modal", "true")
    card.setAttribute("aria-labelledby", "visitor-welcome-text")
    val emojis = el("div", "visitor-welcome-emojis")
    emojis.setAttribute("aria-hidden", "true")
    listOf("📍", "😍", "🍵").forEachIndexed { index, emoji ->
        emojis.appendChild(el("span", "bob bob-${index + 1}", text = emoji))
    }
    card.appendChild(emojis)
    val message = el("p", "visitor-welcome-text", text = Town.t("visitorWelcome"))
    message.setAttribute("id", "visitor-welcome-text")
    card.appendChild(message)
    card.appendChild(el("button", "text-button", text = Town.t("visitorWelcomeGo")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
}

internal fun openMiniProfile(
    host: HTMLElement,
    name: String,
    photoUrl: String?,
    location: String = "",
    intro: String = "",
) {
    if (host.asDynamic().querySelector(".dialog.mini-profile") != null) return
    val backdrop = el("div", "dialog-backdrop stacked")
    val card = el("div", "dialog mini-profile")
    if (!photoUrl.isNullOrBlank()) {
        val face = el("img", "face large")
        face.setAttribute("alt", "")
        face.setAttribute("src", photoUrl)
        card.appendChild(face)
    } else {
        card.appendChild(el("span", "face large initial", text = name.take(1).uppercase()))
    }
    card.appendChild(el("h2", text = name))
    if (location.isNotBlank()) {
        card.appendChild(el("p", "mini-profile-location", text = location))
    }
    if (intro.isNotBlank()) {
        card.appendChild(el("p", "mini-profile-intro", text = intro))
    }
    card.appendChild(el("button", "text-button", text = Town.t("close")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
}

private fun typewrite(node: dynamic, text: String): () -> Unit {
    var index = 0
    var timer: dynamic = null
    var stopped = false
    fun step() {
        if (stopped) return
        index = minOf(text.length, index + 1)
        node.textContent = text.substring(0, index)
        if (index < text.length) {
            timer = window.asDynamic().setTimeout({ step() }, Limits.TYPEWRITER_MILLIS_PER_CHAR)
        }
    }
    node.textContent = ""
    step()
    return {
        stopped = true
        if (timer != null) window.asDynamic().clearTimeout(timer)
    }
}

private fun cropAvatarToContent(image: HTMLElement) {
    image.asDynamic().onload = load@{
        try {
            val img = image.asDynamic()
            val width = num(img.naturalWidth).toInt()
            val height = num(img.naturalHeight).toInt()
            if (width < 2 || height < 2) return@load
            val canvas = document.createElement("canvas").asDynamic()
            canvas.width = width
            canvas.height = height
            val context = canvas.getContext("2d")
            context.drawImage(img, 0, 0)
            val pixels = context.getImageData(0, 0, width, height).data
            var minX = width
            var minY = height
            var maxX = -1
            var maxY = -1
            var pixel = 0
            val count = width * height
            while (pixel < count) {
                if (num(pixels[pixel * 4 + 3]) > 12.0) {
                    val x = pixel % width
                    val y = pixel / width
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
                pixel += 1
            }
            if (maxX < minX) return@load
            minX = (minX - 1).coerceAtLeast(0)
            minY = (minY - 1).coerceAtLeast(0)
            maxX = (maxX + 1).coerceAtMost(width - 1)
            maxY = (maxY + 1).coerceAtMost(height - 1)
            if (minX == 0 && minY == 0 && maxX == width - 1 && maxY == height - 1) return@load
            val cropW = maxX - minX + 1
            val cropH = maxY - minY + 1
            val cropped = document.createElement("canvas").asDynamic()
            cropped.width = cropW
            cropped.height = cropH
            cropped.getContext("2d").drawImage(img, minX, minY, cropW, cropH, 0, 0, cropW, cropH)
            img.onload = null
            img.src = cropped.toDataURL("image/png") as String
        } catch (_: Throwable) {
        }
    }
}

private fun readPosts(array: dynamic): MutableList<PostCard> {
    val posts = mutableListOf<PostCard>()
    if (array == null || array == undefined) return posts
    val length = array.length as Int
    for (i in 0 until length) posts += postFrom(array[i])
    return posts
}

private fun emptyState(title: String, hint: String): HTMLElement {
    val box = el("div", "empty")
    box.appendChild(el("h2", text = title))
    box.appendChild(el("p", text = hint))
    return box
}

internal fun toast(host: HTMLElement, message: String) {
    val node = el("div", "toast", text = message)
    host.appendChild(node)
    window.asDynamic().setTimeout({ node.asDynamic().remove() }, 2800)
}

private fun interactive(target: dynamic): Boolean {
    var node = target
    while (node != null && node != undefined) {
        val tag = (node.tagName as? String)?.lowercase()
        if (tag == "button" || tag == "a" || tag == "input" || tag == "textarea" || tag == "select" || tag == "label") {
            return true
        }
        node = node.parentElement
    }
    return false
}

private fun now(): Double = num(window.asDynamic().performance.now())

private fun feedDialogOpen(): Boolean {
    val dialog = document.asDynamic().querySelector(".dialog-backdrop")
    return dialog != null && dialog != undefined
}

private fun startSwipeHint(host: HTMLElement, onFirstItem: () -> Boolean) {
    if (!Town.signedOut || swipeHintDone) return
    stopSwipeHint()
    swipeHintTimer = window.asDynamic().setInterval({
        if (swipeHintDone || !onFirstItem() || feedDialogOpen()) return@setInterval
        flashSwipeHint(host)
    }, 3000)
}

private fun dismissSwipeHint(host: HTMLElement) {
    swipeHintDone = true
    stopSwipeHint()
    host.asDynamic().querySelector(".swipe-hint")?.remove()
}

private fun stopSwipeHint() {
    if (swipeHintTimer != null && swipeHintTimer != undefined) {
        window.asDynamic().clearInterval(swipeHintTimer)
    }
    swipeHintTimer = null
}

private fun flashSwipeHint(host: HTMLElement) {
    host.asDynamic().querySelector(".swipe-hint")?.remove()
    val hint = el("div", "swipe-hint")
    hint.setAttribute("aria-hidden", "true")
    hint.appendChild(el("div", "swipe-hint-arrow", text = "↓"))
    hint.appendChild(el("div", "swipe-hint-text", text = Town.t("swipeDown")))
    host.appendChild(hint)
    window.asDynamic().setTimeout({ hint.asDynamic().remove() }, 1200)
}

private fun ensureKeys() {
    if (keysInstalled) return
    keysInstalled = true
    window.asDynamic().addEventListener("keydown", { event: dynamic ->
        val key = event.key as? String ?: return@addEventListener
        val paging = key == "ArrowDown" || key == "ArrowUp" || key == "PageDown" || key == "PageUp"
        if (feedKeyHandler != null && paging && !feedDialogOpen()) {
            event.preventDefault()
        }
        feedKeyHandler?.invoke(key)
    })
}

private fun ensureResize() {
    if (resizeInstalled) return
    resizeInstalled = true
    window.asDynamic().addEventListener("resize", {
        feedResizeHandler?.invoke()
    })
}
