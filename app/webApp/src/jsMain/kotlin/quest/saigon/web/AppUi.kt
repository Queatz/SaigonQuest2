package quest.saigon.web

import quest.saigon.AppRoute
import web.html.HTMLElement

internal fun renderApp(root: HTMLElement, router: HistoryRouter, route: AppRoute) {
    val isMessagesRoute = route == AppRoute.Messages || route is AppRoute.Chat
    if (isMessagesRoute && !Town.signedOut && root.asDynamic().querySelector(".messages-shell") != null) {
        Town.applyLanguage()
        selectMessagesChat((route as? AppRoute.Chat)?.id)
        return
    }
    clearFeedKeys()
    clearChatScreen()
    stopMusicPreview()
    clearStatsPoll()
    root.clear()
    Town.applyLanguage()
    if (route == AppRoute.Admin && Town.signedOut) {
        router.navigate("/", replace = true)
        return
    }
    val loggedOutGate = Town.signedOut && (
        route == AppRoute.NewPost || route == AppRoute.Profile || route == AppRoute.MyPosts ||
            route == AppRoute.MyCommunities || route == AppRoute.MyMusic ||
            route == AppRoute.MyProducts || route == AppRoute.MyActivities || route == AppRoute.MyOrders ||
            route == AppRoute.FeatureSuggestions ||
            route == AppRoute.Messages || route is AppRoute.Chat
        )
    val page = when {
        loggedOutGate || route == AppRoute.Home || route is AppRoute.Post || route is AppRoute.User ||
            route is AppRoute.Product || route is AppRoute.Activity || route is AppRoute.Location ->
            feedScreen(router, (route as? AppRoute.Post)?.id)
        route == AppRoute.Wants -> feedScreen(router, startInWants = true)
        route == AppRoute.Privacy -> privacyScreen(router)
        route == AppRoute.Csae -> csaeScreen(router)
        route == AppRoute.NewPost -> composerScreen(router)
        route == AppRoute.Profile -> profileScreen(router)
        route == AppRoute.MyPosts -> myPostsScreen(router)
        route == AppRoute.MyCommunities -> myCommunitiesScreen(router)
        route == AppRoute.MyMusic -> myMusicScreen(router)
        route == AppRoute.MyProducts -> myProductsScreen(router)
        route == AppRoute.MyActivities -> myActivitiesScreen(router)
        route == AppRoute.MyOrders -> myOrdersScreen(router)
        route == AppRoute.FeatureSuggestions -> featureSuggestionsScreen(router)
        route == AppRoute.Stats -> statsScreen(router)
        route == AppRoute.Admin -> adminScreen(router)
        route == AppRoute.Discover -> eventsScreen(router)
        route == AppRoute.Shop -> eventsScreen(router)
        route == AppRoute.Messages -> messagesScreen(router)
        route is AppRoute.Chat -> messagesScreen(router, route.id)
        route is AppRoute.Community -> feedScreen(router, communityId = route.id)
        else -> missingScreen(router)
    }
    root.appendChild(page)
    if (loggedOutGate) {
        val next = when (route) {
            AppRoute.Profile -> "/profile"
            AppRoute.MyPosts -> "/profile/posts"
            AppRoute.MyCommunities -> "/profile/communities"
            AppRoute.MyMusic -> "/profile/music"
            AppRoute.MyProducts -> "/profile/products"
            AppRoute.MyActivities -> "/profile/activities"
            AppRoute.MyOrders -> "/profile/orders"
            AppRoute.FeatureSuggestions -> "/profile/features"
            AppRoute.Messages -> "/messages"
            is AppRoute.Chat -> "/messages/${route.id}"
            else -> "/new"
        }
        showAuthDialog(page, onSuccess = { router.navigate(next) }, onClose = { router.navigate("/", replace = true) })
    }
    when (route) {
        is AppRoute.User -> http("GET", "/api/users/${encodeQuery(route.id)}") { status, text ->
            if (status == 200 && page.asDynamic().isConnected != false) {
                val user = parseJson(text)
                openMiniProfile(
                    page,
                    user.name as? String ?: "",
                    (user.photoUrl as? String)?.let(::prefixServerOrigin),
                    user.location as? String ?: "",
                    user.intro as? String ?: "",
                    user.id as? String ?: route.id,
                    router,
                )
            } else if (status != 200 && page.asDynamic().isConnected != false) {
                toast(page, Town.t("unavailable"))
            }
        }
        is AppRoute.Product -> http("GET", "/api/products/${encodeQuery(route.id)}") { status, text ->
            if (status == 200 && page.asDynamic().isConnected != false) {
                openProductDetails(page, router, parseJson(text))
            } else if (status != 200 && page.asDynamic().isConnected != false) {
                toast(page, Town.t("unavailable"))
            }
        }
        is AppRoute.Activity -> {
            if (page.asDynamic().isConnected != false) openActivitySheet(page, router, route.id)
        }
        is AppRoute.Location -> http("GET", "/api/places/${encodeQuery(route.id)}") { status, text ->
            if (status == 200 && page.asDynamic().isConnected != false) {
                val place = parseJson(text)
                val lat = if (place.lat == null || place.lat == undefined) null else num(place.lat)
                val lng = if (place.lng == null || place.lng == undefined) null else num(place.lng)
                val placeId = (place.placeId as? String)?.takeIf { it.isNotBlank() }
                val customLocationId = (place.id as? String)?.takeIf { it.isNotBlank() }
                openLocationSheet(
                    page,
                    router,
                    place.name as? String ?: "",
                    placeId,
                    lat,
                    lng,
                    customLocationId = customLocationId,
                )
            } else if (status != 200 && page.asDynamic().isConnected != false) {
                toast(page, Town.t("unavailable"))
            }
        }
        else -> Unit
    }
}

internal fun pageHead(
    router: HistoryRouter,
    title: String,
    showProfile: Boolean,
    onBack: (() -> Unit)? = null,
    searchAction: (() -> Unit)? = null,
): HTMLElement {
    val head = el("header", if (showProfile) "page-head has-profile" else "page-head")
    head.appendChild(el("button", "icon-btn", text = "‹") { button ->
        button.setAttribute("type", "button")
        button.setAttribute("aria-label", Town.t("back"))
        button.onClick { if (onBack == null) back(router) else onBack() }
    })
    head.appendChild(el("h1", text = title))
    if (searchAction != null) {
        val search = el("button", "icon-btn post-search-toggle")
        search.setAttribute("type", "button")
        search.setAttribute("aria-label", Town.t("search"))
        search.asDynamic().innerHTML = """<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.8" cy="10.8" r="6.8" fill="none" stroke="currentColor" stroke-width="2"/><path d="m16 16 5 5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>"""
        search.onClick { searchAction() }
        head.appendChild(search)
    }
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
