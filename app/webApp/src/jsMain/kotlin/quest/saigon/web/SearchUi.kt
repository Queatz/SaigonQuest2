package quest.saigon.web

import web.html.HTMLElement
import web.window.window

internal fun postSearchScreen(
    router: HistoryRouter,
    onOpen: (PostCard, List<PostCard>, String, String?) -> Unit,
): HTMLElement {
    val body = el("div", "page-body post-search-body")
    val input = el("input", "post-search-input")
    input.setAttribute("type", "search")
    input.setAttribute("placeholder", Town.t("searchPosts"))
    input.setAttribute("aria-label", Town.t("search"))
    input.setAttribute("autocomplete", "off")
    val grid = el("div", "location-sheet-grid post-search-grid")
    body.appendChild(input)
    body.appendChild(grid)

    var cursor: String? = null
    var activeQuery = ""
    val searchPosts = mutableListOf<PostCard>()
    var loading = false
    var done = false
    var request = 0
    var debounceTimer: dynamic = null

    fun load(reset: Boolean = false) {
        if (reset) {
            request += 1
            loading = false
            done = false
            cursor = null
            searchPosts.clear()
            activeQuery = (input.asDynamic().value as? String).orEmpty().trim()
            grid.clear()
        }
        if (loading || done) return
        loading = true
        val token = ++request
        val query = activeQuery
        val next = cursor
        val more = if (next.isNullOrBlank()) "" else "&cursor=${encodeQuery(next)}"
        http("GET", "/api/posts/search?q=${encodeQuery(query)}&limit=24$more") { status, text ->
            if (token != request) return@http
            loading = false
            if (status != 200) {
                if (grid.childElementCount == 0) grid.appendChild(el("p", "hint", text = Town.t("unavailable")))
                done = true
                return@http
            }
            val parsed = parseJson(text)
            val posts = readLocationPosts(parsed.posts)
            searchPosts.addAll(posts)
            if (posts.isEmpty() && grid.childElementCount == 0) {
                grid.appendChild(el("p", "hint", text = Town.t("noSearchResults")))
            } else {
                posts.forEach { post ->
                    grid.appendChild(
                        locationPostTile(
                            post,
                            router,
                            onClick = { onOpen(post, searchPosts.toList(), activeQuery, cursor) },
                            showMessage = true,
                        ),
                    )
                }
            }
            cursor = parsed.nextCursor as? String
            if (cursor.isNullOrBlank()) done = true
        }
    }

    body.asDynamic().addEventListener("scroll", {
        val top = num(body.asDynamic().scrollTop)
        val height = num(body.asDynamic().clientHeight)
        val full = num(body.asDynamic().scrollHeight)
        if (top + height > full - 80) load()
    })
    input.asDynamic().oninput = {
        if (debounceTimer != null && debounceTimer != undefined) window.asDynamic().clearTimeout(debounceTimer)
        debounceTimer = window.asDynamic().setTimeout({
            debounceTimer = null
            load(reset = true)
        }, 220)
    }
    load()
    return body
}
