package quest.saigon.web

import web.dom.Element
import web.dom.document
import web.html.HTMLElement
import web.window.window
import kotlin.math.round

private var statsTimer: dynamic = null
private var statsGeneration = 0

internal fun clearStatsPoll() {
    if (statsTimer != null && statsTimer != undefined) {
        window.asDynamic().clearInterval(statsTimer)
    }
    statsTimer = null
    statsGeneration += 1
}

internal fun statsScreen(router: HistoryRouter): HTMLElement {
    val generation = statsGeneration
    val shell = el("section", "shell page-shell stats-shell")
    shell.appendChild(pageHead(router, Town.t("stats"), showProfile = false))
    val body = el("div", "page-body stats-body")
    val live = el("div", "stats-live")
    live.appendChild(el("i"))
    live.appendChild(el("span", text = Town.t("statsLive")))
    val hint = el("p", "stats-hint", text = Town.t("statsHint"))
    val ranges = el("div", "stats-ranges")
    val chartHost = el("div", "stats-chart")
    val legend = el("div", "stats-legend")
    val today = el("div", "stats-today")
    val allTime = el("div", "stats-today")
    body.appendChild(live)
    body.appendChild(hint)
    body.appendChild(ranges)
    body.appendChild(chartHost)
    body.appendChild(legend)
    body.appendChild(el("h2", "stats-today-title", text = Town.t("statsToday")))
    body.appendChild(today)
    body.appendChild(el("h2", "stats-today-title", text = Town.t("statsAllTime")))
    body.appendChild(allTime)
    shell.appendChild(body)

    var days = emptyList<DayPoint>()
    var totals = AllTimeTotals()
    var rangeDays = 30
    var hoverX: Double? = null
    val hidden = mutableSetOf<String>()
    val series = seriesDefs()

    fun visibleDays(): List<DayPoint> {
        if (days.isEmpty()) return days
        return days.takeLast(rangeDays.coerceIn(1, days.size))
    }

    fun paint() {
        paintRanges(ranges, rangeDays) { next ->
            rangeDays = next
            paint()
        }
        paintLegend(legend, series, hidden) { key ->
            if (key in hidden) {
                hidden.remove(key)
            } else if (hidden.size < series.size - 1) {
                hidden.add(key)
            }
            paint()
        }
        val restoreHoverX = hoverX
        paintChart(chartHost, visibleDays(), series.filter { it.key !in hidden }, restoreHoverX) { x ->
            hoverX = x
        }
        paintToday(today, days.lastOrNull(), series)
        paintAllTime(allTime, totals)
    }

    fun load() {
        http("GET", "/api/stats") { status, body ->
            if (generation != statsGeneration) return@http
            if (status != 200) {
                chartHost.clear()
                chartHost.appendChild(el("p", "stats-error", text = errorText(body, status)))
                return@http
            }
            val json = parseJson(body)
            days = readDays(json)
            totals = readTotals(json)
            paint()
        }
    }

    paint()
    load()
    statsTimer = window.asDynamic().setInterval({ load() }, 4000)
    return shell
}

private data class DayPoint(
    val day: String,
    val visitorVisits: Long,
    val userVisits: Long,
    val uniqueUserVisits: Long,
    val posts: Long,
    val newUsers: Long,
    val replyClicks: Long,
    val shareClicks: Long,
    val postViews: Long,
)

private data class SeriesDef(
    val key: String,
    val color: String,
    val label: String,
    val value: (DayPoint) -> Long,
)

private data class AllTimeTotals(
    val totalUsers: Long = 0,
    val totalPosts: Long = 0,
)

private fun seriesDefs() = listOf(
    SeriesDef("visitorVisits", "#8aa2b4", Town.t("visitorVisits")) { it.visitorVisits },
    SeriesDef("userVisits", "#e9897c", Town.t("userVisits")) { it.userVisits },
    SeriesDef("uniqueUserVisits", "#d4a017", Town.t("uniqueUserVisits")) { it.uniqueUserVisits },
    SeriesDef("posts", "#5b8c5a", Town.t("postsPerDay")) { it.posts },
    SeriesDef("newUsers", "#6b5ea8", Town.t("newUsersPerDay")) { it.newUsers },
    SeriesDef("replyClicks", "#2a9d8f", Town.t("repliesClicked")) { it.replyClicks },
    SeriesDef("shareClicks", "#e76f51", Town.t("sharesClicked")) { it.shareClicks },
    SeriesDef("postViews", "#457b9d", Town.t("postViews")) { it.postViews },
)

private fun readTotals(node: dynamic) = AllTimeTotals(
    totalUsers = num(node.totalUsers).toLong(),
    totalPosts = num(node.totalPosts).toLong(),
)

