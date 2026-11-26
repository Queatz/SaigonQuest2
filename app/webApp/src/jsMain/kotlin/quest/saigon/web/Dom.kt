@file:OptIn(ExperimentalWasmJsInterop::class)

package quest.saigon.web

import quest.saigon.TextLinks
import web.dom.document
import web.events.EventHandler
import web.history.history
import web.html.HTMLElement
import web.location.location
import web.window.window
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.math.roundToInt

internal fun el(
    tag: String,
    className: String? = null,
    text: String? = null,
    setup: (HTMLElement) -> Unit = {},
): HTMLElement {
    val node = document.createElement(tag)
    if (className != null && className.contains("dialog-backdrop")) installBackdropFade()
    if (className != null) {
        node.setAttribute("class", className)
    }
    if (text != null) {
        node.textContent = text
    }
    setup(node)
    return node
}

private var backdropFadeInstalled = false

private fun installBackdropFade() {
    if (backdropFadeInstalled) return
    backdropFadeInstalled = true
    js(
        """
        (function () {
            var proto = Element.prototype;
            var original = proto.remove;
            proto.remove = function () {
                var node = this;
                if (node.classList && node.classList.contains('dialog-backdrop') && node.isConnected) {
                    if (node.classList.contains('closing')) return;
                    node.classList.add('closing');
                    setTimeout(function () { original.call(node); }, 250);
                } else {
                    original.call(node);
                }
            };
        })();
        """
    )
}

internal fun HTMLElement.clear() {
    while (true) {
        val child = firstChild ?: break
        removeChild(child)
    }
}

internal fun HTMLElement.onClick(handler: () -> Unit) {
    onclick = EventHandler {
        handler()
    }
}

internal fun keyboardInset(): Double {
    val viewport = window.asDynamic().visualViewport
    return if (viewport == null || viewport == undefined) {
        0.0
    } else {
        (num(window.innerHeight) - num(viewport.height) - num(viewport.offsetTop)).coerceAtLeast(0.0)
    }
}

private var keyboardOverlaysBound = false

internal fun HTMLElement.keepAboveKeyboard() {
    bindKeyboardOverlays()
    asDynamic().style.bottom = "${keyboardInset()}px"
}

private fun bindKeyboardOverlays() {
    if (keyboardOverlaysBound) return
    keyboardOverlaysBound = true
    val viewport = window.asDynamic().visualViewport
    if (viewport == null || viewport == undefined) return
    val sync = {
        val inset = "${keyboardInset()}px"
        val nodes = document.asDynamic().querySelectorAll(".dialog-backdrop")
        val length = nodes.length as Int
        for (i in 0 until length) {
            nodes[i].style.bottom = inset
        }
    }
    viewport.addEventListener("resize", sync)
    viewport.addEventListener("scroll", sync)
}

private var escapeOverlaysBound = false

private fun bindEscapeOverlays() {
    if (escapeOverlaysBound) return
    escapeOverlaysBound = true
    document.asDynamic().addEventListener("keydown", { event: dynamic ->
        if (event.defaultPrevented == true || event.key != "Escape") return@addEventListener
        val openBackdrops = document.asDynamic().querySelectorAll(".dialog-backdrop:not(.closing)")
        val length = openBackdrops.length as Int
        if (length <= 0) return@addEventListener
        val top = openBackdrops[length - 1]
        val close = top?.__saigonClose
        if (close != null && close != undefined) {
            event.preventDefault()
            close()
        }
    })
}

internal fun HTMLElement.closeOnOutsideClick(onClose: () -> Unit) {
    keepAboveKeyboard()
    bindEscapeOverlays()
    asDynamic().__saigonClose = onClose
    asDynamic().addEventListener("click", { event: dynamic ->
        if (event.target == event.currentTarget) onClose()
    })
}

private class OverlayHistoryEntry(
    val closeFromHistory: () -> Unit,
    val closeForRouteChange: () -> Unit,
)

private val openOverlayHistory = mutableListOf<OverlayHistoryEntry>()
private var ignoreNextOverlayPopState = false

