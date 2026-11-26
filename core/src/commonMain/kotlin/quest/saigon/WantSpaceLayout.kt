package quest.saigon

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class WantSpacePosition(
    val x: Double,
    val y: Double,
    val floatPhaseX: Double,
    val floatPhaseY: Double,
    val floatSpeedX: Double,
    val floatSpeedY: Double,
    val floatAmpX: Double,
    val floatAmpY: Double,
    val tiltDeg: Double,
)

data class ZoomInertiaStep(
    val zoom: Double,
    val velocity: Double,
)

object WantSpaceLayout {
    const val ANIMATE_IN_WINDOW_MS = 2000L
    const val ANIMATE_IN_ITEM_MS = 350L
    const val CARD_WIDTH_PX = 264.0
    const val CARD_HEIGHT_PX = 112.0
    const val MIN_ZOOM = 0.25
    const val MAX_ZOOM = 1.5
    const val HUE_SHIFT_DEG_PER_SEC = 8.0
    const val ZOOM_VELOCITY_DECAY = 0.86
    const val ZOOM_RELEASE_GAIN = 0.65
    const val MAX_ZOOM_LOG_VEL = 0.045
    const val MIN_ZOOM_LOG_VEL = 0.0004
    const val ZOOM_IDLE_CUTOFF_MS = 140.0

    fun clampZoom(zoom: Double): Double = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    fun updateZoomVelocity(
        currentVelocity: Double,
        zoomFactor: Double,
        dtMs: Double,
    ): Double {
        if (zoomFactor <= 0.0 || !zoomFactor.isFinite() || dtMs <= 0.0) return currentVelocity
        val frames = (dtMs / 16.0).coerceAtLeast(0.25)
        val instVel = (ln(zoomFactor) / frames) * ZOOM_RELEASE_GAIN
        return (currentVelocity * 0.4 + instVel * 0.6).coerceIn(-MAX_ZOOM_LOG_VEL, MAX_ZOOM_LOG_VEL)
    }

    fun wheelZoomVelocity(
        currentVelocity: Double,
        totalLogDelta: Double,
    ): Double {
        if (!totalLogDelta.isFinite() || totalLogDelta == 0.0) return currentVelocity
        val impulse = totalLogDelta * 0.55 * (1.0 - ZOOM_VELOCITY_DECAY)
        return (currentVelocity + impulse).coerceIn(-MAX_ZOOM_LOG_VEL, MAX_ZOOM_LOG_VEL)
    }

    fun releaseZoomVelocity(
        currentVelocity: Double,
        idleMs: Double,
    ): Double {
        if (idleMs >= ZOOM_IDLE_CUTOFF_MS) return 0.0
        if (idleMs <= 0.0) return currentVelocity
        val next = currentVelocity * ZOOM_VELOCITY_DECAY.pow(idleMs / 16.0)
        return if (abs(next) < MIN_ZOOM_LOG_VEL) 0.0 else next
    }

    fun stepZoomInertia(
        zoom: Double,
        velocity: Double,
        dtMs: Double,
    ): ZoomInertiaStep {
        if (abs(velocity) < MIN_ZOOM_LOG_VEL || dtMs <= 0.0) {
            return ZoomInertiaStep(clampZoom(zoom), 0.0)
        }
        val frameScale = (dtMs / 16.0).coerceIn(0.0, 4.0)
        val rawZoom = zoom * exp(velocity * frameScale)
        val nextZoom = clampZoom(rawZoom)
        val hitMin = nextZoom <= MIN_ZOOM && velocity < 0.0
        val hitMax = nextZoom >= MAX_ZOOM && velocity > 0.0
        if (hitMin || hitMax) {
            return ZoomInertiaStep(nextZoom, 0.0)
        }
        val nextVel = velocity * ZOOM_VELOCITY_DECAY.pow(frameScale)
        return ZoomInertiaStep(
            zoom = nextZoom,
            velocity = if (abs(nextVel) < MIN_ZOOM_LOG_VEL) 0.0 else nextVel,
        )
    }

    fun shiftedHue(baseHueDeg: Double, elapsedSec: Double): Double {
        val raw = (baseHueDeg + elapsedSec.coerceAtLeast(0.0) * HUE_SHIFT_DEG_PER_SEC) % 360.0
        return if (raw < 0.0) raw + 360.0 else raw
    }

    fun animateInDelayMs(
        index: Int,
        totalCount: Int,
        totalWindowMs: Long = ANIMATE_IN_WINDOW_MS,
        itemDurationMs: Long = ANIMATE_IN_ITEM_MS,
    ): Long {
        if (totalCount <= 1 || index <= 0) return 0L
        val maxDelay = (totalWindowMs - itemDurationMs).coerceAtLeast(0L)
        val clampedIndex = index.coerceAtMost(totalCount - 1)
        return ((clampedIndex.toDouble() / (totalCount - 1).toDouble()) * maxDelay).toLong()
    }

