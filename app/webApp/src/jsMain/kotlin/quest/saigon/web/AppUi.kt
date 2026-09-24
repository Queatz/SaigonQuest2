package quest.saigon.web

import quest.saigon.AppRoute
import quest.saigon.Greeting
import web.events.EventHandler
import web.html.HTMLElement

private val greeting = Greeting()

internal fun renderApp(root: HTMLElement, router: HistoryRouter, route: AppRoute) {
    root.clear()

    val nav = el("nav", className = "nav")
    nav.appendChild(routeLink(router, "/", "Home", route is AppRoute.Home))
    nav.appendChild(routeLink(router, "/about", "About", route is AppRoute.About))
    root.appendChild(nav)

    when (route) {
        AppRoute.Home -> root.appendChild(homePage())
        AppRoute.About -> root.appendChild(aboutPage())
        is AppRoute.NotFound -> root.appendChild(notFoundPage(router, route.path))
    }
}

private fun homePage(): HTMLElement {
    val page = el("main", className = "page")
    val container = el("div", className = "greeting-container")
    val button = el("button", className = "greeting-button", text = "Click me!")
    val contentHost = el("div")

    var visible = false
    var animating = false

    button.onClick {
        if (visible) {
            animating = true
            val content = contentHost.firstChild as? HTMLElement
            content?.setAttribute("class", "greeting-content fade-out")
        } else {
            visible = true
            contentHost.clear()
            contentHost.appendChild(greetingContent())
        }
    }

    contentHost.onanimationend = EventHandler {
        if (animating) {
            contentHost.clear()
            visible = false
            animating = false
        }
    }

    container.appendChild(button)
    container.appendChild(contentHost)
    page.appendChild(container)
    return page
}

private fun greetingContent(): HTMLElement {
    val content = el("div", className = "greeting-content")
    content.appendChild(el("div", className = "js-logo"))
    content.appendChild(el("div", text = "Kotlin/JS: ${greeting.greet()}"))
    return content
}

private fun aboutPage(): HTMLElement {
    val page = el("main", className = "page")
    page.appendChild(el("h1", text = "SaigonQuest2"))
    page.appendChild(
        el(
            "p",
            text = "This web app is a Kotlin/JS single-page application. Routes use the HTML5 History API, so URLs like /about can be shared and the browser back button works.",
        ),
    )
    return page
}

private fun notFoundPage(router: HistoryRouter, path: String): HTMLElement {
    val page = el("main", className = "page")
    page.appendChild(el("h1", text = "Not found"))
    page.appendChild(el("p", text = "No page for $path"))
    page.appendChild(
        el("button", className = "greeting-button", text = "Go home") { button ->
            button.onClick { router.navigate("/") }
        },
    )
    return page
}
