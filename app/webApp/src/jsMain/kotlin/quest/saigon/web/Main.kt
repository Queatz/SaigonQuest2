package quest.saigon.web

import quest.saigon.shared.js.JsSharedHolder
import quest.saigon.shared.js.launchAction
import quest.saigon.shared.js.observeState
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

    var initialRefreshed = false
    observeState(JsSharedHolder.scope, JsSharedHolder.client.session.state) { sessionState ->
        val wasSignedIn = Town.me != null
        Town.syncFromShared(sessionState)
        if (sessionState.isSignedIn && !wasSignedIn) {
            NotifyInbox.connect()
        } else if (!sessionState.isSignedIn && wasSignedIn) {
            NotifyInbox.disconnect()
        }
        if (sessionState.meKnown && !initialRefreshed) {
            initialRefreshed = true
            refresh(router)
            recordAppVisit()
        }
    }
    launchAction(JsSharedHolder.scope) {
        JsSharedHolder.client.bootstrap()
    }
}

private fun listenForTabResume() {
    val resume = {
        JsSharedHolder.client.onForeground()
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