    fun layout(
        wantIds: List<String>,
        cardWidth: Double = CARD_WIDTH_PX,
        cardHeight: Double = CARD_HEIGHT_PX,
        gapX: Double = 58.0,
        gapY: Double = 54.0,
    ): Map<String, WantSpacePosition> {
        if (wantIds.isEmpty()) return emptyMap()
        val uniqueIds = wantIds.distinct()
        val placed = ArrayList<MutablePoint>(uniqueIds.size)
        val goldenAngle = 2.399963229728653
        val stepX = (cardWidth + gapX) * 0.78
        val stepY = (cardHeight + gapY) * 0.92

        uniqueIds.forEachIndexed { index, id ->
            val seed = hash64(id)
            val jitterAngle = unitFloat(seed, 0) * (PI * 2.0)
            val jitterDist = 18.0 + unitFloat(seed, 8) * 24.0
            if (index == 0) {
                placed += MutablePoint(
                    id = id,
                    seed = seed,
                    x = cos(jitterAngle) * (jitterDist * 0.5),
                    y = sin(jitterAngle) * (jitterDist * 0.4),
                )
            } else {
                val r = sqrt(index.toDouble())
                val angle = index * goldenAngle + (unitFloat(seed, 16) - 0.5) * 0.35
                placed += MutablePoint(
                    id = id,
                    seed = seed,
                    x = cos(angle) * stepX * r + cos(jitterAngle) * jitterDist,
                    y = sin(angle) * stepY * r + sin(jitterAngle) * jitterDist,
                )
            }
        }

        val minSepX = cardWidth + gapX
        val minSepY = cardHeight + gapY
        repeat(60) {
            var moved = false
            for (i in 0 until placed.size) {
                for (j in i + 1 until placed.size) {
                    val a = placed[i]
                    val b = placed[j]
                    val dx = b.x - a.x
                    val dy = b.y - a.y
                    val overlapX = minSepX - abs(dx)
                    val overlapY = minSepY - abs(dy)
                    if (overlapX > 0.0 && overlapY > 0.0) {
                        moved = true
                        if (overlapX / minSepX < overlapY / minSepY) {
                            val dir = if (dx >= 0.0) 1.0 else -1.0
                            val push = overlapX * 0.5 + 1.0
                            if (i == 0) {
                                b.x += dir * push * 2.0
                            } else {
                                a.x -= dir * push
                                b.x += dir * push
                            }
                        } else {
                            val dir = if (dy >= 0.0) 1.0 else -1.0
                            val push = overlapY * 0.5 + 1.0
                            if (i == 0) {
                                b.y += dir * push * 2.0
                            } else {
                                a.y -= dir * push
                                b.y += dir * push
                            }
                        }
                    }
                }
            }
            if (!moved) return@repeat
        }

        val result = LinkedHashMap<String, WantSpacePosition>(placed.size)
        placed.forEach { p ->
            result[p.id] = WantSpacePosition(
                x = p.x,
                y = p.y,
                floatPhaseX = unitFloat(p.seed, 24) * PI * 2.0,
                floatPhaseY = unitFloat(p.seed, 32) * PI * 2.0,
                floatSpeedX = 0.65 + unitFloat(p.seed, 40) * 0.45,
                floatSpeedY = 0.60 + unitFloat(p.seed, 48) * 0.45,
                floatAmpX = 7.0 + unitFloat(p.seed, 52) * 7.0,
                floatAmpY = 8.0 + unitFloat(p.seed, 56) * 8.0,
                tiltDeg = (unitFloat(p.seed, 12) - 0.5) * 2.6,
            )
        }
        return result
    }

    private data class MutablePoint(
        val id: String,
        val seed: ULong,
        var x: Double,
        var y: Double,
    )

    private fun hash64(value: String): ULong {
        var h = 14695981039346656037UL
        for (ch in value) {
            h = h xor ch.code.toULong()
            h *= 1099511628211UL
        }
        h = h xor (h shr 33)
        h *= 0xff51afd7ed558ccdUL
        h = h xor (h shr 33)
        h *= 0xc4ceb9fe1a85ec53UL
        h = h xor (h shr 33)
        return h
    }

    private fun unitFloat(seed: ULong, shift: Int): Double {
        val slice = ((seed shr shift) and 0xFFFFUL).toDouble()
        return slice / 65535.0
    }
}
