package quest.saigon.web

import quest.saigon.Limits
import quest.saigon.feedMotion
import web.dom.document
import web.html.HTMLElement
import web.window.window
import kotlin.math.abs

private var feedKeyHandler: ((String) -> Unit)? = null
private var keysInstalled = false

internal fun clearFeedKeys() {
    feedKeyHandler = null
}

internal fun feedScreen(router: HistoryRouter): HTMLElement {
    val shell = el("section", "shell feed-shell")
    shell.asDynamic().style.setProperty("--avatar-enter", "${Limits.AVATAR_ENTER_PX}px")
    val stage = el("div", "stage")
    val location = el("div", "location")
    val top = el("div", "topbar")
    top.appendChild(location)
    top.appendChild(viewerButton(router, toComposer = true))
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
        if (posts.isEmpty()) {
            stage.appendChild(emptyState(Town.t("emptyFeed"), Town.t("emptyFeedHint")))
            return@http
        }
        mountPager(stage, location, shell, posts, parsed.nextCursor as? String)
    }
    return shell
}

private fun mountPager(
    stage: HTMLElement,
    location: HTMLElement,
    host: HTMLElement,
    posts: MutableList<PostCard>,
    initialCursor: String?,
) {
    var index = 0
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
        val motion = feedMotion(offset)
        val height = stageHeight()
        location.textContent = posts.getOrNull(index)?.location.orEmpty()
        for (i in (index - 1)..(index + 1)) {
            val slide = slides[i] ?: continue
            val base = (i - index) * height
            slide.asDynamic().style.transform = "translate3d(0, ${base + motion.photoOffset}px, 0)"
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
        val slide = buildSlide(posts[i], host) { playCurrent() }
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
        when (key) {
            "ArrowDown", "PageDown" -> commit(1)
            "ArrowUp", "PageUp" -> commit(-1)
        }
    }
    rebuild()
}

private fun buildSlide(post: PostCard, host: HTMLElement, replay: () -> Unit): HTMLElement {
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
    val card = el("div", "card")
    card.appendChild(el("span", "name-chip", text = post.authorName))
    val message = el("p", "message")
    card.appendChild(message)
    card.appendChild(el("button", "reply", text = Town.t("replyTo", mapOf("name" to post.authorName))) { button ->
        button.setAttribute("type", "button")
        button.onClick { openZalo(post.id, host, button) }
    })
    stack.appendChild(card)
    slide.appendChild(stack)
    return slide
}

private fun openZalo(postId: String, host: HTMLElement, button: HTMLElement) {
    val popup = window.asDynamic().open("about:blank", "_blank")
    button.setAttribute("disabled", "disabled")
    http("POST", "/api/posts/$postId/contact", "{}") { status, body ->
        button.removeAttribute("disabled")
        if (status == 200) {
            val url = parseJson(body).zaloUrl as String
            if (popup != null && popup.closed != true) popup.location.href = url
            else window.asDynamic().location.assign(url)
        } else {
            if (popup != null) popup.close()
            toast(host, errorText(body, status))
        }
    }
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

private fun ensureKeys() {
    if (keysInstalled) return
    keysInstalled = true
    window.asDynamic().addEventListener("keydown", { event: dynamic ->
        val key = event.key as? String ?: return@addEventListener
        if (feedKeyHandler != null && (key == "ArrowDown" || key == "ArrowUp" || key == "PageDown" || key == "PageUp")) {
            event.preventDefault()
        }
        feedKeyHandler?.invoke(key)
    })
}