internal fun consumeSheetBack(): Boolean {
    if (ignoreNextOverlayPopState) {
        ignoreNextOverlayPopState = false
        return true
    }
    val overlay = openOverlayHistory.lastOrNull() ?: return false
    overlay.closeFromHistory()
    return true
}

internal fun clearSheetStack() {
    openOverlayHistory.toList().forEach { it.closeForRouteChange() }
    openOverlayHistory.clear()
}

internal fun pushOverlayHistory(
    url: String = location.href,
    onPop: () -> Unit,
    onRouteChange: () -> Unit = onPop,
): () -> Unit {
    history.pushState(null, "", url)
    lateinit var entry: OverlayHistoryEntry
    entry = OverlayHistoryEntry(
        closeFromHistory = {
            openOverlayHistory.remove(entry)
            onPop()
        },
        closeForRouteChange = {
            openOverlayHistory.remove(entry)
            onRouteChange()
        },
    )
    openOverlayHistory.add(entry)
    return {
        openOverlayHistory.remove(entry)
    }
}

internal fun popOverlayHistory(removeEntry: () -> Unit) {
    removeEntry()
    ignoreNextOverlayPopState = true
    history.back()
}

internal class SheetChrome(
    className: String,
    title: String,
    private val beforeClose: () -> Unit = {},
) {
    val sheet: HTMLElement = el("div", className)
    private val backdrop: HTMLElement = el("div", "dialog-backdrop comment-backdrop")
    private var isOpen = false
    private lateinit var historyEntry: OverlayHistoryEntry

    init {
        sheet.setAttribute("role", "dialog")
        sheet.setAttribute("aria-modal", "true")
        val grab = el("div", "sheet-grab")
        val handle = el("div", "sheet-handle")
        handle.setAttribute("aria-hidden", "true")
        val bar = el("div", "comment-bar")
        bar.appendChild(el("h2", text = title))
        bar.appendChild(el("button", "icon-btn sheet-close", text = "×") { button ->
            button.setAttribute("type", "button")
            button.setAttribute("aria-label", Town.t("close"))
            button.onClick { close() }
        })
        grab.appendChild(handle)
        grab.appendChild(bar)
        sheet.appendChild(grab)
        backdrop.appendChild(sheet)
        backdrop.closeOnOutsideClick { close() }
        bindSheetDrag(grab, sheet) { close() }
    }

    fun close() {
        dismiss(updateHistory = true)
    }

    internal fun closeFromHistory() {
        dismiss(updateHistory = false)
    }

    internal fun closeForRouteChange() {
        dismiss(updateHistory = false)
    }

    private fun dismiss(updateHistory: Boolean) {
        if (!isOpen) return
        isOpen = false
        openOverlayHistory.remove(historyEntry)
        beforeClose()
        sheet.asDynamic().style.transform = ""
        sheet.asDynamic().classList.remove("open")
        backdrop.asDynamic().remove()
        if (updateHistory) {
            ignoreNextOverlayPopState = true
            history.back()
        }
    }

    fun open(host: HTMLElement, afterOpen: () -> Unit = {}) {
        if (isOpen) return
        isOpen = true
        history.pushState(null, "", location.href)
        historyEntry = OverlayHistoryEntry(::closeFromHistory, ::closeForRouteChange)
        openOverlayHistory.add(historyEntry)
        host.appendChild(backdrop)
        window.asDynamic().requestAnimationFrame {
            window.asDynamic().requestAnimationFrame {
                sheet.asDynamic().classList.add("open")
                afterOpen()
            }
        }
    }
}

