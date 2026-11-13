package quest.saigon.web

import quest.saigon.Limits
import quest.saigon.PostAge
import quest.saigon.feedSlideMotion
import quest.saigon.postMessageNeedsMore
import quest.saigon.previewPostMessage
import web.dom.document
import web.html.HTMLElement
import web.window.window
import kotlin.math.abs

private var feedKeyHandler: ((String) -> Unit)? = null
private var feedResizeHandler: (() -> Unit)? = null
private var feedResumeHandler: (() -> Unit)? = null
private var keysInstalled = false
private var resizeInstalled = false
private var swipeHintTimer: dynamic = null
private var swipeHintDone = false
private var photoPeekTimer: dynamic = null
private var feedCheckInBubbles: CheckInBubbles? = null

internal fun clearFeedKeys() {
    feedKeyHandler = null
    feedResizeHandler = null
    feedResumeHandler = null
    stopSwipeHint()
    stopPhotoPeekTimer()
    feedCheckInBubbles?.close()
    feedCheckInBubbles = null
    stopFeedAudio()
    clearChatTimers()
}

internal fun resumeFeedOnVisible() {
    feedResumeHandler?.invoke()
}

private fun feedPageUrl(feedPath: String, cursor: String? = null): String {
    val separator = if ('?' in feedPath) "&" else "?"
    val cursorQuery = if (cursor.isNullOrBlank()) "" else "&cursor=${encodeQuery(cursor)}"
    return "$feedPath${separator}limit=${Limits.FEED_PAGE_SIZE}$cursorQuery"
}

