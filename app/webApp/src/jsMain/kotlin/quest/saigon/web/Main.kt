package quest.saigon.web

import web.dom.ElementId
import web.dom.document
import web.navigator.navigator

fun main() {
    val root = document.getElementById(ElementId("root"))
        ?: error("Failed to find the root element")

    lateinit var router: HistoryRouter
    router = HistoryRouter { route ->
        renderApp(root, router, route)
    }
    Town.applyLanguage()
    router.start()
    registerServiceWorker()
    http("GET", "/api/me") { status, body ->
        if (status == 200) {
            try {
                Town.me = meFrom(parseJson(body))
                Town.applyLanguage()
                NotifyInbox.connect()
                refresh(router)
            } catch (_: Throwable) {
            }
        }
        recordAppVisit()
    }
}

private fun registerServiceWorker() {
    try {
        val serviceWorker = navigator.asDynamic().serviceWorker
        if (serviceWorker != null) {
            serviceWorker.register("/sw.js")
        }
    } catch (_: Throwable) {
    }
}
