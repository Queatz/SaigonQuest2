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

internal fun linkedParagraph(text: String, className: String? = null): HTMLElement {
    val node = el("p", className)
    TextLinks.pieces(text).forEach { piece ->
        val href = piece.href
        if (href == null) {
            node.asDynamic().append(piece.text)
        } else {
            val link = el("a", text = piece.text)
            link.setAttribute("href", href)
            link.setAttribute("target", "_blank")
            link.setAttribute("rel", "noopener noreferrer")
            link.asDynamic().addEventListener("click", { event: dynamic ->
                event.stopPropagation()
            })
            node.appendChild(link)
        }
    }
    return node
}

internal fun routeLink(router: HistoryRouter, path: String, label: String, active: Boolean): HTMLElement =
    el("a", className = if (active) "active" else null, text = label) { anchor ->
        anchor.setAttribute("href", path)
        anchor.onclick = EventHandler { event ->
            event.preventDefault()
            router.navigate(path)
        }
    }
