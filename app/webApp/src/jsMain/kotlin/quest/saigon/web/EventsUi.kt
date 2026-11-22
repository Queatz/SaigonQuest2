package quest.saigon.web

import quest.saigon.Limits
import quest.saigon.PlaceEmoji
import web.dom.document
import web.html.HTMLElement
import web.window.WindowTarget
import web.window._blank
import web.window.window

private const val SAIGON_OFFSET = 7 * 60 * 60 * 1000L
internal const val EVENT_ICON = "📅"
internal const val PLACE_ICON = "📍"
internal const val PRICE_ICON = "💰"
private const val COPY_ICON =
    """<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="8" y="7" width="12" height="14" rx="2" fill="none" stroke="currentColor" stroke-width="2"/><path d="M6 17H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v1" fill="none" stroke="currentColor" stroke-width="2"/></svg>"""
private const val CHAT_ICON =
    """<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 5h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H9l-4 4v-4H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg>"""
private var mapsApi: dynamic = null
private var mapsLoadStarted = false
private var mapsImporting = false
private val mapsWaiters = mutableListOf<(Boolean) -> Unit>()

internal class PlacePick(
    var name: String = "",
    var placeId: String? = null,
    var lat: Double? = null,
    var lng: Double? = null,
    var id: String? = null,
    var postCount: Long = 0,
    var photoUrl: String? = null,
    var hasActivities: Boolean = false,
    var activityCount: Long = 0,
    var activityEmojis: List<String> = emptyList(),
)

internal fun bindLocationMapPicker(
    host: HTMLElement,
    input: HTMLElement,
    mapButton: HTMLElement,
    current: PlacePick,
    onChange: () -> Unit = {},
    communityId: () -> String? = { null },
) {
    fun refreshButton() {
        mapButton.asDynamic().classList.toggle(
            "has-place",
            !current.placeId.isNullOrBlank() || (current.lat != null && current.lng != null),
        )
    }

    refreshButton()
    mapButton.onClick {
        openLocationPicker(host, communityId(), current) { picked ->
            current.name = picked.name
            current.placeId = picked.placeId
            current.lat = picked.lat
            current.lng = picked.lng
            current.id = picked.id
            input.asDynamic().value = picked.name
            refreshButton()
            onChange()
        }
    }
    input.asDynamic().oninput = {
        current.placeId = null
        current.lat = null
        current.lng = null
        current.id = null
        current.name = (input.asDynamic().value as? String).orEmpty().trim()
        refreshButton()
        onChange()
    }
}

internal fun eventsScreen(router: HistoryRouter): HTMLElement {
    val shell = el("section", "shell page-shell events-shell")
    var searching = false
    var openSearch: () -> Unit = {}
    var closeSearch: () -> Unit = {}
    val body = el("div", "page-body events-body")
    val checkInsRow = el("div", "check-in-events")
    var checkInRefreshGeneration = 0
    val checkInExpiryTimers = mutableListOf<dynamic>()
    fun refreshCheckIns() {
        val generation = ++checkInRefreshGeneration
        checkInExpiryTimers.forEach { window.asDynamic().clearTimeout(it) }
        checkInExpiryTimers.clear()
        checkInsRow.textContent = ""
        http("GET", "/api/checkins") { status, text ->
            if (generation != checkInRefreshGeneration || status != 200) return@http
            val items: dynamic = parseJson(text).checkIns
            val count = if (items == null || items == undefined) 0 else num(items.length).toInt()
            for (index in 0 until count) {
                val item: dynamic = js("items[index]")
                val checkIn = checkInCardFrom(item) ?: continue
                if (checkIn.untilAt <= num(js("Date.now()")).toLong()) continue
                val bubble = createCheckInBubble(
                    shell,
                    router,
                    checkIn,
                    onChanged = ::refreshCheckIns,
                    cssClass = "check-in-bubble check-in-event-bubble",
                )
                bubble.asDynamic().style.setProperty("--check-in-row-offset", "${index % 3 * 64}px")
                checkInsRow.appendChild(bubble)
                val remaining = checkIn.untilAt - num(js("Date.now()")).toLong()
                checkInExpiryTimers.add(window.asDynamic().setTimeout({ bubble.asDynamic().remove() }, remaining))
            }
        }
    }
    val head = pageHead(
        router,
        Town.t("discover"),
        showProfile = false,
        onBack = { if (searching) closeSearch() else back(router) },
        searchAction = { openSearch() },
    )
    val title = head.asDynamic().querySelector("h1") as HTMLElement
    val searchButton = head.asDynamic().querySelector(".post-search-toggle") as HTMLElement
    shell.appendChild(head)
    val mapBox = el("div", "events-map")
    val lists = el("div", "events-lists")
    val tabs = el("div", "chip-row events-tabs")
    val eventsChip = el("button", "chip selected", text = Town.t("events"))
    eventsChip.setAttribute("type", "button")
    val activitiesChip = el("button", "chip", text = Town.t("activities"))
    activitiesChip.setAttribute("type", "button")
    val learnChip = el("button", "chip", text = Town.t("products"))
    learnChip.setAttribute("type", "button")
    val peopleChip = el("button", "chip", text = Town.t("people"))
    peopleChip.setAttribute("type", "button")
    val communitiesChip = el("button", "chip", text = Town.t("communities"))
    communitiesChip.setAttribute("type", "button")
    tabs.appendChild(eventsChip)
    tabs.appendChild(activitiesChip)
    tabs.appendChild(learnChip)
    tabs.appendChild(peopleChip)
    tabs.appendChild(communitiesChip)
    val split = el("div", "events-split")
    val mapPane = el("div", "events-map-pane")
    val listPane = el("div", "events-list-pane")
    val mapLocations = mutableListOf<PlacePick>()
    val sessionLocations = mutableListOf<PlacePick>()
    val pendingMapLocations = mutableListOf<PlacePick>()
    var eventsMapReady = false
    var navigateEventsMap: (PlacePick) -> Unit = {}
    var addEventsMapLocation: (PlacePick) -> Unit = {}
    var setEventsMapType: (Boolean) -> Unit = {}
    var locateEventsMap: () -> Unit = {}
    var satelliteView = false
    var pendingMapNavigation: PlacePick? = null
    fun onMapLocationCreated(place: PlacePick) {
        if (sessionLocations.none { samePlace(it, place) }) sessionLocations += place
        if (eventsMapReady) {
            addEventsMapLocation(place)
        } else if (pendingMapLocations.none { samePlace(it, place) }) {
            pendingMapLocations += place
        }
    }
    eventsMapPlaceSearch(
        mapPane,
        shell,
        router,
        findOnMap = { place ->
            mapLocations.firstOrNull { samePlace(it, place) }
                ?: sessionLocations.firstOrNull { samePlace(it, place) }
        },
        navigate = { place ->
            if (eventsMapReady) navigateEventsMap(place) else pendingMapNavigation = place
        },
        onCreated = ::onMapLocationCreated,
    )
    val mapTools = el("div", "events-map-tools")
    val satelliteButton = el("button", "icon-btn events-map-tool")
    satelliteButton.setAttribute("type", "button")
    satelliteButton.setAttribute("aria-label", Town.t("satelliteView"))
    satelliteButton.asDynamic().innerHTML =
        """<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 5.5 9 3l6 2.5L20 3v15.5L15 21l-6-2.5L4 21z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="M9 3v15.5M15 5.5V21" fill="none" stroke="currentColor" stroke-width="1.8"/></svg>"""
    satelliteButton.onClick {
        satelliteView = !satelliteView
        satelliteButton.setAttribute("aria-label", Town.t(if (satelliteView) "standardMap" else "satelliteView"))
        setEventsMapType(satelliteView)
    }
    val locateButton = el("button", "icon-btn events-map-tool")
    locateButton.setAttribute("type", "button")
    locateButton.setAttribute("aria-label", Town.t("locateMe"))
    locateButton.asDynamic().innerHTML =
        """<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="7" fill="none" stroke="currentColor" stroke-width="1.8"/><circle cx="12" cy="12" r="2" fill="currentColor"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>"""
    locateButton.onClick { locateEventsMap() }
    mapTools.appendChild(satelliteButton)
    mapTools.appendChild(locateButton)
    mapPane.appendChild(mapTools)
    val eventCol = el("div", "events-col")
    val activityCol = el("div", "events-col")
    val learnCol = el("div", "events-col")
    val peopleCol = el("div", "events-col")
    val communityCol = el("div", "events-col")
    eventCol.appendChild(el("h3", text = Town.t("events")))
    activityCol.appendChild(el("h3", text = Town.t("activities")))
    learnCol.appendChild(el("h3", text = Town.t("products")))
    peopleCol.appendChild(el("h3", text = Town.t("people")))
    communityCol.appendChild(el("h3", text = Town.t("communities")))
    val eventList = el("div", "event-list")
    val activityList = el("div", "activity-list")
    learnCol.appendChild(learnCatalog(shell, router))
    val peopleList = el("div", "member-list")
    val communityList = el("div", "community-list")
    eventCol.appendChild(eventList)
    activityCol.appendChild(activityList)
    peopleCol.appendChild(peopleList)
    communityCol.appendChild(communityList)
    split.appendChild(eventCol)
    split.appendChild(activityCol)
    split.appendChild(learnCol)
    split.appendChild(peopleCol)
    split.appendChild(communityCol)
    lists.appendChild(tabs)
    lists.appendChild(split)
    mapPane.appendChild(mapBox)
    listPane.appendChild(lists)
    body.appendChild(checkInsRow)
    body.appendChild(mapPane)
    body.appendChild(listPane)
    shell.appendChild(body)
    refreshCheckIns()
    var searchView: HTMLElement? = null
    var feedOverlay: HTMLElement? = null
    closeSearch = {
        if (searching) {
            searching = false
            searchView?.asDynamic()?.remove()
            searchView = null
            body.asDynamic().style.display = ""
            title.textContent = Town.t("discover")
            searchButton.asDynamic().hidden = false
        }
    }
    openSearch = {
        if (!searching) {
            searching = true
            body.asDynamic().style.display = "none"
            title.textContent = Town.t("searchPosts")
            searchButton.asDynamic().hidden = true
            val screen = postSearchScreen(router) { post, posts, query, cursor ->
                if (feedOverlay == null) {
                    lateinit var overlay: HTMLElement
                    overlay = feedScreen(
                        router,
                        focusPostId = post.id,
                        onBack = {
                            clearFeedKeys()
                            overlay.asDynamic().remove()
                            feedOverlay = null
                        },
                        seedPosts = posts,
                        seedCursor = cursor,
                        searchQuery = query,
                    )
                    feedOverlay = overlay
                    shell.appendChild(overlay)
                }
            }
            searchView = screen
            shell.appendChild(screen)
            val searchInput = screen.asDynamic().querySelector(".post-search-input")
            if (searchInput != null && searchInput != undefined) searchInput.focus()
        }
    }
    fun selectTab(chip: HTMLElement) {
        eventsChip.asDynamic().classList.toggle("selected", chip === eventsChip)
        activitiesChip.asDynamic().classList.toggle("selected", chip === activitiesChip)
        learnChip.asDynamic().classList.toggle("selected", chip === learnChip)
        peopleChip.asDynamic().classList.toggle("selected", chip === peopleChip)
        communitiesChip.asDynamic().classList.toggle("selected", chip === communitiesChip)
        split.asDynamic().classList.toggle("show-activities", chip === activitiesChip)
        split.asDynamic().classList.toggle("show-learn", chip === learnChip)
        split.asDynamic().classList.toggle("show-people", chip === peopleChip)
        split.asDynamic().classList.toggle("show-communities", chip === communitiesChip)
    }
    eventsChip.onClick { selectTab(eventsChip) }
    activitiesChip.onClick { selectTab(activitiesChip) }
    learnChip.onClick { selectTab(learnChip) }
    peopleChip.onClick { selectTab(peopleChip) }
    communitiesChip.onClick { selectTab(communitiesChip) }
    loadGoogleMaps { }

    http("GET", "/api/events") { status, text ->
        if (status != 200) {
            eventList.appendChild(el("p", "hint", text = Town.t("unavailable")))
            return@http
        }
        val parsed = parseJson(text)
        val events = readPosts(parsed.events)
        if (events.isEmpty()) {
            eventList.appendChild(el("p", "hint", text = Town.t("noEvents")))
            selectTab(activitiesChip)
        } else {
            renderGroupedEvents(eventList, events, router)
        }
        val people = parsed.people
        val length = people?.length as? Int ?: 0
        if (length == 0) {
            peopleList.appendChild(el("p", "hint", text = Town.t("noMembers")))
        } else {
            for (i in 0 until length) {
                peopleList.appendChild(peopleRow(people[i], shell, router))
            }
        }
        val locations = readUserLocations(parsed.locations).toMutableList()
        sessionLocations.forEach { place ->
            if (locations.none { samePlace(it, place) }) locations += place
        }
        mapLocations.clear()
        mapLocations.addAll(locations)
        mountEventsMap(
            mapBox,
            events,
            locations,
            shell,
            router,
            onMapTap = { lat, lng -> openMapAddLocationDialog(shell, router, lat, lng, ::onMapLocationCreated) },
        ) { navigate, addLocation, setMapType, locate ->
            eventsMapReady = true
            navigateEventsMap = navigate
            addEventsMapLocation = addLocation
            setEventsMapType = setMapType
            locateEventsMap = locate
            setMapType(satelliteView)
            pendingMapLocations.forEach { place ->
                if (locations.none { samePlace(it, place) }) addLocation(place)
            }
            pendingMapLocations.clear()
            pendingMapNavigation?.let(navigate)
            pendingMapNavigation = null
        }
    }
    fun loadCommunities() {
        http("GET", "/api/communities?limit=50") { status, body ->
            communityList.clear()
            if (status != 200) {
                communityList.appendChild(el("p", "hint", text = Town.t("unavailable")))
                return@http
            }
            val items = readCommunities(parseJson(body).communities)
            if (items.isEmpty()) {
                communityList.appendChild(el("p", "hint", text = Town.t("noCommunities")))
                return@http
            }
            items.forEach { community ->
                communityList.appendChild(
                    communityActionRow(shell, router, community, leaving = community.joined) { loadCommunities() },
                )
            }
        }
    }
    loadCommunities()
    loadDiscoverActivities(activityList, shell, router)
    return shell
}

