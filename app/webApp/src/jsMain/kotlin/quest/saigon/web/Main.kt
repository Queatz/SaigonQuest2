package quest.saigon.web

import web.dom.ElementId
import web.dom.document

fun main() {
    val root = document.getElementById(ElementId("root"))
        ?: error("Failed to find the root element")

    lateinit var router: HistoryRouter
    router = HistoryRouter { route ->
        renderApp(root, router, route)
    }
    router.start()
}