private fun bindSheetDrag(grab: HTMLElement, sheet: HTMLElement, onClose: () -> Unit) {
    var dragStart = 0.0
    var dragging = false
    fun fromControl(target: dynamic): Boolean {
        var node = target
        while (node != null && node != undefined && node != grab) {
            val tag = (node.tagName as? String)?.lowercase()
            if (tag == "button" || tag == "a" || tag == "input" || tag == "textarea" || tag == "select") return true
            node = node.parentElement
        }
        return false
    }
    grab.asDynamic().addEventListener("pointerdown", { event: dynamic ->
        if (fromControl(event.target)) return@addEventListener
        event.preventDefault()
        dragging = true
        dragStart = num(event.clientY)
        if (grab.asDynamic().setPointerCapture != undefined) grab.asDynamic().setPointerCapture(event.pointerId)
    })
    grab.asDynamic().addEventListener("pointermove", { event: dynamic ->
        if (!dragging) return@addEventListener
        event.preventDefault()
        val delta = (num(event.clientY) - dragStart).coerceAtLeast(0.0)
        sheet.asDynamic().style.transform = "translate3d(0, ${delta}px, 0)"
    })
    val end = { event: dynamic ->
        if (dragging) {
            dragging = false
            val delta = num(event.clientY) - dragStart
            if (delta > 90) onClose() else sheet.asDynamic().style.transform = ""
        }
    }
    grab.asDynamic().addEventListener("pointerup", end)
    grab.asDynamic().addEventListener("pointercancel", end)
}

internal fun linkedParagraph(text: String, className: String? = null): HTMLElement {
    val node = el("p", className)
    appendLinkedText(node, text)
    return node
}

internal fun appendLinkedText(host: HTMLElement, text: String) {
    TextLinks.pieces(text).forEach { piece ->
        val href = piece.href
        if (href == null) {
            host.asDynamic().append(piece.text)
        } else {
            val link = el("a", text = piece.text)
            link.setAttribute("href", href)
            link.setAttribute("target", "_blank")
            link.setAttribute("rel", "noopener noreferrer")
            link.asDynamic().addEventListener("click", { event: dynamic ->
                event.stopPropagation()
            })
            host.appendChild(link)
        }
    }
}

internal fun routeLink(router: HistoryRouter, path: String, label: String, active: Boolean): HTMLElement =
    el("a", className = if (active) "active" else null, text = label) { anchor ->
        anchor.setAttribute("href", path)
        anchor.onclick = EventHandler { event ->
            event.preventDefault()
            router.navigate(path)
        }
    }