internal fun feedScreen(
    router: HistoryRouter,
    focusPostId: String? = null,
    communityId: String? = null,
    onBack: (() -> Unit)? = null,
    seedPosts: List<PostCard>? = null,
    seedCursor: String? = null,
    searchQuery: String? = null,
): HTMLElement {
    val shell = el(
        "section",
        if (onBack != null) "shell feed-shell search-feed-overlay" else "shell feed-shell",
    )
    shell.asDynamic().style.setProperty("--avatar-enter", "${Limits.AVATAR_ENTER_PX}px")
    val stage = el("div", "stage")
    val location = el("button", "location")
    location.setAttribute("type", "button")
    val top = el(
        "div",
        when {
            communityId != null -> "topbar community-top"
            onBack != null -> "topbar search-feed-top"
            else -> "topbar"
        },
    )
    val communityTitle = el("div", "community-title")
    val members = el("button", "pill-btn", text = Town.t("communityMembers"))
    val join = el("button", "pill-btn", text = Town.t("joinCommunity"))
    members.setAttribute("type", "button")
    join.setAttribute("type", "button")
    if (communityId == null) {
        if (onBack != null) {
            val back = el("button", "icon-btn", text = "‹")
            back.setAttribute("type", "button")
            back.setAttribute("aria-label", Town.t("back"))
            back.onClick { onBack() }
            top.appendChild(back)
        }
        val brand = el("div", "feed-brand")
        brand.appendChild(location)
        brand.appendChild(el("div", "feed-app-name", text = Town.t("appName")))
        if (onBack == null) brand.appendChild(eventsSummaryButton(router))
        top.appendChild(brand)
        if (onBack == null) {
            val actions = el("div", "top-actions")
            if (Town.me != null) {
                actions.appendChild(messagesButton(router))
                actions.appendChild(notificationButton(router, shell))
            }
            actions.appendChild(viewerButton(router, toComposer = true))
            top.appendChild(actions)
        }
    } else {
        val back = el("button", "icon-btn", text = "‹")
        back.setAttribute("type", "button")
        back.setAttribute("aria-label", Town.t("back"))
        back.onClick { router.navigate("/") }
        val brand = el("div", "feed-brand community-brand")
        brand.appendChild(communityTitle)
        brand.appendChild(location)
        val actions = el("div", "community-actions")
        actions.appendChild(members)
        actions.appendChild(join)
        top.appendChild(back)
        top.appendChild(brand)
        top.appendChild(actions)
        top.appendChild(viewerButton(router, toComposer = true))
    }
    shell.appendChild(stage)
    shell.appendChild(top)
    if (focusPostId == null && onBack == null && communityId == null && searchQuery.isNullOrBlank()) {
        feedCheckInBubbles?.close()
        feedCheckInBubbles = CheckInBubbles(shell, router)
    }
    val feedPath = when {
        searchQuery != null -> "/api/posts/search?q=${encodeQuery(searchQuery)}"
        communityId.isNullOrBlank() -> "/api/posts"
        else -> "/api/communities/${encodeQuery(communityId)}/posts"
    }
    val emptyTitle = if (communityId == null) Town.t("emptyFeed") else Town.t("emptyCommunity")
    val emptyHint = if (communityId == null) Town.t("emptyFeedHint") else Town.t("emptyCommunityHint")
    var feedLoading = false
    var pagerLive = false
    fun start(posts: MutableList<PostCard>, cursor: String?) {
        if (pagerLive) return
        stage.clear()
        if (posts.isEmpty()) {
            stage.appendChild(emptyState(emptyTitle, emptyHint))
            return
        }
        pagerLive = true
        mountPager(
            stage,
            location,
            shell,
            router,
            posts,
            cursor,
            focusPostId,
            feedPath,
            autoOpenFocusComments = onBack == null,
        )
    }
    fun loadFeed() {
        if (feedLoading || pagerLive) return
        feedLoading = true
        http("GET", feedPageUrl(feedPath)) { status, body ->
            feedLoading = false
            if (shell.asDynamic().isConnected == false) return@http
            if (pagerLive) return@http
            if (status != 200) {
                stage.clear()
                stage.appendChild(emptyState(Town.t("unavailable"), emptyHint))
                return@http
            }
            val parsed = parseJson(body)
            val posts = readPosts(parsed.posts)
            val pending = if (communityId == null) Town.pending else null
            Town.pending = null
            if (pending != null && posts.none { it.id == pending.id }) posts.add(0, pending)
            if (!focusPostId.isNullOrBlank() && posts.none { it.id == focusPostId }) {
                http("GET", "/api/posts/${encodeQuery(focusPostId)}") { postStatus, postBody ->
                    if (shell.asDynamic().isConnected == false) return@http
                    if (postStatus == 200) {
                        val focused = postFrom(parseJson(postBody))
                        if (posts.none { it.id == focused.id }) posts.add(0, focused)
                    }
                    start(posts, parsed.nextCursor as? String)
                }
            } else {
                start(posts, parsed.nextCursor as? String)
            }
        }
    }
    fun watchFeed() {
        feedResumeHandler = { loadFeed() }
    }
    if (communityId.isNullOrBlank()) {
        if (seedPosts != null) {
            window.asDynamic().setTimeout({
                if (shell.asDynamic().isConnected == false) return@setTimeout
                start(seedPosts.toMutableList(), seedCursor)
            }, 0)
        } else {
            watchFeed()
            loadFeed()
        }
    } else {
        bindCommunityBar(shell, router, communityId, communityTitle, members, join, onReady = {
            watchFeed()
            loadFeed()
        }) {
            stage.appendChild(emptyState(Town.t("notFound"), Town.t("goHome")))
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
    feedPath: String = "/api/posts",
    autoOpenFocusComments: Boolean = true,
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
    val photoPagers = mutableMapOf<Int, PhotoPager>()
    var startX = 0.0
    var lastX = 0.0
    var velocityX = 0.0
    var axis: String? = null
    var photoDrag = 0.0
    var peeking = false
    var photoPress = false

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
        val copy = message.querySelector(".message-copy") ?: message
        paintPostTime(message.querySelector(".message-time"), post.createdAt)
        if (viewedId != post.id) {
            viewedId = post.id
            http("POST", "/api/posts/${post.id}/view", "{}") { _, _ -> }
        }
        message.classList.remove("ready")
        cancelType = typewrite(copy, previewPostMessage(post.message)) {
            if (postMessageNeedsMore(post.message)) message.classList.add("ready")
        }
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

    fun endPeek() {
        stopPhotoPeekTimer()
        if (!peeking) return
        peeking = false
        slides[index]?.asDynamic()?.classList?.remove("peeking")
        feedCheckInBubbles?.hideUntilIdle()
    }

    fun beginPeek() {
        feedCheckInBubbles?.hideUntilIdle()
        peeking = true
        dragging = false
        drag = 0.0
        layout(0.0, true)
        slides[index]?.asDynamic()?.classList?.add("peeking")
    }

    fun armPeek() {
        stopPhotoPeekTimer()
        photoPeekTimer = window.asDynamic().setTimeout({
            photoPeekTimer = null
            beginPeek()
        }, Limits.PHOTO_PEEK_MILLIS)
    }

    fun tapPeek() {
        stopPhotoPeekTimer()
        beginPeek()
        photoPeekTimer = window.asDynamic().setTimeout({
            photoPeekTimer = null
            endPeek()
        }, Limits.PHOTO_PEEK_TAP_MILLIS)
    }

    fun ensureSlide(i: Int) {
        if (i !in posts.indices || slides.containsKey(i)) return
        val built = buildSlide(posts[i], host, router, { next -> posts[i] = next }) { playCurrent() }
        stage.appendChild(built.root)
        slides[i] = built.root
        built.pager?.let { photoPagers[i] = it }
    }

    fun rebuild() {
        val stale = slides.keys.filter { it < index - 1 || it > index + 1 }
        stale.forEach { key ->
            slides.remove(key)?.asDynamic()?.remove()
            photoPagers.remove(key)
        }
        ensureSlide(index - 1)
        ensureSlide(index)
        ensureSlide(index + 1)
        revealAvatar()
        if (autoOpenFocusComments && !openedFocus && !focusPostId.isNullOrBlank() && posts.getOrNull(index)?.id == focusPostId) {
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
        http("GET", feedPageUrl(feedPath, next)) { status, body ->
            loading = false
            if (status == 200) {
                val parsed = parseJson(body)
                posts.addAll(readPosts(parsed.posts))
                cursor = parsed.nextCursor as? String
            }
            then()
        }
    }

    fun applyNewer(latest: List<PostCard>): Boolean {
        val known = posts.map { it.id }.toHashSet()
        val newcomers = latest.filter { it.id !in known }
        if (newcomers.isEmpty()) return false
        posts.addAll(0, newcomers)
        slides.values.forEach { it.asDynamic().remove() }
        slides.clear()
        photoPagers.clear()
        index = 0
        return true
    }

    fun finish(delta: Int) {
        endPeek()
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

    fun pullLatest(onUnchanged: (() -> Unit)? = null) {
        if (loading) {
            onUnchanged?.invoke()
            return
        }
        loading = true
        http("GET", feedPageUrl(feedPath)) { status, body ->
            loading = false
            val changed = status == 200 && applyNewer(readPosts(parseJson(body).posts))
            if (changed) {
                drag = 0.0
                animating = false
                dragging = false
                rebuild()
            } else {
                onUnchanged?.invoke()
            }
        }
    }

    fun reloadNewer() {
        if (loading) {
            animate(0.0, 0)
            return
        }
        animating = true
        pullLatest {
            animating = false
            animate(0.0, 0)
        }
    }

    fun reloadQuiet() {
        if (loading || animating || dragging || feedDialogOpen()) return
        pullLatest()
    }

    fun commit(direction: Int) {
        if (animating) return
        val height = stageHeight()
        if (direction < 0 && index == 0) {
            reloadNewer()
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

    fun currentPhotos(): PhotoPager? = photoPagers[index]

    fun resistedPhoto(raw: Double): Double {
        val pager = currentPhotos() ?: return 0.0
        return when {
            raw > 0 && pager.index == 0 -> raw * 0.35
            raw < 0 && pager.index >= pager.lastIndex -> raw * 0.35
            else -> raw
        }
    }

    stage.asDynamic().addEventListener("pointerdown", { event: dynamic ->
        if (animating || interactive(event.target)) return@addEventListener
        dragging = true
        axis = null
        startX = num(event.clientX)
        startY = num(event.clientY)
        lastX = startX
        lastY = startY
        lastTime = num(event.timeStamp)
        velocity = 0.0
        velocityX = 0.0
        photoDrag = 0.0
        photoPress = photoTarget(event.target)
        if (photoPress) armPeek()
        if (stage.asDynamic().setPointerCapture != undefined) stage.asDynamic().setPointerCapture(event.pointerId)
    })
    stage.asDynamic().addEventListener("pointermove", { event: dynamic ->
        if (peeking) {
            event.preventDefault()
            return@addEventListener
        }
        if (!dragging) return@addEventListener
        val x = num(event.clientX)
        val y = num(event.clientY)
        val time = num(event.timeStamp)
        val dt = (time - lastTime).coerceAtLeast(1.0)
        if (axis == null) {
            val dx = x - startX
            val dy = y - startY
            if (abs(dx) < 10 && abs(dy) < 10) return@addEventListener
            stopPhotoPeekTimer()
            val photos = currentPhotos()
            axis = if (photos != null && photos.size > 1 && abs(dx) > abs(dy)) "x" else "y"
        }
        if (axis == "x") {
            velocityX = (x - lastX) / dt
            lastX = x
            lastTime = time
            photoDrag = resistedPhoto(x - startX)
            currentPhotos()?.drag(photoDrag)
            event.preventDefault()
            return@addEventListener
        }
        feedCheckInBubbles?.hideUntilIdle()
        velocity = (y - lastY) / dt
        lastY = y
        lastTime = time
        drag = resisted(y - startY)
        layout(drag, false)
        event.preventDefault()
    })
    val endPointer = { _: dynamic ->
        if (peeking) {
            endPeek()
            dragging = false
            axis = null
            photoDrag = 0.0
            photoPress = false
        } else if (dragging) {
            dragging = false
            stopPhotoPeekTimer()
            if (axis == "x") {
                val pager = currentPhotos()
                if (pager != null) {
                    val width = pager.width()
                    val flung = abs(velocityX) > 0.45
                    val far = abs(photoDrag) > width * 0.18
                    if (flung || far) pager.go(if (velocityX < -0.45 || (!flung && photoDrag < 0)) 1 else -1)
                    else pager.go(0)
                }
            } else if (axis == null && photoPress) {
                val pager = currentPhotos()
                if (pager == null || pager.size < 2) {
                    tapPeek()
                } else {
                    val bounds = pager.host.asDynamic().getBoundingClientRect()
                    val left = num(bounds.left)
                    val width = num(bounds.width)
                    when {
                        width > 0.0 && startX < left + width * 0.25 -> {
                            if (pager.index > 0) pager.go(-1)
                        }
                        width > 0.0 && startX >= left + width * 0.75 -> {
                            if (pager.index < pager.lastIndex) pager.go(1)
                        }
                        else -> tapPeek()
                    }
                }
            } else {
                val height = stageHeight()
                val flung = abs(velocity) > 0.45
                val far = abs(drag) > height * 0.18
                if (flung || far) commit(if (velocity < -0.45 || (!flung && drag < 0)) 1 else -1) else animate(0.0, 0)
            }
            axis = null
            photoDrag = 0.0
            photoPress = false
        }
    }
    stage.asDynamic().addEventListener("pointerup", endPointer)
    stage.asDynamic().addEventListener("pointercancel", endPointer)
    stage.asDynamic().addEventListener("contextmenu", { event: dynamic ->
        if (peeking || photoPeekTimer != null) event.preventDefault()
    })
    stage.asDynamic().addEventListener("wheel", { event: dynamic ->
        event.preventDefault()
        if (animating || dragging || peeking || wheelLock) return@addEventListener
        val dx = num(event.deltaX)
        val dy = num(event.deltaY)
        val photos = currentPhotos()
        if (photos != null && photos.size > 1 && abs(dx) > abs(dy) && abs(dx) >= 24) {
            wheelLock = true
            photos.go(if (dx > 0) 1 else -1)
            window.asDynamic().setTimeout({ wheelLock = false }, 520)
            return@addEventListener
        }
        if (abs(dy) < 24) return@addEventListener
        feedCheckInBubbles?.hideUntilIdle()
        wheelLock = true
        commit(if (dy > 0) 1 else -1)
        window.asDynamic().setTimeout({ wheelLock = false }, 520)
    }, js("({passive:false})"))

    ensureKeys()
    feedKeyHandler = { key ->
        if (!feedDialogOpen()) {
            when (key) {
                "ArrowDown", "PageDown" -> {
                    feedCheckInBubbles?.hideUntilIdle()
                    commit(1)
                }
                "ArrowUp", "PageUp" -> {
                    feedCheckInBubbles?.hideUntilIdle()
                    commit(-1)
                }
                "ArrowRight" -> currentPhotos()?.takeIf { it.size > 1 }?.go(1)
                "ArrowLeft" -> currentPhotos()?.takeIf { it.size > 1 }?.go(-1)
            }
        }
    }
    location.onClick {
        val post = posts.getOrNull(index) ?: return@onClick
        val place = post.location.trim()
        if (place.isBlank()) return@onClick
        openLocationSheet(host, router, place, post.placeId, post.lat, post.lng, post.locationId)
    }
    ensureResize()
    feedResizeHandler = {
        if (!animating && !dragging) layout(0.0, true)
        else layout(drag, false)
        photoPagers.values.forEach { it.layout() }
    }
    feedResumeHandler = { reloadQuiet() }
    rebuild()
    if (index >= posts.lastIndex - 1) loadMore()
    startSwipeHint(host) { index < 1 }
}

private class BuiltSlide(val root: HTMLElement, val pager: PhotoPager?)

private fun buildSlide(
    post: PostCard,
    host: HTMLElement,
    router: HistoryRouter,
    onUpdate: (PostCard) -> Unit,
    @Suppress("UNUSED_PARAMETER") replay: () -> Unit,
): BuiltSlide {
    var current = post
    var eventEl: HTMLElement? = null
    var pollEl: HTMLElement? = null
    var questionEl: HTMLElement? = null
    var openChatEl: HTMLElement? = null
    fun update(next: PostCard) {
        current = next
        onUpdate(next)
    }
    fun replace(old: HTMLElement, next: HTMLElement): HTMLElement {
        old.asDynamic().replaceWith(next)
        return next
    }
    fun updateFromSheet(next: PostCard) {
        update(next)
        eventEl = eventEl?.let { replace(it, eventCard(next, host, router, ::update)) }
        pollEl = pollEl?.let { replace(it, pollCard(next, host, router, ::update)) }
        questionEl = questionEl?.let { replace(it, questionCard(next, host, router, ::update)) }
        openChatEl = openChatEl?.let { replace(it, openChatCard(next, host, router, ::update)) }
    }
    val slide = el("article", "slide")
    val urls = post.allPhotoUrls().ifEmpty { listOf(post.photoUrl) }
    val photo = el("div", "photo")
    val track = el("div", "photo-track")
    val frames = urls.map { url ->
        val frame = el("div", "photo-frame")
        frame.setAttribute("data-url", url)
        track.appendChild(frame)
        frame
    }
    photo.appendChild(track)
    slide.appendChild(photo)
    val stack = el("div", "card-stack")
    if (!post.avatarUrl.isNullOrBlank()) {
        val avatar = el("img", "avatar")
        avatar.setAttribute("alt", "")
        cropAvatarToContent(avatar)
        avatar.setAttribute("src", post.avatarUrl)
        stack.appendChild(avatar)
    }
    val dots = if (urls.size > 1) {
        val row = el("div", "photo-dots")
        urls.forEach { _ -> row.appendChild(el("span", "photo-dot")) }
        row
    } else {
        null
    }
    val meta = el("div", "slide-meta")
    if (dots != null) meta.appendChild(dots)
    if (!post.musicName.isNullOrBlank()) meta.appendChild(musicLine(post))
    if (meta.childNodes.length > 0) stack.appendChild(meta)
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
    name.onClick {
        openMiniProfile(
            host = host,
            name = post.authorName,
            photoUrl = post.authorPhotoUrl,
            location = post.authorLocation,
            intro = post.authorIntro,
            userId = post.authorId,
            router = router
        )
    }
    card.appendChild(name)
    val message = el("p", "message")
    val communityId = post.communityId
    val communityName = post.communityName
    if (!communityId.isNullOrBlank() && !communityName.isNullOrBlank()) {
        val chip = communityChip(
            router,
            CommunityCard(communityId, communityName, post.communityEmoji ?: "✨"),
        )
        chip.asDynamic().classList.add("in-message")
        chip.asDynamic().addEventListener("click", { event: dynamic ->
            event.stopPropagation()
        })
        message.appendChild(chip)
        message.asDynamic().classList.add("has-community")
    }
    message.appendChild(el("span", "message-copy"))
    if (postMessageNeedsMore(post.message)) {
        val openMore = {
            if (message.asDynamic().classList.contains("ready")) {
                openPostSheet(current, host, router, ::updateFromSheet)
            }
        }
        message.appendChild(el("span", "see-more-ellipsis", text = "…"))
        val more = el("button", "see-more", text = Town.t("seeMore"))
        more.setAttribute("type", "button")
        more.onClick { openMore() }
        message.appendChild(more)
        message.onClick { openMore() }
    }
    val time = el("span", "message-time")
    message.appendChild(time)
    paintPostTime(time, post.createdAt)
    if (post.communityOnly) {
        val exclusive = el("span", "message-star")
        exclusive.textContent = "⭐"
        exclusive.title = Town.t("communityOnly")
        message.appendChild(exclusive)
    }
    card.appendChild(message)
    if (post.hasEvent) {
        val node = eventCard(post, host, router, ::update)
        eventEl = node
        card.appendChild(node)
    }
    if (post.hasPoll) {
        val node = pollCard(post, host, router, ::update)
        pollEl = node
        card.appendChild(node)
    }
    if (post.hasQuestion) {
        val node = questionCard(post, host, router, ::update)
        questionEl = node
        card.appendChild(node)
    }
    if (post.hasCta) {
        card.appendChild(ctaCard(post, host, router))
    }
    if (post.hasOpenChat) {
        val node = openChatCard(post, host, router, ::update)
        openChatEl = node
        card.appendChild(node)
    }
    card.appendChild(collapsedChat(post, host, router, ::update))
    stack.appendChild(card)
    slide.appendChild(stack)
    val pager = PhotoPager(urls, photo, track, frames, dots).also { it.preload() }
    return BuiltSlide(slide, pager)
}

private fun openPostSheet(
    post: PostCard,
    host: HTMLElement,
    router: HistoryRouter,
    onUpdate: (PostCard) -> Unit,
) {
    if (host.asDynamic().querySelector(".post-sheet") != null) return
    val chrome = SheetChrome("comment-sheet post-sheet", post.authorName)
    val body = el("div", "post-sheet-body")
    body.appendChild(el("p", "post-sheet-message", text = post.message))
    if (post.hasEvent) body.appendChild(eventCard(post, host, router, onUpdate))
    if (post.hasPoll) body.appendChild(pollCard(post, host, router, onUpdate))
    if (post.hasQuestion) body.appendChild(questionCard(post, host, router, onUpdate))
    if (post.hasCta) body.appendChild(ctaCard(post, host, router))
    if (post.hasOpenChat) body.appendChild(openChatCard(post, host, router, onUpdate))
    chrome.sheet.appendChild(body)
    chrome.open(host)
}

private class PhotoPager(
    val urls: List<String>,
    val host: HTMLElement,
    val track: HTMLElement,
    val frames: List<HTMLElement>,
    val dots: HTMLElement?,
) {
    var index = 0
        private set
    val size: Int get() = urls.size
    val lastIndex: Int get() = urls.lastIndex
    private var shift = 0.0
    private var animating = false

    fun width(): Double {
        val w = num(host.asDynamic().clientWidth)
        return if (w > 40.0) w else 400.0
    }

    fun layout(offset: Double = shift) {
        shift = offset
        track.asDynamic().style.transform = "translate3d(${-index * width() + offset}px, 0, 0)"
        syncDots()
    }

    fun drag(offset: Double) {
        if (animating || size < 2) return
        layout(offset)
    }

    fun go(delta: Int) {
        if (animating) return
        val next = (index + delta).coerceIn(0, lastIndex)
        val width = width()
        if (delta == 0 || next == index) {
            animateTo(0.0, 0)
            return
        }
        animateTo((index - next) * width, next - index)
    }

    fun preload() {
        val last = urls.lastIndex
        for (i in index..(index + 2).coerceAtMost(last)) load(i)
        if (index > 0) load(index - 1)
        layout(0.0)
    }

    private fun load(i: Int) {
        if (i !in frames.indices) return
        val frame = frames[i]
        if (frame.getAttribute("data-loaded") == "1") return
        frame.asDynamic().style.backgroundImage = "url(\"${urls[i]}\")"
        frame.setAttribute("data-loaded", "1")
    }

    private fun syncDots() {
        val row = dots ?: return
        val nodes = row.asDynamic().children
        val count = nodes.length as Int
        for (i in 0 until count) {
            nodes[i].className = if (i == index) "photo-dot on" else "photo-dot"
        }
    }

    private fun animateTo(target: Double, delta: Int) {
        animating = true
        val origin = shift
        val started = now()
        fun frame() {
            val t = ((now() - started) / 280.0).coerceIn(0.0, 1.0)
            val eased = 1 - (1 - t) * (1 - t)
            layout(origin + (target - origin) * eased)
            if (t < 1.0) {
                window.asDynamic().requestAnimationFrame { frame() }
            } else {
                index = (index + delta).coerceIn(0, lastIndex)
                shift = 0.0
                animating = false
                preload()
            }
        }
        frame()
    }
}

internal fun showVisitorWelcome(host: HTMLElement) {
    if (host.asDynamic().querySelector(".dialog-backdrop") != null) return
    val backdrop = el("div", "dialog-backdrop")
    val card = el("div", "dialog visitor-welcome")
    card.setAttribute("role", "dialog")
    card.setAttribute("aria-modal", "true")
    card.setAttribute("aria-labelledby", "visitor-welcome-text")
    val close = el("button", "icon-btn visitor-welcome-close", text = "×")
    close.setAttribute("type", "button")
    close.setAttribute("aria-label", Town.t("close"))
    close.onClick { backdrop.asDynamic().remove() }
    card.appendChild(close)
    val emojis = el("div", "visitor-welcome-emojis")
    emojis.setAttribute("aria-hidden", "true")
    listOf("📍", "😍", "🍵").forEachIndexed { index, emoji ->
        emojis.appendChild(el("span", "bob bob-${index + 1}", text = emoji))
    }
    card.appendChild(emojis)
    val message = el("p", "visitor-welcome-text", text = Town.t("visitorWelcome"))
    message.setAttribute("id", "visitor-welcome-text")
    card.appendChild(message)
    fun updateMessageFade() {
        val top = num(message.asDynamic().scrollTop)
        val height = num(message.asDynamic().clientHeight)
        val full = num(message.asDynamic().scrollHeight)
        val overflows = full > height + 1
        message.asDynamic().classList.toggle("fade-top", overflows && top > 1)
        message.asDynamic().classList.toggle("fade-bottom", overflows && top + height < full - 1)
    }
    message.asDynamic().addEventListener("scroll", { updateMessageFade() })
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
    updateMessageFade()
}

internal fun openMiniProfile(
    host: HTMLElement,
    name: String,
    photoUrl: String?,
    location: String = "",
    intro: String = "",
    userId: String = "",
    router: HistoryRouter? = null,
) {
    if (host.asDynamic().querySelector(".profile-sheet") != null) return
    val chrome = SheetChrome("comment-sheet profile-sheet", name)
    val body = el("div", "profile-sheet-body")
    val head = el("div", "profile-sheet-head")
    if (!photoUrl.isNullOrBlank()) {
        val face = el("img", "face large")
        face.setAttribute("alt", "")
        face.setAttribute("src", photoUrl)
        head.appendChild(face)
    } else {
        head.appendChild(el("span", "face large initial", text = name.take(1).uppercase()))
    }
    if (location.isNotBlank()) {
        head.appendChild(el("p", "mini-profile-location", text = location))
    }
    if (intro.isNotBlank()) {
        head.appendChild(el("p", "mini-profile-intro", text = intro))
    }
    if (userId.isNotBlank()) {
        val row = el("div", "mini-communities")
        head.appendChild(row)
        http("GET", "/api/users/${encodeQuery(userId)}/communities") { status, body ->
            if (status != 200) return@http
            val communities = readCommunities(parseJson(body).communities)
            communities.take(Limits.MINI_PROFILE_COMMUNITIES).forEach { community ->
                row.appendChild(communityChip(router, community) { chrome.close() })
            }
            val remaining = communities.drop(Limits.MINI_PROFILE_COMMUNITIES)
            if (remaining.isNotEmpty()) {
                val more = el(
                    "button",
                    "text-button mini-communities-more",
                    text = Town.t("moreCommunities", mapOf("count" to remaining.size.toString())),
                )
                more.setAttribute("type", "button")
                more.onClick {
                    remaining.forEach { community ->
                        row.appendChild(communityChip(router, community) { chrome.close() })
                    }
                    more.asDynamic().remove()
                }
                row.appendChild(more)
            }
        }
    }
    if (router != null && userId.isNotBlank() && userId != Town.me?.id) {
        val actions = el("div", "profile-sheet-actions")
        actions.appendChild(el("button", "primary profile-sheet-message", text = Town.t("message")) { button ->
            button.setAttribute("type", "button")
            button.onClick {
                chrome.close()
                openDirectChat(host, router, userId, name)
            }
        })
        val addToGroup = el("button", "icon-btn profile-sheet-add-to-group")
        addToGroup.setAttribute("type", "button")
        addToGroup.setAttribute("aria-label", Town.t("addToGroup", mapOf("name" to name)))
        addToGroup.setAttribute("title", Town.t("addToGroup", mapOf("name" to name)))
        addToGroup.asDynamic().innerHTML = """<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="9" cy="8" r="3" fill="none" stroke="currentColor" stroke-width="2"/><path d="M3 19c0-3 2.5-5 6-5s6 2 6 5M19 8v8M15 12h8" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>"""
        addToGroup.onClick {
            chrome.close()
            openAddToGroup(host, userId, name)
        }
        actions.appendChild(addToGroup)
        head.appendChild(actions)
    }
    if (userId.isNotBlank()) {
        head.appendChild(copyLinkButton(host, "/u/$userId", "text-button profile-sheet-copy-link"))
    }
    body.appendChild(head)
    if (userId.isNotBlank() && router != null) body.appendChild(miniProfileActivities(userId, router, host))
    if (userId.isNotBlank() && router != null) body.appendChild(miniProfileProducts(userId, router, host))
    val countHead = el("h3", "profile-sheet-count")
    countHead.asDynamic().hidden = true
    val grid = el("div", "location-sheet-grid")
    if (userId.isNotBlank() && router != null) {
        body.appendChild(countHead)
        body.appendChild(grid)
    }
    chrome.sheet.appendChild(body)

    var cursor: String? = null
    var loading = false
    var done = false

    fun load() {
        if (router == null || userId.isBlank() || loading || done) return
        loading = true
        val next = cursor
        val more = if (next.isNullOrBlank()) "" else "&cursor=${encodeQuery(next)}"
        http("GET", "/api/users/${encodeQuery(userId)}/posts?limit=24$more") { status, text ->
            loading = false
            if (status != 200) {
                if (grid.childElementCount == 0) {
                    grid.appendChild(el("p", "hint", text = Town.t("unavailable")))
                }
                done = true
                return@http
            }
            val parsed = parseJson(text)
            val total = num(parsed.count).toInt()
            countHead.textContent = Town.t("postCount", mapOf("count" to total.toString()))
            countHead.asDynamic().hidden = false
            val posts = readLocationPosts(parsed.posts)
            if (posts.isEmpty() && grid.childElementCount == 0) {
                grid.appendChild(el("p", "hint", text = Town.t("noPosts")))
            } else {
                posts.forEach { post ->
                    grid.appendChild(locationPostTile(post, router, showAuthor = false, showMessage = true))
                }
            }
            cursor = parsed.nextCursor as? String
            if (cursor.isNullOrBlank()) done = true
        }
    }

    body.asDynamic().addEventListener("scroll", {
        val top = num(body.asDynamic().scrollTop)
        val height = num(body.asDynamic().clientHeight)
        val full = num(body.asDynamic().scrollHeight)
        if (top + height > full - 80) load()
    })
    chrome.open(host) { load() }
}

private val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

internal fun shortPostTime(createdAt: Long): String {
    val now = num(js("Date.now()")).toLong()
    return PostAge.relative(createdAt, now, Town.language) ?: absolutePostDate(createdAt)
}

private fun absolutePostDate(createdAt: Long): String {
    val date = js("new Date(createdAt)")
    val month = monthNames[num(date.getMonth()).toInt().coerceIn(0, 11)]
    val day = num(date.getDate()).toInt()
    val year = num(date.getFullYear()).toInt()
    return "$month $day, $year"
}

private fun paintPostTime(node: dynamic, createdAt: Long) {
    if (node == null || node == undefined) return
    val label = shortPostTime(createdAt)
    node.textContent = label
    node.style.display = if (label.isBlank()) "none" else ""
}

private fun typewrite(node: dynamic, text: String, onDone: () -> Unit = {}): () -> Unit {
    var index = 0
    var timer: dynamic = null
    var stopped = false
    fun step() {
        if (stopped) return
        index = minOf(text.length, index + 1)
        node.textContent = text.substring(0, index)
        if (index < text.length) {
            timer = window.asDynamic().setTimeout({ step() }, Limits.TYPEWRITER_MILLIS_PER_CHAR)
        } else {
            onDone()
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
        val list = node.classList
        if (list != null && list != undefined && list.contains("message") == true && list.contains("ready") == true) {
            return true
        }
        node = node.parentElement
    }
    return false
}

private fun photoTarget(target: dynamic): Boolean {
    var node = target
    while (node != null && node != undefined) {
        val list = node.classList
        if (list != null && list != undefined) {
            if (list.contains("card-stack") == true) return false
            if (list.contains("photo") == true) return true
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

private fun stopPhotoPeekTimer() {
    if (photoPeekTimer != null && photoPeekTimer != undefined) {
        window.asDynamic().clearTimeout(photoPeekTimer)
    }
    photoPeekTimer = null
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
        val paging = key == "ArrowDown" || key == "ArrowUp" || key == "PageDown" || key == "PageUp" ||
            key == "ArrowLeft" || key == "ArrowRight"
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
