package quest.saigon.web

import quest.saigon.AppRoute
import web.html.HTMLElement

internal fun renderApp(root: HTMLElement, router: HistoryRouter, route: AppRoute) {
    clearFeedKeys()
    stopMusicPreview()
    clearStatsPoll()
    root.clear()
    Town.applyLanguage()
    if (route == AppRoute.Admin && Town.signedOut) {
        router.navigate("/", replace = true)
        return
    }
    val loggedOutGate = Town.signedOut && (
        route == AppRoute.NewPost || route == AppRoute.Profile || route == AppRoute.MyPosts
        )
    val page = when {
        loggedOutGate || route == AppRoute.Home || route is AppRoute.Post ->
            feedScreen(router, (route as? AppRoute.Post)?.id)
        route == AppRoute.NewPost -> composerScreen(router)
        route == AppRoute.Profile -> profileScreen(router)
        route == AppRoute.MyPosts -> myPostsScreen(router)
        route == AppRoute.Stats -> statsScreen(router)
        route == AppRoute.Admin -> adminScreen(router)
        else -> missingScreen(router)
    }
    root.appendChild(page)
    if (loggedOutGate) {
        val next = when (route) {
            AppRoute.Profile -> "/profile"
            AppRoute.MyPosts -> "/profile/posts"
            else -> "/new"
        }
        showAuthDialog(page, onSuccess = { router.navigate(next) }, onClose = { router.navigate("/", replace = true) })
    } else if (route == AppRoute.Home && Town.signedOut) {
        showVisitorWelcome(page)
    }
}

internal fun pageHead(router: HistoryRouter, title: String, showProfile: Boolean): HTMLElement {
    val head = el("header", if (showProfile) "page-head has-profile" else "page-head")
    head.appendChild(el("button", "icon-btn", text = "‹") { button ->
        button.setAttribute("type", "button")
        button.setAttribute("aria-label", Town.t("back"))
        button.onClick { back(router) }
    })
    head.appendChild(el("h1", text = title))
    if (showProfile) head.appendChild(viewerButton(router, toComposer = false, editBadge = true))
    return head
}

internal fun viewerButton(router: HistoryRouter, toComposer: Boolean, editBadge: Boolean = false): HTMLElement {
    val me = Town.me
    val button = el("button", if (toComposer && Town.signedOut) "profile-btn sparkle" else "profile-btn")
    button.setAttribute("type", "button")
    button.setAttribute("aria-label", Town.t(if (toComposer) "createPost" else "openProfile"))
    if (me == null) {
        button.appendChild(el("span", "plus-mark", text = "+"))
        if (toComposer && Town.signedOut) {
            button.appendChild(sparkles())
            button.appendChild(firstPostBubble())
        }
    } else {
        button.appendChild(profileFace(me, large = false))
        if (toComposer) button.appendChild(el("span", "plus-badge", text = "+"))
        if (editBadge) button.appendChild(editBadge())
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

private fun editBadge(): HTMLElement {
    val badge = el("span", "edit-badge")
    badge.setAttribute("aria-hidden", "true")
    badge.asDynamic().innerHTML = """<svg viewBox="0 0 24 24" width="11" height="11"><path fill="currentColor" d="M3 17.2V21h3.8l11-11-3.8-3.8-11 11zm17.7-10.4a1 1 0 0 0 0-1.4l-2.1-2.1a1 1 0 0 0-1.4 0l-1.6 1.6 3.5 3.5 1.6-1.6z"/></svg>"""
    return badge
}

private fun sparkles(): HTMLElement {
    val layer = el("span", "sparkles")
    layer.setAttribute("aria-hidden", "true")
    listOf("s1", "s2", "s3", "s4", "s5", "s6").forEach { name ->
        layer.appendChild(el("i", "spark $name"))
    }
    return layer
}

private fun firstPostBubble(): HTMLElement {
    val bubble = el("span", "first-post-bubble", text = Town.t("createFirstPost"))
    bubble.setAttribute("aria-hidden", "true")
    return bubble
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