internal fun openPhotoViewer(
    source: List<UserPhoto>,
    startIndex: Int,
    onDelete: ((String, () -> Unit) -> Unit)? = null,
) {
    if (source.isEmpty()) return
    val selected = startIndex.coerceIn(source.indices)
    var photos = source.toList()
    var current = selected
    var closed = false
    var dragging = false
    var settling = false
    var dragStartX = 0.0
    var dragStartScroll = 0.0
    var dragStartIndex = 0
    var settleTimeout: dynamic = null
    lateinit var keyHandler: (dynamic) -> Unit
    lateinit var historyEntry: OverlayHistoryEntry

    val backdrop = el("div", "dialog-backdrop photo-viewer-backdrop")
    backdrop.setAttribute("role", "dialog")
    backdrop.setAttribute("aria-modal", "true")
    val viewer = el("div", "photo-viewer")
    val frame = el("div", "photo-viewer-frame")
    val stage = el("div", "photo-viewer-stage")
    val photoZoom = PhotoViewerZoom(stage)
    val previous = el("button", "photo-viewer-arrow photo-viewer-previous")
    previous.asDynamic().innerHTML = """
        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
            <path d="M15 18 9 12 15 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
    """.trimIndent()
    val next = el("button", "photo-viewer-arrow photo-viewer-next")
    next.asDynamic().innerHTML = """
        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
            <path d="M9 18 15 12 9 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
    """.trimIndent()
    val close = el("button", "photo-viewer-close", text = "×")
    val remove = el("button", "photo-viewer-delete")
    remove.asDynamic().innerHTML = """
        <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 7h16M10 11v6M14 11v6M5 7l1 14h12l1-14M9 7V4h6v3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
    """.trimIndent()
    val thumbs = el("div", "photo-viewer-thumbs")
    val thumbButtons = mutableListOf<HTMLElement>()
    var viewerImages = emptyList<HTMLElement>()

    fun updateControls() {
        previous.asDynamic().disabled = current <= 0
        next.asDynamic().disabled = current >= photos.lastIndex
        val photo = photos.getOrNull(current)
        remove.asDynamic().hidden = photo == null || onDelete == null || !photo.canDelete
        thumbButtons.forEachIndexed { index, button ->
            button.asDynamic().classList.toggle("active", index == current)
            if (index == current) button.setAttribute("aria-current", "true") else button.removeAttribute("aria-current")
        }
    }

    fun scrollToPhoto(index: Int, smooth: Boolean = true) {
        if (photos.isEmpty()) return
        current = index.coerceIn(photos.indices)
        photoZoom.setImage(viewerImages.getOrNull(current))
        if (settleTimeout != null && settleTimeout != undefined) {
            window.asDynamic().clearTimeout(settleTimeout)
            settleTimeout = null
        }
        settling = smooth
        val width = num(stage.asDynamic().clientWidth)
        if (width > 0) {
            val options = js("({})")
            options.left = width * current
            options.behavior = if (smooth) "smooth" else "instant"
            stage.asDynamic().scrollTo(options)
        }
        updateControls()
        if (smooth) {
            settleTimeout = window.asDynamic().setTimeout({
                settling = false
                if (!dragging) stage.asDynamic().classList.remove("dragging")
                settleTimeout = null
            }, 700)
        }
    }

    fun closeViewer(updateHistory: Boolean = true) {
        if (closed) return
        closed = true
        photoZoom.reset()
        document.asDynamic().removeEventListener("keydown", keyHandler)
        backdrop.asDynamic().remove()
        openOverlayHistory.remove(historyEntry)
        if (updateHistory) {
            ignoreNextOverlayPopState = true
            history.back()
        }
    }

    fun renderPhotos() {
        stage.clear()
        thumbs.clear()
        thumbButtons.clear()
        val images = mutableListOf<HTMLElement>()
        photos.forEachIndexed { index, photo ->
            val slide = el("div", "photo-viewer-slide")
            val image = el("img", "photo-viewer-image")
            image.setAttribute("src", photo.url)
            image.setAttribute("alt", Town.t("photo"))
            slide.appendChild(image)
            stage.appendChild(slide)
            images += image

            val thumb = el("button", "photo-viewer-thumb")
            thumb.setAttribute("type", "button")
            thumb.setAttribute("aria-label", Town.t("photo"))
            val thumbnail = el("img")
            thumbnail.setAttribute("src", photo.url)
            thumbnail.setAttribute("alt", "")
            thumb.appendChild(thumbnail)
            thumb.onClick { scrollToPhoto(index) }
            thumbButtons += thumb
            thumbs.appendChild(thumb)
        }
        viewerImages = images
        photoZoom.setImage(viewerImages.getOrNull(current))
        updateControls()
    }

    close.setAttribute("type", "button")
    close.setAttribute("aria-label", Town.t("closePhotoViewer"))
    close.onClick { closeViewer() }
    previous.setAttribute("type", "button")
    previous.setAttribute("aria-label", Town.t("previousPhoto"))
    previous.onClick { scrollToPhoto(current - 1) }
    next.setAttribute("type", "button")
    next.setAttribute("aria-label", Town.t("nextPhoto"))
    next.onClick { scrollToPhoto(current + 1) }
    remove.setAttribute("type", "button")
    remove.setAttribute("aria-label", Town.t("deletePhotoAsk"))
    remove.onClick {
        val photo = photos.getOrNull(current) ?: return@onClick
        onDelete?.invoke(photo.id) {
            val index = current
            photos = photos.filterNot { it.id == photo.id }
            if (photos.isEmpty()) {
                closeViewer()
            } else {
                current = index.coerceAtMost(photos.lastIndex)
                renderPhotos()
                window.asDynamic().requestAnimationFrame { scrollToPhoto(current, smooth = false) }
            }
        }
    }

    stage.asDynamic().addEventListener("scroll", {
        val width = num(stage.asDynamic().clientWidth)
        if (width > 0 && !dragging && !settling) {
            val index = (num(stage.asDynamic().scrollLeft) / width).roundToInt().coerceIn(0, photos.lastIndex)
            if (index != current) {
                current = index
                photoZoom.setImage(viewerImages.getOrNull(current))
                updateControls()
            }
        }
    })
    stage.asDynamic().addEventListener("scrollend", {
        if (!dragging) {
            if (settleTimeout != null && settleTimeout != undefined) {
                window.asDynamic().clearTimeout(settleTimeout)
                settleTimeout = null
            }
            settling = false
            val width = num(stage.asDynamic().clientWidth)
            if (width > 0) {
                val index = (num(stage.asDynamic().scrollLeft) / width).roundToInt().coerceIn(0, photos.lastIndex)
                if (index != current) {
                    current = index
                    photoZoom.setImage(viewerImages.getOrNull(current))
                    updateControls()
                }
            }
            stage.asDynamic().classList.remove("dragging")
        }
    })
    stage.asDynamic().addEventListener("pointerdown", { event: dynamic ->
        if (event.button != 0) return@addEventListener
        val action = photoZoom.pointerDown(event)
        if (action == PhotoViewerPointerAction.IGNORE) return@addEventListener
        if (settleTimeout != null && settleTimeout != undefined) {
            window.asDynamic().clearTimeout(settleTimeout)
            settleTimeout = null
        }
        settling = false
        when (action) {
            PhotoViewerPointerAction.SWIPE_START -> {
                dragging = true
                dragStartX = num(event.clientX)
                dragStartScroll = num(stage.asDynamic().scrollLeft)
                dragStartIndex = current
                stage.asDynamic().classList.add("dragging")
                if (event.pointerType == "mouse" && stage.asDynamic().setPointerCapture != undefined) {
                    stage.asDynamic().setPointerCapture(event.pointerId)
                }
            }
            PhotoViewerPointerAction.CANCEL_SWIPE -> {
                if (dragging) {
                    dragging = false
                    stage.asDynamic().classList.remove("dragging")
                    scrollToPhoto(dragStartIndex, smooth = false)
                }
            }
            else -> Unit
        }
    })
    stage.asDynamic().addEventListener("pointermove", { event: dynamic ->
        when (photoZoom.pointerMove(event)) {
            PhotoViewerPointerAction.SWIPE_MOVE -> {
                if (!dragging) return@addEventListener
                event.preventDefault()
                stage.asDynamic().scrollLeft = dragStartScroll - (num(event.clientX) - dragStartX)
            }
            PhotoViewerPointerAction.CONSUMED -> event.preventDefault()
            else -> Unit
        }
    })
    val endDrag: (dynamic) -> Unit = { event ->
        val action = photoZoom.pointerUp(event)
        if (action == PhotoViewerPointerAction.SWIPE_END && dragging) {
            dragging = false
            val width = num(stage.asDynamic().clientWidth)
            val dragDistance = dragStartX - num(event.clientX)
            val target = when {
                width > 0 && dragDistance > width * 0.15 -> dragStartIndex + 1
                width > 0 && dragDistance < -width * 0.15 -> dragStartIndex - 1
                else -> dragStartIndex
            }
            scrollToPhoto(target)
            settleTimeout = window.asDynamic().setTimeout({
                if (!dragging) stage.asDynamic().classList.remove("dragging")
                settleTimeout = null
            }, 700)
        }
    }
    stage.asDynamic().addEventListener("pointerup", endDrag)
    stage.asDynamic().addEventListener("pointercancel", endDrag)

    keyHandler = { event ->
        when (event.key as? String) {
            "Escape" -> {
                event.preventDefault()
                event.stopPropagation()
                closeViewer()
            }
            "ArrowLeft" -> {
                event.preventDefault()
                scrollToPhoto(current - 1)
            }
            "ArrowRight" -> {
                event.preventDefault()
                scrollToPhoto(current + 1)
            }
        }
    }
    frame.appendChild(stage)
    frame.appendChild(previous)
    frame.appendChild(next)
    viewer.appendChild(close)
    viewer.appendChild(remove)
    viewer.appendChild(frame)
    viewer.appendChild(thumbs)
    backdrop.appendChild(viewer)
    document.asDynamic().addEventListener("keydown", keyHandler)
    document.body.appendChild(backdrop)
    history.pushState(null, "", location.href)
    historyEntry = OverlayHistoryEntry({ closeViewer(updateHistory = false) }, { closeViewer(updateHistory = false) })
    openOverlayHistory.add(historyEntry)
    renderPhotos()
    window.asDynamic().requestAnimationFrame { scrollToPhoto(selected, smooth = false) }
}
