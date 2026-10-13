package quest.saigon.web

import web.dom.ElementId
import web.dom.document
import web.navigator.navigator
import web.window.window

fun main() {
    val root = document.getElementById(ElementId("root"))
        ?: error("Failed to find the root element")

    lateinit var router: HistoryRouter
    router = HistoryRouter { route ->
        renderApp(root, router, route)
    }
    Town.applyLanguage()
    router.start()
    listenForTabResume()
    registerServiceWorker()
    http("GET", "/api/me") { status, body ->
        if (status == 200) {
            try {
                Town.me = meFrom(parseJson(body))
                Town.applyLanguage()
                NotifyInbox.connect()
            } catch (_: Throwable) {
            }
        }
        Town.meKnown = true
        refresh(router)
        recordAppVisit()
    }
}

private fun listenForTabResume() {
    val resume = {
        NotifyInbox.connect()
        resumeFeedOnVisible()
    }
    document.asDynamic().addEventListener("visibilitychange", {
        if (document.asDynamic().visibilityState == "visible") resume()
    })
    window.asDynamic().addEventListener("pageshow", { event: dynamic ->
        if (event.persisted == true) resume()
    })
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
