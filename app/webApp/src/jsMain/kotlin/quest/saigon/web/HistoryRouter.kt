@file:OptIn(ExperimentalWasmJsInterop::class)

package quest.saigon.web

import quest.saigon.AppRoute
import quest.saigon.matchAppRoute
import web.events.EventHandler
import web.history.history
import web.location.location
import web.window.window
import kotlin.js.ExperimentalWasmJsInterop

class HistoryRouter(
    private val onRoute: (AppRoute) -> Unit,
) {
    fun start() {
        window.onpopstate = EventHandler {
            emit()
        }
        emit()
    }

    fun navigate(path: String, replace: Boolean = false) {
        if (replace) {
            history.replaceState(null, "", path)
        } else {
            history.pushState(null, "", path)
        }
        emit()
    }

    fun currentPath(): String = location.pathname

    private fun emit() {
        onRoute(matchAppRoute(currentPath()))
    }
}
