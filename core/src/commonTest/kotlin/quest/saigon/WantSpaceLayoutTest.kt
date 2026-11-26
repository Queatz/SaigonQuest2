package quest.saigon

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WantSpaceLayoutTest {
    @Test
    fun animateInDelaysSpanTwoSecondsSequentially() {
        assertEquals(0L, WantSpaceLayout.animateInDelayMs(0, 1))
        assertEquals(0L, WantSpaceLayout.animateInDelayMs(0, 10))
        val delays = (0 until 10).map { WantSpaceLayout.animateInDelayMs(it, 10) }
        for (i in 1 until delays.size) {
            assertTrue(delays[i] > delays[i - 1], "Expected strictly increasing delay at index $i")
        }
        assertEquals(
            WantSpaceLayout.ANIMATE_IN_WINDOW_MS,
            delays.last() + WantSpaceLayout.ANIMATE_IN_ITEM_MS,
        )
    }

    @Test
    fun positionsAreDeterministicAndNonOverlapping() {
        val ids = (1..30).map { "want-$it" }
        val first = WantSpaceLayout.layout(ids)
        val second = WantSpaceLayout.layout(ids)
        assertEquals(first, second)
        assertEquals(30, first.size)

        val w = WantSpaceLayout.CARD_WIDTH_PX
        val h = WantSpaceLayout.CARD_HEIGHT_PX
        val list = first.values.toList()
        for (i in list.indices) {
            for (j in i + 1 until list.size) {
                val dx = abs(list[i].x - list[j].x)
                val dy = abs(list[i].y - list[j].y)
                assertTrue(
                    dx >= w || dy >= h,
                    "Cards $i and $j overlap: dx=$dx, dy=$dy",
                )
            }
        }
        assertTrue(abs(list.first().x) < 40.0 && abs(list.first().y) < 40.0)
    }

    @Test
    fun zoomBoundsAndHueShiftAreBounded() {
        assertEquals(0.25, WantSpaceLayout.clampZoom(0.05))
        assertEquals(1.0, WantSpaceLayout.clampZoom(1.0))
        assertEquals(1.5, WantSpaceLayout.clampZoom(3.2))

        assertEquals(229.0, WantSpaceLayout.shiftedHue(229.0, 0.0), 0.001)
        val mid = WantSpaceLayout.shiftedHue(229.0, 10.0)
        assertEquals(309.0, mid, 0.001)
        val wrapped = WantSpaceLayout.shiftedHue(229.0, 20.0)
        assertEquals(29.0, wrapped, 0.001)
    }

    @Test
    fun zoomInertiaGlidesSmoothlyAndRespectsBoundsAndIdleCutoff() {
        var vel = 0.0
        repeat(5) {
            vel = WantSpaceLayout.updateZoomVelocity(vel, zoomFactor = 1.04, dtMs = 16.0)
        }
        assertTrue(vel > 0.0 && vel <= WantSpaceLayout.MAX_ZOOM_LOG_VEL)

        val releasedPromptly = WantSpaceLayout.releaseZoomVelocity(vel, idleMs = 16.0)
        assertTrue(releasedPromptly > 0.0)
        assertEquals(0.0, WantSpaceLayout.releaseZoomVelocity(vel, idleMs = 200.0))

        var zoom = 1.0
        var coastVel = releasedPromptly
        var frames = 0
        while (coastVel != 0.0 && frames < 120) {
            val step = WantSpaceLayout.stepZoomInertia(zoom, coastVel, dtMs = 16.0)
            assertTrue(step.zoom >= zoom)
            zoom = step.zoom
            coastVel = step.velocity
            frames++
        }
        assertTrue(zoom > 1.02 && zoom <= WantSpaceLayout.MAX_ZOOM)
        assertEquals(0.0, coastVel)

        val clampedHigh = WantSpaceLayout.stepZoomInertia(1.49, 0.04, dtMs = 16.0)
        assertEquals(WantSpaceLayout.MAX_ZOOM, clampedHigh.zoom)
        assertEquals(0.0, clampedHigh.velocity)

        val clampedLow = WantSpaceLayout.stepZoomInertia(0.255, -0.04, dtMs = 16.0)
        assertEquals(WantSpaceLayout.MIN_ZOOM, clampedLow.zoom)
        assertEquals(0.0, clampedLow.velocity)

        val wheelVel = WantSpaceLayout.wheelZoomVelocity(0.0, totalLogDelta = 0.2)
        assertTrue(wheelVel > 0.0 && wheelVel <= WantSpaceLayout.MAX_ZOOM_LOG_VEL)
    }
}
