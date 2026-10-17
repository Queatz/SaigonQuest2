package quest.saigon.web

import quest.saigon.TextLinks
import web.dom.document
import web.events.EventHandler
import web.html.HTMLElement
import web.window.window

internal fun el(
    tag: String,
    className: String? = null,
    text: String? = null,
    setup: (HTMLElement) -> Unit = {},
): HTMLElement {
    val node = document.createElement(tag)
    if (className != null) {
        node.setAttribute("class", className)
    }
    if (text != null) {
        node.textContent = text
    }
    setup(node)
    return node
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

internal fun HTMLElement.closeOnOutsideClick(onClose: () -> Unit) {
    keepAboveKeyboard()
    asDynamic().addEventListener("click", { event: dynamic ->
        if (event.target == event.currentTarget) onClose()
    })
}

    internal class SheetChrome(
    className: String,
    title: String,
    private val beforeClose: () -> Unit = {},
) {
    val sheet: HTMLElement = el("div", className)
    private val backdrop: HTMLElement = el("div", "dialog-backdrop comment-backdrop")

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
        beforeClose()
        sheet.asDynamic().style.transform = ""
        sheet.asDynamic().classList.remove("open")
        window.asDynamic().setTimeout({ backdrop.asDynamic().remove() }, 280)
    }

    fun open(host: HTMLElement, afterOpen: () -> Unit = {}) {
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
