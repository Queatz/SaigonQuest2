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
    Town.applyLanguage()
    router.start()
    http("GET", "/api/me") { status, body ->
        if (status != 200) return@http
        try {
            Town.me = meFrom(parseJson(body))
            Town.applyLanguage()
            refresh(router)
        } catch (_: Throwable) {
        }
    }
}
