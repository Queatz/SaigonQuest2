package quest.saigon.web

import quest.saigon.WantSpaceLayout
import quest.saigon.WantSpacePosition
import web.dom.document
import web.html.HTMLElement
import web.window.window
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal class WantsSpaceOverlay(
    private val host: HTMLElement,
    private val router: HistoryRouter,
    explode: Boolean,
    private val pushHistoryEntry: Boolean = true,
    private val onClosed: () -> Unit,
) {
    private val root = el("div", if (explode) "wants-space is-entering" else "wants-space is-active")
    private val starsLayer = el("div", "wants-stars")
    private val viewport = el("div", "wants-viewport")
    private val world = el("div", "wants-world")
    private val statusBox = el("div", "wants-status")
    private val explosionLayer = el("div", "wants-explosion")

    private val timers = mutableListOf<dynamic>()
    private var rafId: dynamic = null
    private var closed = false
    private var removeHistoryEntry: (() -> Unit)? = null

    private var panX = 0.0
    private var panY = 0.0
    private var dragging = false
    private var panned = false
    private var suppressClick = false
    private var startX = 0.0
    private var startY = 0.0
    private var lastX = 0.0
    private var lastY = 0.0
    private var lastTime = 0.0
    private var velX = 0.0
    private var velY = 0.0
    private var activePointerId: dynamic = null

    private data class StarNode(
        val el: HTMLElement,
        val baseXFrac: Double,
        val baseYFrac: Double,
        val depth: Double,
    )

    private data class FloatingCard(
        var data: WantCardData,
        val pos: WantSpacePosition,
        val wrapper: HTMLElement,
        val inner: HTMLElement,
    )

    private val stars = mutableListOf<StarNode>()
    private val cards = mutableListOf<FloatingCard>()
    private var startTimeMs = perfNow()
    private lateinit var keyHandler: (dynamic) -> Unit

    init {
        starsLayer.setAttribute("aria-hidden", "true")
        explosionLayer.setAttribute("aria-hidden", "true")
        root.appendChild(starsLayer)
        viewport.appendChild(world)
        root.appendChild(viewport)
        root.appendChild(statusBox)
        if (explode) root.appendChild(explosionLayer)

        populateStars()
        if (explode) {
            populateExplosion()
            host.asDynamic().classList.add("is-exploding-to-wants")
            timers.add(window.asDynamic().setTimeout({
                if (!closed) {
                    host.asDynamic().classList.add("wants-feed-hidden")
                    root.asDynamic().classList.remove("is-entering")
                    root.asDynamic().classList.add("is-active")
                }
            }, 480))
            timers.add(window.asDynamic().setTimeout({
                explosionLayer.asDynamic().remove()
            }, 820))
        } else {
            host.asDynamic().classList.add("wants-feed-hidden")
        }

        host.appendChild(root)
        if (pushHistoryEntry) {
            removeHistoryEntry = pushOverlayHistory(
                url = "/wants",
                onPop = { close(updateHistory = false) },
                onRouteChange = { destroyImmediate() },
            )
        }

        bindInteractions()
        loadWants()
        startFrameLoop()
    }

    fun destroyForRouteChange() {
        destroyImmediate()
    }

    fun close(updateHistory: Boolean = false) {
        if (closed) return
        closed = true
        cleanupTimersAndListeners()
        if (updateHistory) {
            removeHistoryEntry?.let { popOverlayHistory(it) }
            removeHistoryEntry = null
        } else {
            removeHistoryEntry?.invoke()
            removeHistoryEntry = null
        }
        host.asDynamic().classList.remove("is-exploding-to-wants")
        host.asDynamic().classList.remove("wants-feed-hidden")
        host.asDynamic().classList.add("wants-feed-returning")
        root.asDynamic().classList.add("is-leaving")
        window.asDynamic().setTimeout({
            root.asDynamic().remove()
            host.asDynamic().classList.remove("wants-feed-returning")
            onClosed()
        }, 280)
    }

    private fun destroyImmediate() {
        if (closed) return
        closed = true
        cleanupTimersAndListeners()
        removeHistoryEntry?.invoke()
        removeHistoryEntry = null
        host.asDynamic().classList.remove("is-exploding-to-wants")
        host.asDynamic().classList.remove("wants-feed-hidden")
        host.asDynamic().classList.remove("wants-feed-returning")
        root.asDynamic().remove()
        onClosed()
    }

    private fun cleanupTimersAndListeners() {
        timers.forEach { window.asDynamic().clearTimeout(it) }
        timers.clear()
        if (rafId != null) {
            window.asDynamic().cancelAnimationFrame(rafId)
            rafId = null
        }
        document.asDynamic().removeEventListener("keydown", keyHandler)
    }

    private fun populateStars() {
        val colors = arrayOf("#ffffff", "#fff6c8", "#d7e6ff", "#ffd6ce")
        var seed = 1337L
        fun nextRand(): Double {
            seed = (seed * 1664525L + 1013904223L) and 0x7FFFFFFFL
            return seed.toDouble() / 0x7FFFFFFFL.toDouble()
        }
        for (i in 0 until 64) {
            val size = 1.6 + nextRand() * 2.4
            val sparkle = i % 9 == 0
            val star = el("span", if (sparkle) "wants-star is-sparkle" else "wants-star")
            val xFrac = nextRand()
            val yFrac = nextRand()
            val depth = 0.08 + nextRand() * 0.22
            val duration = 2.0 + nextRand() * 3.2
            val delay = -nextRand() * 4.0
            val color = colors[i % colors.size]
            val st = star.asDynamic().style
            st.width = "${if (sparkle) size * 2.5 else size}px"
            st.height = "${if (sparkle) size * 2.5 else size}px"
            st.backgroundColor = color
            st.color = color
            st.animationDuration = "${duration}s"
            st.animationDelay = "${delay}s"
            starsLayer.appendChild(star)
            stars += StarNode(star, xFrac, yFrac, depth)
        }
    }

    private fun populateExplosion() {
        val flash = el("div", "wants-explosion-flash")
        val ring = el("div", "wants-explosion-ring")
        explosionLayer.appendChild(flash)
        explosionLayer.appendChild(ring)

        val colors = arrayOf("#fff6c8", "#e9897c", "#7f7ce9", "#ffffff")
        for (i in 0 until 36) {
            val spark = el("span", "wants-explosion-spark")
            val angle = (i.toDouble() / 36.0) * (PI * 2.0) + ((i % 3) - 1) * 0.09
            val dist = 120.0 + ((i * 73) % 240)
            val size = 4.0 + ((i * 19) % 6)
            val tx = cos(angle) * dist
            val ty = sin(angle) * dist
            val st = spark.asDynamic().style
            st.width = "${size}px"
            st.height = "${size}px"
            st.backgroundColor = colors[i % colors.size]
            st.setProperty("--tx", "${tx}px")
            st.setProperty("--ty", "${ty}px")
            st.animationDelay = "${(i % 5) * 18}ms"
            explosionLayer.appendChild(spark)
        }
    }

    private fun bindInteractions() {
        viewport.asDynamic().addEventListener("pointerdown", { event: dynamic ->
            if (dialogOpen() || event.button != 0) return@addEventListener
            if (event.target.closest("button") != null) return@addEventListener
            dragging = true
            panned = false
            suppressClick = false
            velX = 0.0
            velY = 0.0
            startX = num(event.clientX)
            startY = num(event.clientY)
            lastX = startX
            lastY = startY
            lastTime = num(event.timeStamp)
            activePointerId = event.pointerId
        })
        viewport.asDynamic().addEventListener("pointermove", { event: dynamic ->
            if (!dragging) return@addEventListener
            val x = num(event.clientX)
            val y = num(event.clientY)
            if (!panned) {
                if (hypot(x - startX, y - startY) < 7.0) return@addEventListener
                panned = true
                suppressClick = true
                viewport.asDynamic().classList.add("is-panning")
                if (viewport.asDynamic().setPointerCapture != undefined && activePointerId != null) {
                    try {
                        viewport.asDynamic().setPointerCapture(activePointerId)
                    } catch (_: Throwable) {
                    }
                }
            }
            val time = num(event.timeStamp)
            val dt = (time - lastTime).coerceAtLeast(1.0)
            val dx = x - lastX
            val dy = y - lastY
            panX += dx
            panY += dy
            velX = dx / dt * 16.0
            velY = dy / dt * 16.0
            lastX = x
            lastY = y
            lastTime = time
            event.preventDefault()
        })
        val endPointer = { _: dynamic ->
            if (dragging) {
                dragging = false
                activePointerId = null
                viewport.asDynamic().classList.remove("is-panning")
                if (suppressClick) {
                    timers.add(window.asDynamic().setTimeout({ suppressClick = false }, 80))
                }
            }
        }
        viewport.asDynamic().addEventListener("pointerup", endPointer)
        viewport.asDynamic().addEventListener("pointercancel", endPointer)

        viewport.asDynamic().addEventListener("wheel", { event: dynamic ->
            if (dialogOpen()) return@addEventListener
            event.preventDefault()
            velX = 0.0
            velY = 0.0
            panX -= num(event.deltaX)
            panY -= num(event.deltaY)
        }, js("({passive:false})"))

        keyHandler = { event ->
            if (!closed && !dialogOpen()) {
                val step = 56.0
                when (event.key as? String) {
                    "ArrowLeft" -> {
                        panX += step
                        event.preventDefault()
                    }
                    "ArrowRight" -> {
                        panX -= step
                        event.preventDefault()
                    }
                    "ArrowUp" -> {
                        panY += step
                        event.preventDefault()
                    }
                    "ArrowDown" -> {
                        panY -= step
                        event.preventDefault()
                    }
                }
            }
        }
        document.asDynamic().addEventListener("keydown", keyHandler)
    }

    private fun loadWants() {
        statusBox.clear()
        statusBox.asDynamic().hidden = false
        statusBox.appendChild(el("span", "avatar-spinner wants-spinner"))
        http("GET", "/api/wants") { status, body ->
            if (closed || root.asDynamic().isConnected == false) return@http
            if (status != 200) {
                statusBox.clear()
                statusBox.appendChild(el("p", "hint wants-empty", text = Town.t("unavailable")))
                return@http
            }
            val parsed = parseJson(body).wants
            val count = parsed?.length as? Int ?: 0
            if (count == 0) {
                statusBox.clear()
                statusBox.appendChild(el("p", "hint wants-empty", text = Town.t("noWants")))
                return@http
            }
            statusBox.clear()
            statusBox.asDynamic().hidden = true

            val items = mutableListOf<WantCardData>()
            for (i in 0 until count) {
                items += parseWantCardData(parsed[i])
            }
            renderWants(items)
        }
    }

    private fun renderWants(items: List<WantCardData>) {
        world.clear()
        cards.clear()
        val layout = WantSpaceLayout.layout(items.map { it.id })
        items.forEachIndexed { index, item ->
            val pos = layout[item.id] ?: return@forEachIndexed
            val wrapper = el("div", "want-floating-card")
            val inner = el("div", "want-floating-inner")
            val card = buildFloatingCard(item)
            inner.appendChild(card)
            wrapper.appendChild(inner)
            world.appendChild(wrapper)

            val floating = FloatingCard(item, pos, wrapper, inner)
            cards += floating

            val delayMs = WantSpaceLayout.animateInDelayMs(index, items.size)
            timers.add(window.asDynamic().setTimeout({
                if (!closed) inner.asDynamic().classList.add("is-entered")
            }, delayMs.toInt()))
        }
        paintPositions(perfNow())
    }

    private fun buildFloatingCard(item: WantCardData): HTMLElement {
        lateinit var replaceCard: (WantCardData) -> Unit
        fun make(data: WantCardData): HTMLElement = wantNotebookCardElement(
            host = host,
            want = data,
            router = router,
            leaveOnly = false,
            extraClass = "want-space-card",
            onUpdated = { next -> replaceCard(next) },
            onRequireAuth = { loadWants() },
            canClickCard = { !suppressClick },
        )
        var currentEl = make(item)
        replaceCard = { next ->
            val target = cards.firstOrNull { it.data.id == next.id }
            if (target != null) {
                target.data = next
                val fresh = make(next)
                currentEl.asDynamic().replaceWith(fresh)
                currentEl = fresh
            }
        }
        return currentEl
    }

    private fun startFrameLoop() {
        startTimeMs = perfNow()
        fun tick() {
            if (closed) return
            val now = perfNow()
            if (!dragging && (abs(velX) > 0.1 || abs(velY) > 0.1)) {
                panX += velX
                panY += velY
                velX *= 0.92
                velY *= 0.92
            }
            paintPositions(now)
            rafId = window.asDynamic().requestAnimationFrame { tick() }
        }
        rafId = window.asDynamic().requestAnimationFrame { tick() }
    }

    private fun paintPositions(nowMs: Double) {
        val w = num(root.asDynamic().clientWidth).takeIf { it > 40 } ?: 400.0
        val h = num(root.asDynamic().clientHeight).takeIf { it > 40 } ?: 720.0
        val t = (nowMs - startTimeMs) / 1000.0
        val reducedMotion = prefersReducedMotion()

        stars.forEach { s ->
            val rawX = s.baseXFrac * w + panX * s.depth
            val rawY = s.baseYFrac * h + panY * s.depth
            val x = ((rawX % w) + w) % w
            val y = ((rawY % h) + h) % h
            s.el.asDynamic().style.transform = "translate3d(${x}px, ${y}px, 0)"
        }

        cards.forEach { card ->
            val p = card.pos
            val dx = if (reducedMotion) 0.0 else sin(t * p.floatSpeedX + p.floatPhaseX) * p.floatAmpX
            val dy = if (reducedMotion) 0.0 else cos(t * p.floatSpeedY + p.floatPhaseY) * p.floatAmpY
            val tilt = if (reducedMotion) 0.0 else p.tiltDeg
            val tx = panX + p.x + dx
            val ty = panY + p.y + dy
            card.wrapper.asDynamic().style.transform =
                "translate3d(calc(-50% + ${tx}px), calc(-50% + ${ty}px), 0) rotate(${tilt}deg)"
        }
    }

    private fun dialogOpen(): Boolean {
        val dialog = document.asDynamic().querySelector(".dialog-backdrop")
        return dialog != null && dialog != undefined
    }

    private fun prefersReducedMotion(): Boolean = try {
        window.asDynamic().matchMedia("(prefers-reduced-motion: reduce)").matches == true
    } catch (_: Throwable) {
        false
    }

    private fun perfNow(): Double = num(window.asDynamic().performance.now())
}