private fun readDays(node: dynamic): List<DayPoint> {
    val days = node.days ?: return emptyList()
    val n = days.length as Int
    return (0 until n).map { i ->
        val d = days[i]
        DayPoint(
            day = (d.day as? String).orEmpty(),
            visitorVisits = num(d.visitorVisits).toLong(),
            userVisits = num(d.userVisits).toLong(),
            uniqueUserVisits = num(d.uniqueUserVisits).toLong(),
            posts = num(d.posts).toLong(),
            newUsers = num(d.newUsers).toLong(),
            replyClicks = num(d.replyClicks).toLong(),
            shareClicks = num(d.shareClicks).toLong(),
            postViews = num(d.postViews).toLong(),
        )
    }
}

private fun paintRanges(host: HTMLElement, selected: Int, onSelect: (Int) -> Unit) {
    host.clear()
    listOf(7 to Town.t("statsRange7"), 30 to Town.t("statsRange30")).forEach { (days, label) ->
        host.appendChild(el("button", if (days == selected) "active" else null, text = label) { button ->
            button.setAttribute("type", "button")
            button.onClick { onSelect(days) }
        })
    }
}

private fun paintLegend(
    host: HTMLElement,
    series: List<SeriesDef>,
    hidden: Set<String>,
    onToggle: (String) -> Unit,
) {
    host.clear()
    series.forEach { item ->
        val off = item.key in hidden
        host.appendChild(el("button", if (off) "off" else null) { button ->
            button.setAttribute("type", "button")
            button.appendChild(el("i", "swatch") { swatch ->
                swatch.style.background = item.color
            })
            button.appendChild(el("span", text = item.label))
            button.onClick { onToggle(item.key) }
        })
    }
}

private fun paintToday(host: HTMLElement, day: DayPoint?, series: List<SeriesDef>) {
    host.clear()
    if (day == null) return
    series.forEach { item ->
        val card = el("div", "stats-card")
        card.appendChild(el("small", text = item.label))
        card.appendChild(el("b", text = item.value(day).toString()))
        host.appendChild(card)
    }
}

private fun paintAllTime(host: HTMLElement, totals: AllTimeTotals) {
    host.clear()
    listOf(
        Town.t("totalUsers") to totals.totalUsers,
        Town.t("totalPosts") to totals.totalPosts,
    ).forEach { (label, value) ->
        val card = el("div", "stats-card")
        card.appendChild(el("small", text = label))
        card.appendChild(el("b", text = value.toString()))
        host.appendChild(card)
    }
}

private const val VW = 360.0
private const val VH = 220.0
private const val PL = 36.0
private const val PR = 10.0
private const val PT = 16.0
private const val PB = 28.0

