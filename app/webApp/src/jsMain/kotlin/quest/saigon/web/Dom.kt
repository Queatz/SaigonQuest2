package quest.saigon.web

import web.dom.document
import web.events.EventHandler
import web.html.HTMLElement

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

internal fun routeLink(router: HistoryRouter, path: String, label: String, active: Boolean): HTMLElement =
    el("a", className = if (active) "active" else null, text = label) { anchor ->
        anchor.setAttribute("href", path)
        anchor.onclick = EventHandler { event ->
            event.preventDefault()
            router.navigate(path)
        }
    }
