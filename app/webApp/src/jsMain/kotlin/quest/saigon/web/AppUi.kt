package quest.saigon.web

import quest.saigon.AppRoute
import web.html.HTMLElement

internal fun renderApp(root: HTMLElement, router: HistoryRouter, route: AppRoute) {
    clearFeedKeys()
    root.clear()
    Town.applyLanguage()
    val loggedOutGate = Town.me == null && (route == AppRoute.NewPost || route == AppRoute.Profile)
    val page = when {
        loggedOutGate || route == AppRoute.Home -> feedScreen(router)
        route == AppRoute.NewPost -> composerScreen(router)
        route == AppRoute.Profile -> profileScreen(router)
        else -> missingScreen(router)
    }
    root.appendChild(page)
    if (loggedOutGate) {
        val next = if (route == AppRoute.Profile) "/profile" else "/new"
        showAuthDialog(page, onSuccess = { router.navigate(next) }, onClose = { router.navigate("/", replace = true) })
    }
}

internal fun pageHead(router: HistoryRouter, title: String, showProfile: Boolean): HTMLElement {
    val head = el("header", "page-head")
    head.appendChild(el("button", "icon-btn", text = "‹") { button ->
        button.setAttribute("type", "button")
        button.setAttribute("aria-label", Town.t("back"))
        button.onClick { back(router) }
    })
    head.appendChild(el("h1", text = title))
    if (showProfile) head.appendChild(viewerButton(router, toComposer = false))
    return head
}

internal fun viewerButton(router: HistoryRouter, toComposer: Boolean): HTMLElement {
    val me = Town.me
    val button = el("button", if (toComposer && me == null) "profile-btn sparkle" else "profile-btn")
    button.setAttribute("type", "button")
    button.setAttribute("aria-label", Town.t(if (toComposer) "createPost" else "openProfile"))
    if (me == null) {
        button.appendChild(el("span", "plus-mark", text = "+"))
        if (toComposer) button.appendChild(sparkles())
    } else {
        button.appendChild(profileFace(me, large = false))
        if (toComposer) button.appendChild(el("span", "plus-badge", text = "+"))
    }
    button.onClick {
        if (toComposer && Town.me == null) {
            val shell = button.asDynamic().closest(".shell")
            val host = if (shell == null || shell == undefined) button else shell as HTMLElement
            showAuthDialog(host, onSuccess = { go(router, "/new") })
        } else {
            go(router, if (toComposer) "/new" else "/profile")
        }
    }
    return button
}

private fun sparkles(): HTMLElement {
    val layer = el("span", "sparkles")
    layer.setAttribute("aria-hidden", "true")
    listOf("s1", "s2", "s3", "s4", "s5", "s6").forEach { name ->
        layer.appendChild(el("i", "spark $name"))
    }
    return layer
}

private fun missingScreen(router: HistoryRouter): HTMLElement {
    val shell = el("section", "shell page-shell")
    val body = el("div", "page-body empty")
    body.appendChild(el("h2", text = Town.t("notFound")))
    body.appendChild(el("button", "primary", text = Town.t("goHome")) { button ->
        button.setAttribute("type", "button")
        button.onClick { router.navigate("/") }
    })
    shell.appendChild(body)
    return shell
}