private fun paintChart(
    host: HTMLElement,
    days: List<DayPoint>,
    series: List<SeriesDef>,
    restoreHoverX: Double? = null,
    onHover: (Double?) -> Unit = {},
) {
    host.clear()
    if (days.isEmpty() || series.isEmpty()) return
    val svg = svgEl("svg")
    svg.setAttribute("class", "stats-svg")
    svg.setAttribute("role", "img")
    svg.setAttribute("aria-label", Town.t("stats"))
    host.appendChild(svg)

    val bounds = svg.getBoundingClientRect()
    val width = num(bounds.asDynamic().width).let { if (it > 1.0) it else VW }
    val height = num(bounds.asDynamic().height).let { if (it > 1.0) it else VH }
    svg.setAttribute("viewBox", "0 0 $width $height")
    svg.setAttribute("preserveAspectRatio", "none")
    val innerW = width - PL - PR
    val innerH = height - PT - PB
    val maxY = series.maxOf { item -> days.maxOf { item.value(it) } }.toDouble().coerceAtLeast(1.0)

    for (g in 0..4) {
        val y = PT + innerH * g / 4.0
        val line = svgEl("line")
        line.setAttribute("x1", PL.toString())
        line.setAttribute("x2", (width - PR).toString())
        line.setAttribute("y1", y.toString())
        line.setAttribute("y2", y.toString())
        line.setAttribute("class", "stats-grid")
        svg.appendChild(line)
        val label = svgEl("text")
        label.setAttribute("x", "4")
        label.setAttribute("y", (y + 3).toString())
        label.setAttribute("class", "stats-axis")
        label.textContent = formatAxis(maxY * (4 - g) / 4.0)
        svg.appendChild(label)
    }

    fun xAt(i: Int): Double {
        if (days.size == 1) return PL + innerW / 2
        return PL + innerW * i / (days.size - 1)
    }

    fun yAt(value: Long): Double = PT + innerH * (1 - value / maxY)

    series.forEach { item ->
        val path = svgEl("path")
        path.setAttribute(
            "d",
            days.mapIndexed { i, day ->
                val cmd = if (i == 0) "M" else "L"
                "$cmd${xAt(i)} ${yAt(item.value(day))}"
            }.joinToString(" "),
        )
        path.setAttribute("fill", "none")
        path.setAttribute("stroke", item.color)
        path.setAttribute("stroke-width", "2.2")
        path.setAttribute("stroke-linejoin", "round")
        path.setAttribute("stroke-linecap", "round")
        svg.appendChild(path)
    }

    val labelEvery = when {
        days.size <= 7 -> 1
        days.size <= 14 -> 2
        else -> 5
    }
    days.forEachIndexed { i, day ->
        if (i % labelEvery == 0 || i == days.lastIndex) {
            val text = svgEl("text")
            text.setAttribute("x", xAt(i).toString())
            text.setAttribute("y", (height - 8).toString())
            text.setAttribute("text-anchor", "middle")
            text.setAttribute("class", "stats-axis")
            text.textContent = shortDay(day.day)
            svg.appendChild(text)
        }
    }

    val hoverLine = svgEl("line")
    hoverLine.setAttribute("class", "stats-hover-line")
    hoverLine.setAttribute("y1", PT.toString())
    hoverLine.setAttribute("y2", (PT + innerH).toString())
    svg.appendChild(hoverLine)

    val dots = series.map { item ->
        val circle = svgEl("circle")
        circle.setAttribute("class", "stats-hover-dot")
        circle.setAttribute("r", "4")
        circle.setAttribute("fill", item.color)
        svg.appendChild(circle)
        item to circle
    }

    val overlay = svgEl("rect")
    overlay.setAttribute("x", "0")
    overlay.setAttribute("y", "0")
    overlay.setAttribute("width", width.toString())
    overlay.setAttribute("height", height.toString())
    overlay.setAttribute("fill", "transparent")
    svg.appendChild(overlay)

    val tooltip = el("div", "stats-tooltip")
    tooltip.asDynamic().hidden = true
    host.appendChild(tooltip)

    fun pointerX(clientX: Double): Double {
        val rect = svg.getBoundingClientRect()
        val left = num(rect.asDynamic().left)
        val boxWidth = num(rect.asDynamic().width).let { if (it > 0.0) it else width }
        return ((clientX - left) / boxWidth * width)
    }

    fun show(idx: Int, eventX: Double) {
        val x = xAt(idx)
        hoverLine.setAttribute("x1", x.toString())
        hoverLine.setAttribute("x2", x.toString())
        dots.forEach { (item, circle) ->
            circle.setAttribute("cx", x.toString())
            circle.setAttribute("cy", yAt(item.value(days[idx])).toString())
        }
        val day = days[idx]
        tooltip.clear()
        tooltip.appendChild(el("strong", text = day.day))
        series.forEach { item ->
            val row = el("div", "stats-tip-row")
            row.appendChild(el("i", "swatch") { swatch ->
                swatch.style.background = item.color
            })
            row.appendChild(el("span", text = "${item.label}: ${item.value(day)}"))
            tooltip.appendChild(row)
        }
        tooltip.asDynamic().hidden = false
        val hostRect = host.getBoundingClientRect()
        val hostLeft = num(hostRect.asDynamic().left)
        val hostWidth = num(hostRect.asDynamic().width).let { if (it > 0.0) it else width }
        val left = (eventX - hostLeft).coerceIn(8.0, hostWidth - 8.0)
        tooltip.style.left = "${left}px"
        tooltip.style.transform = if (idx > days.size / 2) "translateX(-108%)" else "translateX(8px)"
        host.asDynamic().classList.add("is-hovering")
        onHover(eventX)
    }

    fun hide() {
        host.asDynamic().classList.remove("is-hovering")
        tooltip.asDynamic().hidden = true
        onHover(null)
    }

    overlay.asDynamic().onmousemove = { event: dynamic ->
        val clientX = num(event.clientX)
        show(nearestIndex(pointerX(clientX), days.size, width), clientX)
    }
    overlay.asDynamic().onmouseleave = { hide() }
    overlay.asDynamic().ontouchmove = handler@{ event: dynamic ->
        val touch = event.touches[0] ?: return@handler
        event.preventDefault()
        val clientX = num(touch.clientX)
        show(nearestIndex(pointerX(clientX), days.size, width), clientX)
    }
    restoreHoverX?.let { clientX ->
        show(nearestIndex(pointerX(clientX), days.size, width), clientX)
    }
}

private fun nearestIndex(px: Double, count: Int, width: Double): Int {
    if (count <= 1) return 0
    val t = ((px - PL) / (width - PL - PR)).coerceIn(0.0, 1.0)
    return round(t * (count - 1)).toInt()
}

private fun formatAxis(value: Double): String {
    val n = round(value)
    return if (n >= 1000) "${(n / 1000).toInt()}k" else n.toInt().toString()
}

private fun shortDay(day: String): String {
    val parts = day.split("-")
    if (parts.size < 3) return day
    return "${parts[2]}/${parts[1]}"
}

private fun svgEl(tag: String): Element =
    document.createElementNS("http://www.w3.org/2000/svg", tag)