internal fun eventsSummaryButton(router: HistoryRouter): HTMLElement {
    val button = el("button", "pill-btn events-summary")
    button.setAttribute("type", "button")
    button.setAttribute("aria-label", Town.t("openEvents"))
    button.setAttribute("hidden", "hidden")
    button.onClick { router.navigate("/discover") }
    http("GET", "/api/events/summary") { status, body ->
        val eventsLabel = "📅 " + if (status == 200) {
            val parsed = parseJson(body)
            val today = num(parsed.today).toInt()
            val tomorrow = num(parsed.tomorrow).toInt()
            val week = num(parsed.week).toInt()
            when {
                today == 1 -> Town.t("eventToday")
                today > 1 -> Town.t("eventsToday", mapOf("count" to today.toString()))
                tomorrow == 1 -> Town.t("eventTomorrow")
                tomorrow > 1 -> Town.t("eventsTomorrow", mapOf("count" to tomorrow.toString()))
                week == 1 -> Town.t("eventWeek")
                week > 1 -> Town.t("eventsWeek", mapOf("count" to week.toString()))
                else -> Town.t("noUpcomingEvents")
            }
        } else {
            Town.t("noUpcomingEvents")
        }
        val messages = listOf(
            eventsLabel,
            "🛍️ ${Town.t("searchForProducts")}",
            "🫂 ${Town.t("joinACommunity")}",
            "🙆 ${Town.t("discoverNewPeople")}",
            "♥️ ${Town.t("findThingsToDo")}",
        )
        var index = 0
        fun show() {
            if (button.asDynamic().isConnected == false) return
            button.textContent = messages[index]
            index = (index + 1) % messages.size
        }
        show()
        button.removeAttribute("hidden")
        window.asDynamic().setInterval({
            if (button.asDynamic().isConnected == false) return@setInterval
            show()
        }, 3000)
    }
    return button
}

internal fun eventCard(
    post: PostCard,
    host: HTMLElement,
    router: HistoryRouter,
    onUpdate: (PostCard) -> Unit,
): HTMLElement {
    val card = el("div", "event-card")
    fun paint(current: PostCard) {
        card.clear()
        val start = current.eventStartAt ?: return
        val details = el("div", "event-details")
        val whenRow = el("div", "event-when")
        whenRow.appendChild(el("span", "event-icon", text = EVENT_ICON))
        val whenCopy = el("div", "event-when-copy")
        whenCopy.appendChild(el("strong", text = eventScheduleLabel(start, current.eventEndAt)))
        val relative = eventRelativeLabel(start, current.eventEndAt)
        if (relative != null) whenCopy.appendChild(el("small", text = relative))
        whenRow.appendChild(whenCopy)
        details.appendChild(whenRow)
        if (current.location.isNotBlank()) {
            val place = el("button", "event-place")
            place.setAttribute("type", "button")
            place.appendChild(el("span", "event-icon", text = PLACE_ICON))
            place.appendChild(el("span", text = current.location))
            place.onClick {
                openLocationSheet(
                    host,
                    router,
                    current.location,
                    current.placeId,
                    current.lat,
                    current.lng,
                    current.locationId,
                )
            }
            details.appendChild(place)
        }
        val price = current.eventPrice
        val priceRow = el("span", "event-price")
        priceRow.appendChild(el("span", "event-icon", text = PRICE_ICON))
        priceRow.appendChild(el("span", text = if (price != null && price > 0) formatVnd(price) else Town.t("free")))
        details.appendChild(priceRow)
        val faces = el("button", "event-faces")
        faces.setAttribute("type", "button")
        fillAttendeeFaces(faces, current)
        faces.onClick { openAttendees(current, host, router) }
        details.appendChild(faces)
        card.appendChild(details)
        val join = el(
            "button",
            if (current.joined) "event-join is-leave" else "event-join",
            text = Town.t(if (current.joined) "leaveEvent" else "joinEvent"),
        )
        join.setAttribute("type", "button")
        join.onClick {
            if (Town.me == null) {
                showAuthDialog(host, onSuccess = { join.asDynamic().click() })
                return@onClick
            }
            join.setAttribute("disabled", "disabled")
            val action = if (current.joined) "leave" else "join"
            http("POST", "/api/posts/${current.id}/$action") { status, body ->
                join.removeAttribute("disabled")
                if (status != 200) {
                    toast(host, errorText(body, status))
                    return@http
                }
                val next = postFrom(parseJson(body))
                onUpdate(next)
                paint(next)
            }
        }
        card.appendChild(join)
    }
    paint(post)
    return card
}

internal fun openLocationPicker(
    host: HTMLElement,
    communityId: String?,
    current: PlacePick,
    onPick: (PlacePick) -> Unit,
) {
    if (host.asDynamic().querySelector(".location-picker") != null) return
    val backdrop = el("div", "dialog-backdrop location-picker")
    val card = el("div", "dialog location-dialog")
    card.setAttribute("role", "dialog")
    card.appendChild(el("h2", text = Town.t("pickLocation")))
    val search = el("input")
    search.setAttribute("type", "search")
    search.setAttribute("placeholder", Town.t("searchPlaces"))
    card.appendChild(search)
    val recentBox = el("div", "place-recent")
    val results = el("div", "place-results")
    val mapBox = el("div", "picker-map")
    val placeHint = el("p", "hint location-picker-hint")
    placeHint.asDynamic().hidden = true
    card.appendChild(recentBox)
    card.appendChild(results)
    card.appendChild(mapBox)
    card.appendChild(placeHint)
    val actions = el("div", "dialog-actions")
    actions.appendChild(el("button", "text-button", text = Town.t("close")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    val confirm = el("button", "primary", text = Town.t("usePlace"))
    confirm.setAttribute("type", "button")
    confirm.asDynamic().disabled = true
    actions.appendChild(confirm)
    card.appendChild(actions)
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
    search.asDynamic().focus()

    var map: dynamic = null
    var marker: dynamic = null
    var pending: PlacePick? = null
    var selectedRow: HTMLElement? = null
    var request = 0
    var previewToken = 0
    var placingLocation = false
    var locationName = ""
    var satelliteView = false
    var currentLocationMarker: dynamic = null
    fun markSelected(row: HTMLElement) {
        selectedRow?.asDynamic()?.classList?.remove("selected")
        row.asDynamic().classList.add("selected")
        selectedRow = row
    }

    fun pan(place: PlacePick) {
        if (map == null || map == undefined) return
        val lat = place.lat ?: return
        val lng = place.lng ?: return
        val pos = js("{}")
        pos.lat = lat
        pos.lng = lng
        map.setCenter(pos)
        map.setZoom(16)
        if (marker != null && marker != undefined) marker.setMap(null)
        marker = googleHtmlMarker(map, pos, placeMapBubble(place.name, place.placeId, place.lat, place.lng))
    }

    fun startPlacing(row: HTMLElement) {
        locationName = (search.asDynamic().value as? String).orEmpty().trim()
        if (locationName.isBlank()) {
            placeHint.textContent = Town.t("enterLocationName")
            placeHint.asDynamic().hidden = false
            search.asDynamic().focus()
            return
        }
        if (Town.me == null) {
            showAuthDialog(host, onSuccess = { startPlacing(row) })
            return
        }
        placingLocation = true
        ++previewToken
        pending = null
        if (marker != null && marker != undefined) marker.setMap(null)
        marker = null
        confirm.asDynamic().disabled = true
        confirm.textContent = Town.t("createLocation")
        placeHint.textContent = Town.t("tapMapToPlace")
        placeHint.asDynamic().hidden = false
        mapBox.asDynamic().classList.add("is-placing")
        markSelected(row)
    }

    loadGoogleMaps { ready ->
        if (!ready || backdrop.asDynamic().isConnected == false) return@loadGoogleMaps
        val opts = js("{}")
        opts.center = js("{lat:10.7769,lng:106.7009}")
        opts.zoom = 12
        opts.disableDefaultUI = true
        opts.zoomControl = true
        map = googleMap(mapBox, opts)
        val mapTools = el("div", "events-map-tools picker-map-tools")
        val satelliteButton = el("button", "icon-btn events-map-tool")
        satelliteButton.setAttribute("type", "button")
        satelliteButton.setAttribute("aria-label", Town.t("satelliteView"))
        satelliteButton.asDynamic().innerHTML =
            """<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 5.5 9 3l6 2.5L20 3v15.5L15 21l-6-2.5L4 21z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="M9 3v15.5M15 5.5V21" fill="none" stroke="currentColor" stroke-width="1.8"/></svg>"""
        satelliteButton.onClick {
            satelliteView = !satelliteView
            satelliteButton.setAttribute("aria-label", Town.t(if (satelliteView) "standardMap" else "satelliteView"))
            map.setMapTypeId(if (satelliteView) "hybrid" else "roadmap")
        }
        val locateButton = el("button", "icon-btn events-map-tool")
        locateButton.setAttribute("type", "button")
        locateButton.setAttribute("aria-label", Town.t("locateMe"))
        locateButton.asDynamic().innerHTML =
            """<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="7" fill="none" stroke="currentColor" stroke-width="1.8"/><circle cx="12" cy="12" r="2" fill="currentColor"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>"""
        locateButton.onClick {
            val geolocation = window.asDynamic().navigator.geolocation
            if (geolocation == null || geolocation == undefined) {
                toast(host, Town.t("locationUnavailable"))
                return@onClick
            }
            val options = js("({})")
            options.enableHighAccuracy = true
            options.timeout = 10000
            options.maximumAge = 30000
            geolocation.getCurrentPosition(
                { result: dynamic ->
                    val position = js("({})")
                    position.lat = num(result.coords.latitude)
                    position.lng = num(result.coords.longitude)
                    map.panTo(position)
                    map.setZoom(16)
                    if (currentLocationMarker != null && currentLocationMarker != undefined) {
                        currentLocationMarker.setMap(null)
                    }
                    currentLocationMarker = googleHtmlMarker(map, position, el("div", "events-current-location-dot"))
                },
                { error: dynamic ->
                    toast(
                        host,
                        Town.t(if (num(error.code).toInt() == 1) "locationPermissionDenied" else "locationUnavailable"),
                    )
                },
                options,
            )
        }
        mapTools.appendChild(satelliteButton)
        mapTools.appendChild(locateButton)
        mapTools.asDynamic().addEventListener("pointerdown", { event: dynamic -> event.stopPropagation() })
        mapTools.asDynamic().addEventListener("click", { event: dynamic -> event.stopPropagation() })
        mapBox.appendChild(mapTools)
        map.addListener("click", { event: dynamic ->
            if (!placingLocation) return@addListener
            val point = event.latLng
            val lat = num(point.lat())
            val lng = num(point.lng())
            val place = PlacePick(locationName, lat = lat, lng = lng)
            pending = place
            confirm.asDynamic().disabled = false
            placeHint.textContent = Town.t("locationPlaced")
            if (marker != null && marker != undefined) marker.setMap(null)
            val pos = js("({})")
            pos.lat = lat
            pos.lng = lng
            marker = googleHtmlMarker(map, pos, placeMapBubble(place.name, null, lat, lng))
        })
        if (current.lat != null && current.lng != null) pan(current)
    }

    fun preview(place: PlacePick, row: HTMLElement) {
        placingLocation = false
        mapBox.asDynamic().classList.remove("is-placing")
        placeHint.asDynamic().hidden = true
        confirm.textContent = Town.t("usePlace")
        markSelected(row)
        confirm.asDynamic().disabled = true
        val token = ++previewToken
        fun ready() {
            if (token != previewToken) return
            if (place.lat != null && place.lng != null) pan(place)
            pending = place
            confirm.asDynamic().disabled = false
        }
        val id = place.placeId
        if (!id.isNullOrBlank() && (place.lat == null || place.lng == null)) {
            http("GET", "/api/places/details?id=${encodeQuery(id)}") { status, body ->
                if (status == 200) {
                    val parsed = parseJson(body)
                    place.name = (parsed.name as? String)?.takeIf { it.isNotBlank() } ?: place.name
                    place.lat = if (parsed.lat == null || parsed.lat == undefined) null else num(parsed.lat)
                    place.lng = if (parsed.lng == null || parsed.lng == undefined) null else num(parsed.lng)
                }
                ready()
            }
        } else {
            ready()
        }
    }

    confirm.onClick {
        val place = pending ?: return@onClick
        if (placingLocation) {
            confirm.asDynamic().disabled = true
            val payload = js("({})")
            payload.name = place.name
            payload.lat = place.lat
            payload.lng = place.lng
            http("POST", "/api/places", jsonStringify(payload)) { status, body ->
                if (status != 201) {
                    confirm.asDynamic().disabled = false
                    placeHint.textContent = errorText(body, status)
                    placeHint.asDynamic().hidden = false
                    return@http
                }
                val created = parseJson(body)
                onPick(
                    PlacePick(
                        name = (created.name as? String).orEmpty(),
                        placeId = (created.placeId as? String)?.takeIf { it.isNotBlank() },
                        lat = if (created.lat == null || created.lat == undefined) null else num(created.lat),
                        lng = if (created.lng == null || created.lng == undefined) null else num(created.lng),
                        id = (created.id as? String)?.takeIf { it.isNotBlank() },
                    ),
                )
                backdrop.asDynamic().remove()
            }
            return@onClick
        }
        onPick(place)
        backdrop.asDynamic().remove()
    }

    fun addLocationButton(): HTMLElement {
        val row = el("button", "chooser-row add-location")
        row.setAttribute("type", "button")
        row.appendChild(el("span", "place-thumb emoji add-location-icon", text = "+"))
        val copy = el("span", "place-row-copy")
        copy.appendChild(el("strong", text = Town.t("addThisLocation")))
        copy.appendChild(el("small", text = Town.t("addLocationHint")))
        row.appendChild(copy)
        row.onClick { startPlacing(row) }
        return row
    }

    fun placeButton(node: dynamic, recent: Boolean): HTMLElement {
        val name = (node.name as? String).orEmpty()
        val address = (node.address as? String).orEmpty()
        val row = el("button", if (recent) "chooser-row recent" else "chooser-row")
        row.setAttribute("type", "button")
        val thumb = el("span", "place-thumb")
        val categoryEmoji = PlaceEmoji.forPrimaryType(node.category as? String) ?: PLACE_ICON
        fun showFallback() {
            thumb.clear()
            thumb.asDynamic().classList.add("emoji")
            thumb.textContent = categoryEmoji
        }
        val imageUrls = listOfNotNull(
            (node.photoUrl as? String)?.takeIf { it.isNotBlank() },
            (node.googlePhotoUrl as? String)?.takeIf { it.isNotBlank() },
        )
        fun showPhoto(index: Int) {
            if (index >= imageUrls.size) {
                showFallback()
                return
            }
            val image = el("img", "place-thumb-photo")
            image.setAttribute("alt", "")
            image.asDynamic().onload = {
                thumb.clear()
                thumb.asDynamic().classList.remove("emoji")
                thumb.appendChild(image)
            }
            image.asDynamic().onerror = { showPhoto(index + 1) }
            image.setAttribute("src", prefixServerOrigin(imageUrls[index]))
        }
        showFallback()
        if (imageUrls.isNotEmpty()) showPhoto(0)
        row.appendChild(thumb)
        val copy = el("span", "place-row-copy")
        copy.appendChild(el("strong", text = name))
        if (address.isNotBlank() && address != name) copy.appendChild(el("small", text = address))
        copy.appendChild(
            el(
                "small",
                text = Town.t("postCount", mapOf("count" to num(node.postCount).toLong().toString())),
            ),
        )
        row.appendChild(copy)
        row.onClick {
            preview(
                PlacePick(
                    name = name,
                    placeId = (node.placeId as? String)?.takeIf { it.isNotBlank() },
                    lat = if (node.lat == null || node.lat == undefined) null else num(node.lat),
                    lng = if (node.lng == null || node.lng == undefined) null else num(node.lng),
                    id = (node.id as? String)?.takeIf { it.isNotBlank() },
                ),
                row,
            )
        }
        return row
    }

    fun load(query: String) {
        val searching = query.isNotBlank()
        if (searching) {
            recentBox.clear()
            recentBox.asDynamic().hidden = true
        }
        val token = ++request
        val q = if (searching) "&q=${encodeQuery(query)}" else ""
        val community = if (communityId.isNullOrBlank()) "" else "&communityId=${encodeQuery(communityId)}"
        http("GET", "/api/places?$q$community") { status, body ->
            if (token != request) return@http
            results.clear()
            if (status != 200) {
                if (query.isNotBlank()) results.appendChild(addLocationButton())
                return@http
            }
            val parsed = parseJson(body)
            recentBox.clear()
            if (searching) {
                recentBox.asDynamic().hidden = true
            } else {
                recentBox.asDynamic().hidden = false
                val recent = parsed.recent
                val recentLen = recent?.length as? Int ?: 0
                if (recentLen > 0) {
                    recentBox.appendChild(el("p", "hint", text = Town.t("recentPlaces")))
                    for (i in 0 until recentLen) recentBox.appendChild(placeButton(recent[i], true))
                }
            }
            val places = parsed.places
            val placeLen = places?.length as? Int ?: 0
            for (i in 0 until placeLen) results.appendChild(placeButton(places[i], false))
            if (query.isNotBlank()) results.appendChild(addLocationButton())
        }
    }

    load("")
    var timer: dynamic = null
    search.asDynamic().oninput = {
        val query = (search.asDynamic().value as? String).orEmpty()
        if (query.isNotBlank()) {
            recentBox.clear()
            recentBox.asDynamic().hidden = true
        }
        if (timer != null) window.asDynamic().clearTimeout(timer)
        timer = window.asDynamic().setTimeout({ load(query) }, 220)
    }
}

private fun eventsMapPlaceSearch(
    mapPane: HTMLElement,
    overlayHost: HTMLElement,
    router: HistoryRouter,
    findOnMap: (PlacePick) -> PlacePick?,
    navigate: (PlacePick) -> Unit,
    onCreated: (PlacePick) -> Unit,
) {
    val desktopSearch = el("div", "events-map-search events-map-search-desktop")
    val desktopPanel = el("div", "events-map-search-panel")
    desktopSearch.appendChild(desktopPanel)
    mapPane.appendChild(desktopSearch)
    eventsMapSearchPanel(desktopPanel, overlayHost, router, false, {}, findOnMap, navigate, onCreated)

    val mobileButton = el("button", "icon-btn events-map-search-toggle")
    mobileButton.setAttribute("type", "button")
    mobileButton.setAttribute("aria-label", Town.t("searchPlaces"))
    mobileButton.asDynamic().innerHTML =
        """<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.8" cy="10.8" r="6.8" fill="none" stroke="currentColor" stroke-width="2"/><path d="m16 16 5 5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>"""
    mobileButton.onClick {
        val backdrop = el("div", "dialog-backdrop events-place-search-backdrop")
        val dialog = el("div", "dialog events-place-search-dialog")
        dialog.setAttribute("role", "dialog")
        dialog.setAttribute("aria-modal", "true")
        dialog.setAttribute("aria-label", Town.t("searchPlaces"))
        val heading = el("div", "events-place-search-heading")
        heading.appendChild(el("h2", text = Town.t("searchPlaces")))
        val close = el("button", "icon-btn", text = "×")
        close.setAttribute("type", "button")
        close.setAttribute("aria-label", Town.t("close"))
        heading.appendChild(close)
        dialog.appendChild(heading)
        val panel = el("div", "events-map-search-panel")
        dialog.appendChild(panel)
        backdrop.appendChild(dialog)
        fun closeDialog() {
            backdrop.asDynamic().remove()
        }
        close.onClick { closeDialog() }
        backdrop.closeOnOutsideClick { closeDialog() }
        overlayHost.appendChild(backdrop)
        eventsMapSearchPanel(panel, overlayHost, router, true, ::closeDialog, findOnMap, navigate, onCreated)
    }
    mapPane.appendChild(mobileButton)
}

private fun eventsMapSearchPanel(
    host: HTMLElement,
    overlayHost: HTMLElement,
    router: HistoryRouter,
    closeOnExisting: Boolean,
    close: () -> Unit,
    findOnMap: (PlacePick) -> PlacePick?,
    navigate: (PlacePick) -> Unit,
    onCreated: (PlacePick) -> Unit,
) {
    val input = el("input", "events-map-search-input")
    input.setAttribute("type", "search")
    input.setAttribute("placeholder", Town.t("searchPlaces"))
    input.setAttribute("autocomplete", "off")
    val results = el("div", "events-map-search-results")
    host.appendChild(input)
    host.appendChild(results)
    host.asDynamic().addEventListener("pointerdown", { event: dynamic -> event.stopPropagation() })

    var request = 0
    var selection = 0
    var timer: dynamic = null
    fun coordinates(place: PlacePick, done: (Boolean) -> Unit) {
        if (place.lat != null && place.lng != null) {
            done(true)
            return
        }
        val placeId = place.placeId
        if (placeId.isNullOrBlank()) {
            done(false)
            return
        }
        http("GET", "/api/places/details?id=${encodeQuery(placeId)}") { status, body ->
            if (status == 200) {
                val details = parseJson(body)
                place.name = (details.name as? String)?.takeIf { it.isNotBlank() } ?: place.name
                place.lat = if (details.lat == null || details.lat == undefined) null else num(details.lat)
                place.lng = if (details.lng == null || details.lng == undefined) null else num(details.lng)
            }
            done(place.lat != null && place.lng != null)
        }
    }

    fun addPlace(place: PlacePick, button: HTMLElement, hint: HTMLElement) {
        if (Town.me == null) {
            showAuthDialog(overlayHost, onSuccess = { addPlace(place, button, hint) })
            return
        }
        button.asDynamic().disabled = true
        coordinates(place) { hasCoordinates ->
            if (!hasCoordinates) {
                button.asDynamic().disabled = false
                hint.textContent = Town.t("unavailable")
                hint.asDynamic().hidden = false
                return@coordinates
            }
            val payload = js("({})")
            payload.name = place.name
            payload.lat = place.lat
            payload.lng = place.lng
            http("POST", "/api/places", jsonStringify(payload)) { status, body ->
                if (status != 201) {
                    button.asDynamic().disabled = false
                    hint.textContent = errorText(body, status)
                    hint.asDynamic().hidden = false
                    return@http
                }
                val created = parseJson(body)
                val added = PlacePick(
                    name = (created.name as? String)?.takeIf { it.isNotBlank() } ?: place.name,
                    placeId = place.placeId,
                    lat = if (created.lat == null || created.lat == undefined) place.lat else num(created.lat),
                    lng = if (created.lng == null || created.lng == undefined) place.lng else num(created.lng),
                    id = (created.id as? String)?.takeIf { it.isNotBlank() },
                )
                button.asDynamic().hidden = true
                hint.asDynamic().hidden = true
                onCreated(added)
                navigate(added)
                close()
                openLocationSheet(
                    overlayHost,
                    router,
                    added.name,
                    added.placeId,
                    added.lat,
                    added.lng,
                    customLocationId = added.id,
                )
            }
        }
    }

    fun placeRow(node: dynamic): HTMLElement {
        val place = PlacePick(
            name = (node.name as? String).orEmpty(),
            placeId = (node.placeId as? String)?.takeIf { it.isNotBlank() },
            lat = if (node.lat == null || node.lat == undefined) null else num(node.lat),
            lng = if (node.lng == null || node.lng == undefined) null else num(node.lng),
            id = (node.id as? String)?.takeIf { it.isNotBlank() },
        )
        val suggestion = el("div", "events-map-place-suggestion")
        val select = el("button", "events-map-place-select")
        select.setAttribute("type", "button")
        select.appendChild(el("strong", text = place.name))
        val address = (node.address as? String).orEmpty()
        if (address.isNotBlank() && address != place.name) select.appendChild(el("small", text = address))
        val add = el("button", "primary events-map-add-place", text = Town.t("addThisPlace"))
        add.setAttribute("type", "button")
        add.asDynamic().hidden = true
        val hint = el("p", "hint events-map-place-hint")
        hint.asDynamic().hidden = true
        select.onClick {
            val version = ++selection
            val oldButtons = results.asDynamic().querySelectorAll(".events-map-add-place")
            for (i in 0 until (oldButtons.length as Int)) oldButtons[i].hidden = true
            val oldHints = results.asDynamic().querySelectorAll(".events-map-place-hint")
            for (i in 0 until (oldHints.length as Int)) oldHints[i].hidden = true
            hint.asDynamic().hidden = true
            add.asDynamic().hidden = true
            val existing = findOnMap(place)
            if (existing != null) {
                if (place.lat == null) place.lat = existing.lat
                if (place.lng == null) place.lng = existing.lng
                navigate(existing)
                if (closeOnExisting) close()
                return@onClick
            }
            coordinates(place) { hasCoordinates ->
                if (version != selection) return@coordinates
                if (!hasCoordinates) {
                    hint.textContent = Town.t("unavailable")
                    hint.asDynamic().hidden = false
                    return@coordinates
                }
                navigate(place)
                add.asDynamic().hidden = false
            }
        }
        add.onClick { addPlace(place, add, hint) }
        suggestion.appendChild(select)
        suggestion.appendChild(add)
        suggestion.appendChild(hint)
        return suggestion
    }

    fun load(query: String) {
        val token = ++request
        results.clear()
        if (query.trim().length < 2) return
        results.appendChild(el("p", "hint events-map-search-loading", text = Town.t("loading")))
        http("GET", "/api/places?q=${encodeQuery(query.trim())}") { status, body ->
            if (token != request) return@http
            results.clear()
            if (status != 200) {
                results.appendChild(el("p", "hint", text = errorText(body, status)))
                return@http
            }
            val places = parseJson(body).places
            val length = places?.length as? Int ?: 0
            if (length == 0) {
                results.appendChild(el("p", "hint", text = Town.t("noPlaceMatches")))
                return@http
            }
            for (i in 0 until length) results.appendChild(placeRow(places[i]))
        }
    }

    input.asDynamic().oninput = {
        val query = (input.asDynamic().value as? String).orEmpty()
        ++selection
        results.clear()
        if (timer != null) window.asDynamic().clearTimeout(timer)
        timer = window.asDynamic().setTimeout({ load(query) }, 220)
    }
    if (closeOnExisting) input.asDynamic().focus()
}

private fun samePlace(first: PlacePick, second: PlacePick): Boolean {
    val firstId = first.id?.takeIf { it.isNotBlank() }
    val secondId = second.id?.takeIf { it.isNotBlank() }
    if (firstId != null && secondId != null && firstId == secondId) return true
    val firstPlaceId = first.placeId?.takeIf { it.isNotBlank() }
    val secondPlaceId = second.placeId?.takeIf { it.isNotBlank() }
    return firstPlaceId != null && secondPlaceId != null && firstPlaceId == secondPlaceId
}

internal fun composerEventFields(): ComposerEvent {
    val state = ComposerEvent()
    val box = el("div", "event-composer")
    val toggle = el("input")
    toggle.setAttribute("type", "checkbox")
    val row = el("label", "switch-row")
    row.appendChild(toggle)
    row.appendChild(el("span", "switch-ui"))
    row.appendChild(el("span", text = "📅 ${Town.t("addEvent")}"))
    val fields = el("div", "event-fields")
    fields.asDynamic().hidden = true
    val start = field(Town.t("eventStart"), "datetime-local", "")
    val end = field(Town.t("eventEnd"), "datetime-local", "")
    val endRow = el("div", "event-end-row")
    val removeEnd = el("button", "event-end-remove", text = "×")
    removeEnd.setAttribute("type", "button")
    removeEnd.setAttribute("aria-label", Town.t("removeEventEnd"))
    removeEnd.asDynamic().hidden = true
    endRow.appendChild(end.root)
    endRow.appendChild(removeEnd)
    val price = field(Town.t("eventPrice"), "text", Town.t("eventPriceHint"))
    price.input.setAttribute("inputmode", "numeric")
    price.input.setAttribute("pattern", "[0-9]*")
    price.input.setAttribute("autocomplete", "off")
    fields.appendChild(start.root)
    fields.appendChild(endRow)
    fields.appendChild(price.root)
    toggle.asDynamic().onchange = {
        val on = toggle.asDynamic().checked == true
        fields.asDynamic().hidden = !on
        state.enabled = on
    }
    start.input.asDynamic().onchange = { state.startAt = epochFromLocalInput(start.value()) }
    start.input.asDynamic().oninput = { state.startAt = epochFromLocalInput(start.value()) }
    fun updateEnd() {
        state.endAt = epochFromLocalInput(end.value())
        removeEnd.asDynamic().hidden = state.endAt == null
    }
    end.input.asDynamic().onchange = { updateEnd() }
    end.input.asDynamic().oninput = { updateEnd() }
    removeEnd.asDynamic().onclick = {
        end.input.asDynamic().value = ""
        state.endAt = null
        removeEnd.asDynamic().hidden = true
    }
    price.input.asDynamic().oninput = {
        val digits = price.value().filter { it.isDigit() }
        val amount = digits.toLongOrNull()?.coerceAtMost(Limits.EVENT_PRICE_MAX.toLong())
        val shown = amount?.toString().orEmpty()
        if (price.value() != shown) price.input.asDynamic().value = shown
        state.price = amount
    }
    box.appendChild(row)
    box.appendChild(fields)
    state.root = box
    return state
}

internal class ComposerEvent {
    var enabled: Boolean = false
    var startAt: Long? = null
    var endAt: Long? = null
    var price: Long? = null
    lateinit var root: HTMLElement
}

internal fun mapsUrl(location: String, placeId: String?, lat: Double? = null, lng: Double? = null): String {
    val place = placeId?.takeIf { it.isNotBlank() }
    if (place == null && lat != null && lng != null) {
        return "https://www.google.com/maps?q=$lat,$lng(${encodeQuery(location)})"
    }
    val query = encodeQuery(if (place != null) location else "$location ở gần Sài Gòn")
    return if (place == null) {
        "https://www.google.com/maps/search/?api=1&query=$query"
    } else {
        "https://www.google.com/maps/search/?api=1&query=$query&query_place_id=${encodeQuery(place)}"
    }
}

internal fun formatVnd(amount: Long): String {
    val digits = amount.toString()
    val grouped = buildString {
        digits.forEachIndexed { index, char ->
            if (index > 0 && (digits.length - index) % 3 == 0) append('.')
            append(char)
        }
    }
    return "${grouped}₫"
}

internal fun eventScheduleLabel(startAt: Long, endAt: Long?): String {
    val startClock = formatEventClock(startAt)
    val startDay = eventDayHeading(startAt)
    val end = endAt ?: return "$startClock ($startDay)"
    val endClock = formatEventClock(end)
    return if (saigonDay(startAt) == saigonDay(end)) {
        "$startClock – $endClock ($startDay)"
    } else {
        "$startClock ($startDay) – $endClock (${eventDayHeading(end)})"
    }
}

internal fun eventRelativeLabel(startAt: Long, endAt: Long?): String? {
    val now = num(js("Date.now()")).toLong()
    if (saigonDay(startAt) != saigonDay(now)) return null
    val delta = startAt - now
    return when {
        delta >= 60 * 60 * 1000L -> {
            val hours = delta / (60 * 60 * 1000L)
            if (hours <= 1L) Town.t("startsInHour") else Town.t("startsInHours", mapOf("count" to hours.toString()))
        }
        delta > 60 * 1000L -> Town.t("startsInMinutes", mapOf("count" to (delta / (60 * 1000L)).toString()))
        delta > 0 -> Town.t("startsInMinutes", mapOf("count" to "1"))
        endAt == null || now < endAt -> Town.t("happeningNow")
        else -> null
    }
}

internal fun eventWhenLabel(startAt: Long, endAt: Long?): String =
    eventRelativeLabel(startAt, endAt) ?: eventScheduleLabel(startAt, endAt)

internal fun epochFromLocalInput(value: String): Long? {
    if (value.isBlank()) return null
    val parsed = js("Date.parse")(value) as Double
    if (parsed.isNaN()) return null
    return parsed.toLong()
}

private fun openAttendees(post: PostCard, host: HTMLElement, router: HistoryRouter) {
    if (host.asDynamic().querySelector(".attendee-sheet") != null) return
    val backdrop = el("div", "dialog-backdrop attendee-sheet")
    val card = el("div", "dialog")
    card.appendChild(el("h2", text = Town.t("attendees")))
    val list = el("div", "member-list")
    card.appendChild(list)
    card.appendChild(el("button", "text-button", text = Town.t("close")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
    http("GET", "/api/posts/${post.id}/attendees") { status, body ->
        if (status != 200) {
            list.appendChild(el("p", "hint", text = errorText(body, status)))
            return@http
        }
        val people = parseJson(body).attendees
        val length = people?.length as? Int ?: 0
        if (length == 0) {
            list.appendChild(el("p", "hint", text = Town.t("noMembers")))
            return@http
        }
        for (i in 0 until length) list.appendChild(attendeeRow(eventPersonFrom(people[i]), host, router))
    }
}

private fun attendeeRow(person: EventPerson, host: HTMLElement, router: HistoryRouter): HTMLElement {
    val row = el("div", "attendee-row")
    val profile = el("button", "attendee-profile")
    profile.setAttribute("type", "button")
    if (!person.photoUrl.isNullOrBlank()) {
        val face = el("img", "face")
        face.setAttribute("alt", "")
        face.setAttribute("src", person.photoUrl)
        profile.appendChild(face)
    } else {
        profile.appendChild(el("span", "face initial", text = person.name.take(1).uppercase()))
    }
    val copy = el("span", "member-copy")
    copy.appendChild(el("strong", text = person.name))
    if (person.location.isNotBlank()) copy.appendChild(el("small", text = person.location))
    profile.appendChild(copy)
    profile.onClick {
        openMiniProfile(host, person.name, person.photoUrl, person.location, person.intro, person.id, router)
    }
    row.appendChild(profile)
    if (!person.phone.isNullOrBlank()) {
        row.appendChild(
            iconAction(Town.t("copyPhone"), COPY_ICON) {
                val value = person.phone
                try {
                    window.asDynamic().navigator.clipboard.writeText(value)
                    toast(host, Town.t("phoneCopied"))
                } catch (_: Throwable) {
                    toast(host, Town.t("copyFailed"))
                }
            },
        )
    }
    if (!person.zaloUrl.isNullOrBlank()) {
        row.appendChild(
            iconAction(Town.t("zaloReply"), CHAT_ICON) {
                window.asDynamic().open(person.zaloUrl, "_blank")
            },
        )
    }
    return row
}

private fun iconAction(label: String, svg: String, onClick: () -> Unit): HTMLElement {
    val button = el("button", "attendee-icon")
    button.setAttribute("type", "button")
    button.setAttribute("aria-label", label)
    button.asDynamic().innerHTML = svg
    button.onClick(onClick)
    return button
}

private fun peopleRow(node: dynamic, host: HTMLElement, router: HistoryRouter): HTMLElement {
    val person = eventPersonFrom(node)
    val row = el("div", "member-row people-row")
    val profile = el("button", "attendee-profile people-profile")
    profile.setAttribute("type", "button")
    if (!person.photoUrl.isNullOrBlank()) {
        val face = el("img", "face")
        face.setAttribute("alt", "")
        face.setAttribute("src", person.photoUrl)
        profile.appendChild(face)
    } else {
        profile.appendChild(el("span", "face initial", text = person.name.take(1).uppercase()))
    }
    val copy = el("span", "member-copy")
    copy.appendChild(el("strong", text = person.name))
    if (person.location.isNotBlank()) copy.appendChild(el("small", text = person.location))
    if (person.intro.isNotBlank()) copy.appendChild(el("small", "member-intro", text = person.intro))
    profile.appendChild(copy)
    profile.onClick {
        openMiniProfile(host, person.name, person.photoUrl, person.location, person.intro, person.id, router)
    }
    row.appendChild(profile)
    if (!person.latestPostId.isNullOrBlank() && !person.latestPostPhotoUrl.isNullOrBlank()) {
        val latestPost = el("button", "people-post-thumb")
        latestPost.setAttribute("type", "button")
        latestPost.setAttribute("aria-label", "Open latest post by ${person.name}")
        val photo = el("img", "people-post-photo")
        photo.setAttribute("alt", "")
        photo.setAttribute("src", person.latestPostPhotoUrl)
        latestPost.appendChild(photo)
        latestPost.onClick { router.navigate("/p/${person.latestPostId}") }
        row.appendChild(latestPost)
    }
    return row
}

private fun renderGroupedEvents(list: HTMLElement, events: List<PostCard>, router: HistoryRouter) {
    var lastDay = ""
    events.forEach { post ->
        val start = post.eventStartAt ?: return@forEach
        val day = eventDayHeading(start)
        if (day != lastDay) {
            list.appendChild(el("h4", "event-day", text = day))
            lastDay = day
        }
        val row = el("button", "event-row")
        row.setAttribute("type", "button")
        row.appendChild(el("span", "event-icon", text = EVENT_ICON))
        val copy = el("span", "event-row-copy")
        copy.appendChild(el("strong", text = post.message.ifBlank { post.location }))
        copy.appendChild(el("small", text = "${eventScheduleLabel(start, post.eventEndAt)} · ${post.location}"))
        copy.appendChild(attendeeFaces(post))
        row.appendChild(copy)
        row.onClick { router.navigate("/p/${post.id}") }
        list.appendChild(row)
    }
}

internal fun mountPlaceMap(
    host: HTMLElement,
    lat: Double?,
    lng: Double?,
    title: String,
    placeId: String? = null,
    zoomControl: Boolean = true,
) {
    if (lat == null || lng == null) {
        if (host.childElementCount == 0) {
            host.appendChild(el("p", "hint", text = "$PLACE_ICON $title"))
        }
        return
    }
    fun tryMount() {
        if (host.asDynamic().isConnected == false) {
            window.asDynamic().requestAnimationFrame { tryMount() }
            return
        }
        loadGoogleMaps { ready ->
            if (!ready || host.asDynamic().isConnected == false) {
                if (host.asDynamic().isConnected != false && host.childElementCount == 0) {
                    host.appendChild(el("p", "hint", text = "$PLACE_ICON $title"))
                }
                return@loadGoogleMaps
            }
            host.clear()
            val opts = js("{}")
            opts.center = js("{lat:10.7769,lng:106.7009}")
            opts.zoom = 16
            opts.disableDefaultUI = true
            opts.zoomControl = zoomControl
            val map = googleMap(host, opts)
            val pos = js("{}")
            pos.lat = lat
            pos.lng = lng
            googleHtmlMarker(map, pos, placeMapBubble(title, placeId, lat, lng))
            fun fit() {
                map.setCenter(pos)
                map.setZoom(16)
            }
            fun relayout() {
                val trigger = mapsEvent()?.trigger
                if (trigger != null && trigger != undefined) trigger(map, "resize")
                fit()
            }
            fit()
            window.asDynamic().requestAnimationFrame { relayout() }
            window.asDynamic().setTimeout({ relayout() }, 120)
        }
    }
    tryMount()
}

private fun placeMapBubble(title: String, placeId: String?, lat: Double? = null, lng: Double? = null): HTMLElement {
    val bubble = el("button", "event-map-bubble")
    bubble.setAttribute("type", "button")
    bubble.setAttribute("aria-label", title)
    val heading = el("span", "event-map-bubble-title")
    heading.appendChild(el("span", "event-map-bubble-emoji", text = PLACE_ICON))
    heading.appendChild(el("strong", text = title))
    bubble.appendChild(heading)
    bubble.onClick {
        window.open(mapsUrl(title, placeId, lat, lng), WindowTarget._blank)
    }
    return bubble
}

private data class MapMarkerBox(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    fun overlaps(other: MapMarkerBox): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

    fun overlapArea(other: MapMarkerBox): Double {
        val overlapWidth = (minOf(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0.0)
        val overlapHeight = (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0.0)
        return overlapWidth * overlapHeight
    }
}

private class EventsMapMarker(
    val overlay: dynamic,
    val position: dynamic,
    val content: HTMLElement,
    val isBubble: Boolean,
    val isLocation: Boolean,
    val postCount: Long,
    val attendeeCount: Int,
    val index: Int,
)

private data class PositionedMapMarker(val marker: EventsMapMarker, val box: MapMarkerBox)

private fun packMapMarkers(markers: List<PositionedMapMarker>): List<PositionedMapMarker> {
    val prioritized = markers.sortedWith(
        compareByDescending<PositionedMapMarker> { it.marker.isBubble }
            .thenByDescending { it.marker.postCount }
            .thenByDescending { it.marker.attendeeCount }
            .thenBy { it.marker.index },
    )
    val accepted = mutableListOf<PositionedMapMarker>()
    prioritized.forEach { candidate ->
        if (accepted.none { it.box.overlaps(candidate.box) }) accepted += candidate
    }
    return accepted
}

private fun mapMarkerBox(marker: EventsMapMarker, point: dynamic): MapMarkerBox {
    val content = marker.content.asDynamic()
    val width = num(content.offsetWidth).takeIf { it > 0.0 } ?: if (marker.isBubble) 120.0 else 24.0
    val height = num(content.offsetHeight).takeIf { it > 0.0 } ?: if (marker.isBubble) 56.0 else 24.0
    val x = num(point.x)
    val y = num(point.y)
    val left = x - width / 2.0
    val top = if (marker.isBubble) y - height - 10.0 else y - height / 2.0
    val right = left + width
    val bottom = top + height + if (marker.isBubble) 5.0 else 0.0
    return MapMarkerBox(left, top, right, bottom)
}

private fun setMapMarkerShown(content: HTMLElement, shown: Boolean) {
    val node = content.asDynamic()
    if (shown) {
        node.tabIndex = 0
        node.setAttribute("aria-hidden", "false")
        if (node.classList.contains("is-shown") == true || node.__eventsMapMarkerPendingShow == true) return
        val generation = num(node.__eventsMapMarkerGeneration).toInt() + 1
        node.__eventsMapMarkerGeneration = generation
        node.__eventsMapMarkerPendingShow = true
        window.asDynamic().requestAnimationFrame {
            window.asDynamic().requestAnimationFrame {
                if (num(node.__eventsMapMarkerGeneration).toInt() == generation && node.isConnected != false) {
                    node.__eventsMapMarkerPendingShow = false
                    node.classList.add("is-shown")
                }
            }
        }
    } else {
        node.__eventsMapMarkerGeneration = num(node.__eventsMapMarkerGeneration).toInt() + 1
        node.__eventsMapMarkerPendingShow = false
        node.classList.remove("is-shown")
        node.tabIndex = -1
        node.setAttribute("aria-hidden", "true")
    }
}

private fun mountEventsMap(
    host: HTMLElement,
    events: List<PostCard>,
    locations: List<PlacePick>,
    sheetHost: HTMLElement,
    router: HistoryRouter,
    onMapTap: (Double, Double) -> Unit,
    onMapReady: ((PlacePick) -> Unit, (PlacePick) -> Unit, (Boolean) -> Unit, () -> Unit) -> Unit,
) {
    val marked = events.filter { it.lat != null && it.lng != null }
    val markedLocations = locations.filter { it.lat != null && it.lng != null }
    fun tryMount() {
        if (host.asDynamic().isConnected == false) {
            window.asDynamic().requestAnimationFrame { tryMount() }
            return
        }
        loadGoogleMaps { ready ->
            if (!ready || host.asDynamic().isConnected == false) {
                if (host.asDynamic().isConnected != false && host.childElementCount == 0) {
                    host.appendChild(el("p", "hint", text = "📍 ${Town.t("events")}"))
                }
                return@loadGoogleMaps
            }
            val opts = js("{}")
            opts.center = js("{lat:10.7769,lng:106.7009}")
            opts.zoom = 12
            opts.disableDefaultUI = true
            opts.zoomControl = true
            val map = googleMap(host, opts)
            var currentLocationMarker: dynamic = null
            fun setMapType(satellite: Boolean) {
                map.setMapTypeId(if (satellite) "hybrid" else "roadmap")
            }
            fun locateMe() {
                val geolocation = window.asDynamic().navigator.geolocation
                if (geolocation == null || geolocation == undefined) {
                    toast(sheetHost, Town.t("locationUnavailable"))
                    return
                }
                val options = js("({})")
                options.enableHighAccuracy = true
                options.timeout = 10000
                options.maximumAge = 30000
                geolocation.getCurrentPosition(
                    { result: dynamic ->
                        val position = js("({})")
                        position.lat = num(result.coords.latitude)
                        position.lng = num(result.coords.longitude)
                        map.panTo(position)
                        map.setZoom(16)
                        if (currentLocationMarker != null && currentLocationMarker != undefined) {
                            currentLocationMarker.setMap(null)
                        }
                        val dot = el("div", "events-current-location-dot")
                        currentLocationMarker = googleHtmlMarker(map, position, dot)
                    },
                    { error: dynamic ->
                        toast(
                            sheetHost,
                            Town.t(if (num(error.code).toInt() == 1) "locationPermissionDenied" else "locationUnavailable"),
                        )
                    },
                    options,
                )
            }
            map.addListener("click", { event: dynamic ->
                val point = event.latLng
                if (point != null && point != undefined) onMapTap(num(point.lat()), num(point.lng()))
            })
            val bounds = googleBounds()
            val mapMarkers = mutableListOf<EventsMapMarker>()
            val renderedLocations = markedLocations.toMutableList()
            var markerIndex = 0
            var hasNavigated = false
            marked.forEach { post ->
                val pos = js("{}")
                pos.lat = post.lat
                pos.lng = post.lng
                val content = eventMapBubble(post)
                val overlay = googleHtmlMarker(map, pos, content, reveal = false)
                mapMarkers += EventsMapMarker(
                    overlay = overlay,
                    position = pos,
                    content = content,
                    isBubble = true,
                    isLocation = false,
                    postCount = 1,
                    attendeeCount = post.attendeeCount,
                    index = markerIndex++,
                )
                bounds.extend(pos)
            }
            markedLocations.forEach { location ->
                val lat = location.lat ?: return@forEach
                val lng = location.lng ?: return@forEach
                val pos = js("{}")
                pos.lat = lat
                pos.lng = lng
                val dotContent = userLocationDot(location, sheetHost, router)
                val dot = googleHtmlMarker(map, pos, dotContent, reveal = false)
                mapMarkers += EventsMapMarker(
                    overlay = dot,
                    position = pos,
                    content = dotContent,
                    isBubble = false,
                    isLocation = true,
                    postCount = location.postCount.coerceAtLeast(0),
                    attendeeCount = 0,
                    index = markerIndex++,
                )
                val bubbleContent = userLocationBubble(location, sheetHost, router)
                val bubble = googleHtmlMarker(map, pos, bubbleContent, reveal = false)
                mapMarkers += EventsMapMarker(
                    overlay = bubble,
                    position = pos,
                    content = bubbleContent,
                    isBubble = true,
                    isLocation = true,
                    postCount = location.postCount.coerceAtLeast(0),
                    attendeeCount = 0,
                    index = markerIndex++,
                )
                bounds.extend(pos)
            }
            fun updateMapMarkers() {
                val zoomedIn = num(map.getZoom()) >= 18.0
                val positioned = mapMarkers
                    .filter { !it.isLocation || it.isBubble == zoomedIn }
                    .mapNotNull { marker ->
                        val projection = marker.overlay.getProjection()
                        if (projection == null || projection == undefined) return@mapNotNull null
                        val point = projection.fromLatLngToDivPixel(marker.position)
                        if (point == null || point == undefined) return@mapNotNull null
                        PositionedMapMarker(marker, mapMarkerBox(marker, point))
                    }
                val packed = packMapMarkers(positioned)
                val markerCount = packed.size
                val mapWidth = num(host.asDynamic().clientWidth)
                val mapHeight = num(host.asDynamic().clientHeight)
                val placedLabels = mutableListOf<MapMarkerBox>()
                packed.forEach { item ->
                    val marker = item.marker
                    if (!marker.isLocation || marker.isBubble) return@forEach
                    val label = marker.content.asDynamic().querySelector(".user-location-name")
                    if (label == null || label == undefined) return@forEach
                    val labelWidth = num(label.offsetWidth)
                    val labelHeight = num(label.offsetHeight)
                    if (labelWidth <= 0.0 || labelHeight <= 0.0) return@forEach
                    val gap = 8.0
                    val centerX = (item.box.left + item.box.right) / 2.0
                    val centerY = (item.box.top + item.box.bottom) / 2.0
                    val preferredLeft = centerX > mapWidth / 2.0
                    fun labelBox(left: Boolean): MapMarkerBox {
                        val labelLeft = if (left) {
                            item.box.left - gap - labelWidth
                        } else {
                            item.box.right + gap
                        }
                        return MapMarkerBox(
                            left = labelLeft,
                            top = centerY - labelHeight / 2.0,
                            right = labelLeft + labelWidth,
                            bottom = centerY + labelHeight / 2.0,
                        )
                    }
                    fun overlapArea(box: MapMarkerBox): Double =
                        packed.asSequence()
                            .filter { it.marker !== marker }
                            .sumOf { box.overlapArea(it.box) } +
                            placedLabels.sumOf { box.overlapArea(it) }
                    fun overflowArea(box: MapMarkerBox): Double {
                        if (mapWidth <= 0.0 || mapHeight <= 0.0) return 0.0
                        val mapBounds = MapMarkerBox(0.0, 0.0, mapWidth, mapHeight)
                        return (box.right - box.left) * (box.bottom - box.top) - box.overlapArea(mapBounds)
                    }
                    val leftBox = labelBox(true)
                    val rightBox = labelBox(false)
                    val leftOverlap = overlapArea(leftBox)
                    val rightOverlap = overlapArea(rightBox)
                    val leftOverflow = overflowArea(leftBox)
                    val rightOverflow = overflowArea(rightBox)
                    val useLeft = when {
                        leftOverlap < rightOverlap -> true
                        rightOverlap < leftOverlap -> false
                        leftOverflow < rightOverflow -> true
                        rightOverflow < leftOverflow -> false
                        else -> preferredLeft
                    }
                    val chosenBox = if (useLeft) leftBox else rightBox
                    marker.content.asDynamic().classList.toggle("is-label-left", useLeft)
                    placedLabels += chosenBox
                }
                packed.forEachIndexed { index, item ->
                    val marker = item.marker
                    marker.content.asDynamic().style.zIndex =
                        (if (marker.isBubble) 2 * markerCount - index else markerCount - index).toString()
                }
                val visible = packed.map { it.marker }.toSet()
                mapMarkers.forEach { marker -> setMapMarkerShown(marker.content, marker in visible) }
            }
            fun navigate(place: PlacePick) {
                val lat = place.lat ?: return
                val lng = place.lng ?: return
                hasNavigated = true
                val pos = js("({})")
                pos.lat = lat
                pos.lng = lng
                map.setCenter(pos)
                map.setZoom(18)
                window.asDynamic().requestAnimationFrame { updateMapMarkers() }
            }
            fun addLocation(location: PlacePick) {
                val lat = location.lat ?: return
                val lng = location.lng ?: return
                if (renderedLocations.any { samePlace(it, location) }) return
                renderedLocations += location
                val pos = js("({})")
                pos.lat = lat
                pos.lng = lng
                val dotContent = userLocationDot(location, sheetHost, router)
                val dot = googleHtmlMarker(map, pos, dotContent, reveal = false)
                mapMarkers += EventsMapMarker(
                    overlay = dot,
                    position = pos,
                    content = dotContent,
                    isBubble = false,
                    isLocation = true,
                    postCount = location.postCount.coerceAtLeast(0),
                    attendeeCount = 0,
                    index = markerIndex++,
                )
                val bubbleContent = userLocationBubble(location, sheetHost, router)
                val bubble = googleHtmlMarker(map, pos, bubbleContent, reveal = false)
                mapMarkers += EventsMapMarker(
                    overlay = bubble,
                    position = pos,
                    content = bubbleContent,
                    isBubble = true,
                    isLocation = true,
                    postCount = location.postCount.coerceAtLeast(0),
                    attendeeCount = 0,
                    index = markerIndex++,
                )
                updateMapMarkers()
            }
            map.addListener("zoom_changed", { updateMapMarkers() })
            map.addListener("idle", { updateMapMarkers() })
            fun fit() {
                if (hasNavigated) return
                val total = marked.size + markedLocations.size
                if (total == 1) {
                    val only = js("{}")
                    val post = marked.firstOrNull()
                    val location = markedLocations.firstOrNull()
                    only.lat = post?.lat ?: location?.lat
                    only.lng = post?.lng ?: location?.lng
                    map.setCenter(only)
                    map.setZoom(15)
                } else if (total > 1) {
                    map.fitBounds(bounds)
                }
            }
            fun relayout() {
                val trigger = mapsEvent()?.trigger
                if (trigger != null && trigger != undefined) trigger(map, "resize")
                fit()
                window.asDynamic().requestAnimationFrame { updateMapMarkers() }
            }
            fit()
            updateMapMarkers()
            window.asDynamic().requestAnimationFrame { relayout() }
            window.asDynamic().setTimeout({ relayout() }, 120)
            onMapReady(::navigate, ::addLocation, ::setMapType, ::locateMe)
        }
    }
    tryMount()
}

private fun openMapAddLocationDialog(
    host: HTMLElement,
    router: HistoryRouter,
    lat: Double,
    lng: Double,
    onCreated: (PlacePick) -> Unit,
) {
    if (Town.me == null) {
        showAuthDialog(host, onSuccess = { openMapAddLocationDialog(host, router, lat, lng, onCreated) })
        return
    }

    val backdrop = el("div", "dialog-backdrop map-location-backdrop")
    val dialog = el("div", "dialog map-location-dialog")
    dialog.setAttribute("role", "dialog")
    dialog.setAttribute("aria-modal", "true")
    dialog.appendChild(el("h2", text = Town.t("createLocation")))
    val name = field(Town.t("location"), "text", Town.t("location"))
    dialog.appendChild(name.root)
    val hint = el("p", "hint map-location-hint")
    hint.asDynamic().hidden = true
    dialog.appendChild(hint)
    val actions = el("div", "dialog-actions")
    val cancel = el("button", "text-button", text = Town.t("cancel"))
    cancel.setAttribute("type", "button")
    val save = el("button", "primary", text = Town.t("save"))
    save.setAttribute("type", "button")
    actions.appendChild(cancel)
    actions.appendChild(save)
    dialog.appendChild(actions)
    backdrop.appendChild(dialog)
    fun close() = backdrop.asDynamic().remove()
    cancel.onClick { close() }
    backdrop.closeOnOutsideClick { close() }
    save.onClick {
        val locationName = name.value().trim()
        if (locationName.isBlank()) {
            hint.textContent = Town.t("enterLocationName")
            hint.asDynamic().hidden = false
            name.input.focus()
            return@onClick
        }
        save.asDynamic().disabled = true
        val payload = js("({})")
        payload.name = locationName
        payload.lat = lat
        payload.lng = lng
        http("POST", "/api/places", jsonStringify(payload)) { status, body ->
            if (status != 201) {
                save.asDynamic().disabled = false
                hint.textContent = errorText(body, status)
                hint.asDynamic().hidden = false
                return@http
            }
            val created = parseJson(body)
            val place = PlacePick(
                name = (created.name as? String)?.takeIf { it.isNotBlank() } ?: locationName,
                placeId = (created.placeId as? String)?.takeIf { it.isNotBlank() },
                lat = if (created.lat == null || created.lat == undefined) lat else num(created.lat),
                lng = if (created.lng == null || created.lng == undefined) lng else num(created.lng),
                id = (created.id as? String)?.takeIf { it.isNotBlank() },
            )
            onCreated(place)
            close()
            openLocationSheet(host, router, place.name, place.placeId, place.lat, place.lng, customLocationId = place.id)
        }
    }
    host.appendChild(backdrop)
    name.input.focus()
}

private fun userLocationDot(location: PlacePick, host: HTMLElement, router: HistoryRouter): HTMLElement {
    val dot = el("button", "user-location-dot")
    dot.setAttribute("type", "button")
    val count = location.postCount.coerceAtLeast(0)
    dot.setAttribute("aria-label", userLocationAriaLabel(location))
    dot.textContent = count.coerceAtMost(99).toString()
    location.photoUrl?.takeIf { it.isNotBlank() }?.let { photoUrl ->
        dot.asDynamic().style.backgroundImage = "url(\"${prefixServerOrigin(photoUrl)}\")"
    }
    if (location.hasActivities) dot.appendChild(activityMapDot())
    if (location.name.isNotBlank()) {
        val name = el("span", "user-location-name")
        name.setAttribute("aria-hidden", "true")
        name.appendChild(el("span", "user-location-name-title", text = location.name))
        if (location.activityEmojis.isNotEmpty()) {
            name.appendChild(el("span", "user-location-activities", text = location.activityEmojis.joinToString(" ")))
        }
        dot.appendChild(name)
    }
    dot.onClick {
        openLocationSheet(
            host,
            router,
            location.name,
            location.placeId,
            location.lat,
            location.lng,
            customLocationId = location.id,
        )
    }
    return dot
}

private fun userLocationBubble(location: PlacePick, host: HTMLElement, router: HistoryRouter): HTMLElement {
    val bubble = el("button", "event-map-bubble custom-location-bubble")
    bubble.setAttribute("type", "button")
    val count = location.postCount.coerceAtLeast(0)
    bubble.setAttribute("aria-label", userLocationAriaLabel(location))
    val photoUrl = location.photoUrl?.takeIf { it.isNotBlank() }
    if (photoUrl != null) {
        bubble.asDynamic().classList.add("has-photo")
        val photo = el("span", "custom-location-bubble-photo")
        photo.setAttribute("aria-hidden", "true")
        photo.asDynamic().style.backgroundImage = "url(\"${prefixServerOrigin(photoUrl)}\")"
        bubble.appendChild(photo)
    }
    val heading = el("span", "event-map-bubble-title")
    heading.appendChild(el("span", "event-map-bubble-emoji", text = PLACE_ICON))
    heading.appendChild(el("strong", text = location.name))
    val activityCount = location.activityCount.coerceAtLeast(0)
    val counts = buildList {
        if (activityCount > 0) {
            add(Town.t("activityCountShort", mapOf("count" to activityCount.toString())))
        }
        add(Town.t("postCount", mapOf("count" to count.toString())))
    }.joinToString(", ")
    val meta = el("span", "event-map-bubble-meta")
    if (activityCount > 0 && location.activityEmojis.isNotEmpty()) {
        meta.appendChild(el("span", "event-map-bubble-activities", text = location.activityEmojis.joinToString(" ")))
    }
    meta.appendChild(el("span", "event-map-bubble-counts", text = counts))
    if (photoUrl != null) {
        val text = el("span", "custom-location-bubble-text")
        text.appendChild(heading)
        text.appendChild(meta)
        bubble.appendChild(text)
    } else {
        bubble.appendChild(heading)
        bubble.appendChild(meta)
    }
    if (location.hasActivities) bubble.appendChild(activityMapDot())
    bubble.onClick {
        openLocationSheet(
            host,
            router,
            location.name,
            location.placeId,
            location.lat,
            location.lng,
            customLocationId = location.id,
        )
    }
    return bubble
}

private fun userLocationAriaLabel(location: PlacePick): String = buildList {
    add(location.name)
    val activityCount = location.activityCount.coerceAtLeast(0)
    if (activityCount > 0) {
        add(Town.t("activityCountShort", mapOf("count" to activityCount.toString())))
    }
    add(Town.t("postCount", mapOf("count" to location.postCount.coerceAtLeast(0).toString())))
}.joinToString(", ")

private fun loadGoogleMaps(done: (Boolean) -> Unit) {
    fun capture(lib: dynamic = null): Boolean {
        val fromWindow = window.asDynamic().google?.maps
        val windowReady = fromWindow != null && fromWindow != undefined && isJsFunction(fromWindow.Map)
        val fromLib = if (lib != null && lib != undefined && isJsFunction(lib.Map)) lib else null
        val ns = if (windowReady) fromWindow else fromLib
        if (ns == null) return false
        mapsApi = ns
        return true
    }
    fun complete(ok: Boolean) {
        if (mapsWaiters.isEmpty() && (ok || !mapsLoadStarted)) return
        if (!ok) {
            mapsLoadStarted = false
            mapsImporting = false
        }
        val waiters = mapsWaiters.toList()
        mapsWaiters.clear()
        waiters.forEach { it(ok) }
    }
    fun finish() {
        if (capture()) {
            complete(true)
            return
        }
        if (mapsImporting) return
        val maps = window.asDynamic().google?.maps
        val importer = maps?.importLibrary
        if (importer == null || importer == undefined) return
        mapsImporting = true
        val onOk: (dynamic) -> Unit = { lib ->
            mapsImporting = false
            complete(capture(lib) || capture())
        }
        val onErr: (dynamic) -> Unit = {
            mapsImporting = false
            complete(capture())
        }
        try {
            importer("maps").then(onOk, onErr)
        } catch (_: Throwable) {
            mapsImporting = false
            window.asDynamic().setTimeout({ finish() }, 50)
        }
    }
    if (capture()) {
        done(true)
        return
    }
    mapsWaiters += done
    if (mapsLoadStarted) {
        finish()
        return
    }
    mapsLoadStarted = true
    if (window.asDynamic().google?.maps != null && window.asDynamic().google.maps != undefined) {
        finish()
        return
    }
    http("GET", "/api/config") { status, body ->
        if (status != 200) {
            complete(false)
            return@http
        }
        val key = parseJson(body).mapsKey as? String
        if (key.isNullOrBlank()) {
            complete(false)
            return@http
        }
        window.asDynamic().gm_authFailure = {
            mapsApi = null
            complete(false)
        }
        window.asDynamic().__sqMapsReady = { finish() }
        val pending = document.asDynamic().querySelector("script[data-sq-maps]")
        if (pending != null && pending != undefined) {
            pending.addEventListener("load") { finish() }
            pending.addEventListener("error") { complete(false) }
            finish()
            return@http
        }
        val script = document.createElement("script")
        script.setAttribute("data-sq-maps", "1")
        script.setAttribute("async", "")
        script.setAttribute("referrerpolicy", "origin")
        script.setAttribute("src", "https://maps.googleapis.com/maps/api/js?key=$key&v=weekly&loading=async&callback=__sqMapsReady")
        script.asDynamic().onload = { finish() }
        script.asDynamic().onerror = { complete(false) }
        document.asDynamic().head.appendChild(script)
    }
}

private fun mapsEvent(): dynamic {
    val fromApi = mapsApi?.event
    if (fromApi != null && fromApi != undefined) return fromApi
    return window.asDynamic().google?.maps?.event
}

private fun isJsFunction(value: dynamic): Boolean {
    if (value == null || value == undefined) return false
    return js("typeof value === 'function'") == true
}

private fun googleMap(element: HTMLElement, opts: dynamic): dynamic {
    val ctor = mapsApi.Map
    return js("new ctor(element, opts)")
}

private fun eventMapBubble(post: PostCard): HTMLElement {
    val bubble = el("button", "event-map-bubble")
    bubble.setAttribute("type", "button")
    val name = post.message.ifBlank { post.location }
    bubble.setAttribute("aria-label", name)
    val title = el("span", "event-map-bubble-title")
    title.appendChild(el("span", "event-map-bubble-emoji", text = post.communityEmoji ?: EVENT_ICON))
    title.appendChild(el("strong", text = name))
    bubble.appendChild(title)
    val start = post.eventStartAt
    val price = post.eventPrice
    val priceText = if (price != null && price > 0) formatVnd(price) else Town.t("free")
    val meta = buildString {
        if (start != null) {
            append(formatEventClock(start))
            append(" · ")
        }
        append(PRICE_ICON)
        append(' ')
        append(priceText)
    }
    bubble.appendChild(el("span", "event-map-bubble-meta", text = meta))
    if (post.location.isNotBlank()) {
        val place = el("span", "event-map-bubble-place")
        place.appendChild(el("span", text = PLACE_ICON))
        place.appendChild(el("span", text = post.location))
        bubble.appendChild(place)
    }
    bubble.appendChild(attendeeFaces(post, "event-faces event-map-bubble-faces"))
    bubble.onClick {
        window.asDynamic().location.assign("/p/${post.id}")
    }
    return bubble
}

private fun activityMapDot(): HTMLElement {
    val dot = el("span", "activity-map-dot")
    dot.setAttribute("aria-hidden", "true")
    return dot
}

private fun attendeeFaces(post: PostCard, className: String = "event-faces"): HTMLElement {
    val faces = el("span", className)
    fillAttendeeFaces(faces, post)
    return faces
}

private fun fillAttendeeFaces(host: HTMLElement, post: PostCard) {
    val shown = post.attendees.take(5)
    shown.forEach { person ->
        if (!person.photoUrl.isNullOrBlank()) {
            val img = el("img", "face event-face")
            img.setAttribute("alt", "")
            img.setAttribute("src", person.photoUrl)
            host.appendChild(img)
        } else {
            host.appendChild(el("span", "face event-face initial", text = person.name.take(1).uppercase()))
        }
    }
    val count = post.attendeeCount.coerceAtLeast(shown.size)
    host.appendChild(el("span", text = if (count > 0) "${Town.t("attendees")} · $count" else Town.t("attendees")))
}

private fun googleHtmlMarker(
    map: dynamic,
    position: dynamic,
    content: HTMLElement,
    reveal: Boolean = true,
): dynamic {
    val overlay = googleOverlay()
    overlay.onAdd = {
        val panes = overlay.getPanes()
        val pane = panes?.overlayMouseTarget ?: panes?.floatPane
        if (pane != null && pane != undefined) pane.appendChild(content)
        setMapMarkerShown(content, false)
        val prevent = mapsApi?.OverlayView?.preventMapHitsAndGesturesFrom
            ?: window.asDynamic().google?.maps?.OverlayView?.preventMapHitsAndGesturesFrom
        if (prevent != null && prevent != undefined) prevent(content)
        if (reveal) setMapMarkerShown(content, true)
    }
    overlay.draw = {
        val projection = overlay.getProjection()
        if (projection != null && projection != undefined) {
            val point = projection.fromLatLngToDivPixel(position)
            if (point != null && point != undefined) {
                val style = content.asDynamic().style
                style.left = "${num(point.x)}px"
                style.top = "${num(point.y)}px"
            }
        }
    }
    overlay.onRemove = {
        setMapMarkerShown(content, false)
        val node = content.asDynamic()
        val generation = num(node.__eventsMapMarkerGeneration).toInt()
        window.asDynamic().setTimeout({
            if (num(node.__eventsMapMarkerGeneration).toInt() == generation) node.remove()
        }, 240)
    }
    overlay.setMap(map)
    return overlay
}

private fun googleOverlay(): dynamic {
    val ctor = if (isJsFunction(mapsApi.OverlayView)) mapsApi.OverlayView else window.asDynamic().google.maps.OverlayView
    return js("new ctor()")
}

private fun googleBounds(): dynamic {
    val ctor = if (isJsFunction(mapsApi.LatLngBounds)) mapsApi.LatLngBounds else window.asDynamic().google.maps.LatLngBounds
    return js("new ctor()")
}

private fun saigonShiftedDate(millis: Long): dynamic {
    val at = millis.toDouble() + SAIGON_OFFSET.toDouble()
    return js("new Date(at)")
}

private fun eventDayHeading(millis: Long): String {
    val now = num(js("Date.now()")).toLong()
    val day = saigonDay(millis)
    return when (day) {
        saigonDay(now) -> Town.t("today")
        saigonDay(now + 24 * 60 * 60 * 1000L) -> Town.t("tomorrow")
        else -> formatEventDate(millis)
    }
}

private fun saigonDay(millis: Long): String {
    val shifted = saigonShiftedDate(millis)
    val year = num(shifted.getUTCFullYear()).toInt()
    val month = num(shifted.getUTCMonth()).toInt() + 1
    val day = num(shifted.getUTCDate()).toInt()
    return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

private fun formatEventClock(millis: Long): String {
    val shifted = saigonShiftedDate(millis)
    val hour = num(shifted.getUTCHours()).toInt()
    val minute = num(shifted.getUTCMinutes()).toInt()
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

private fun formatEventTime(millis: Long): String = "${formatEventDate(millis)} ${formatEventClock(millis)}"

private fun formatEventDate(millis: Long): String {
    val shifted = saigonShiftedDate(millis)
    val month = num(shifted.getUTCMonth()).toInt() + 1
    val day = num(shifted.getUTCDate()).toInt()
    return "$day/$month"
}

private fun readPosts(array: dynamic): List<PostCard> {
    val posts = mutableListOf<PostCard>()
    if (array == null || array == undefined) return posts
    val length = array.length as Int
    for (i in 0 until length) posts += postFrom(array[i])
    return posts
}

private fun readUserLocations(array: dynamic): List<PlacePick> {
    val locations = mutableListOf<PlacePick>()
    if (array == null || array == undefined) return locations
    val length = array.length as? Int ?: return locations
    for (i in 0 until length) {
        val place = array[i]
        val lat = if (place.lat == null || place.lat == undefined) null else num(place.lat)
        val lng = if (place.lng == null || place.lng == undefined) null else num(place.lng)
        val postCount = if (place.postCount == null || place.postCount == undefined) 0L else num(place.postCount).toLong()
        if (lat != null && lng != null) {
            locations += PlacePick(
                name = (place.name as? String).orEmpty(),
                placeId = (place.placeId as? String)?.takeIf { it.isNotBlank() },
                lat = lat,
                lng = lng,
                id = (place.id as? String)?.takeIf { it.isNotBlank() },
                postCount = postCount,
                photoUrl = (place.photoUrl as? String)?.takeIf { it.isNotBlank() },
                hasActivities = place.hasActivities == true,
                activityCount = if (place.activityCount == null || place.activityCount == undefined) 0L else num(place.activityCount).toLong(),
                activityEmojis = readStringList(place.activityEmojis),
            )
        }
    }
    return locations
}
