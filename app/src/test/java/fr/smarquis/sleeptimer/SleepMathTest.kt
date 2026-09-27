package fr.smarquis.sleeptimer

import fr.smarquis.sleeptimer.SleepMath.FADE_STEP_MAX_MILLIS
import fr.smarquis.sleeptimer.SleepMath.FADE_TOTAL_MAX_MILLIS
import fr.smarquis.sleeptimer.SleepMath.ceilMinutes
import fr.smarquis.sleeptimer.SleepMath.fadeStepDelayMillis
import fr.smarquis.sleeptimer.SleepMath.formatCountdown
import fr.smarquis.sleeptimer.SleepMath.isBeforeDeadline
import fr.smarquis.sleeptimer.SleepMath.nextTimeout
import fr.smarquis.sleeptimer.SleepMath.secondsToMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepMathTest {

    private val max = 24 * 60 * 60 * 1000L

    @Test
    fun `fade has no delay without steps`() {
        assertEquals(0L, fadeStepDelayMillis(0))
        assertEquals(0L, fadeStepDelayMillis(-3))
    }

    @Test
    fun `fade uses one step per second for regular volume ranges`() {
        assertEquals(FADE_STEP_MAX_MILLIS, fadeStepDelayMillis(1))
        assertEquals(FADE_STEP_MAX_MILLIS, fadeStepDelayMillis(15))
        assertEquals(FADE_STEP_MAX_MILLIS, fadeStepDelayMillis(25))
    }

    @Test
    fun `fade never exceeds its total duration`() {
        for (steps in 1..1000) assertTrue(steps * fadeStepDelayMillis(steps) <= FADE_TOTAL_MAX_MILLIS)
    }

    @Test
    fun `missing duration`() = assertNull(secondsToMillis(0, max))

    @Test
    fun `duration is converted to millis`() {
        assertEquals(600_000L, secondsToMillis(600, max))
        assertEquals(-60_000L, secondsToMillis(-60, max))
    }

    @Test
    fun `duration is clamped instead of overflowing`() {
        assertEquals(max, secondsToMillis(Long.MAX_VALUE, max))
        assertEquals(-max, secondsToMillis(Long.MIN_VALUE, max))
    }

    @Test
    fun `next timeout applies delta`() {
        assertEquals(15L, nextTimeout(remaining = 10, delta = 5, allowCancel = true, maxMillis = max))
        assertEquals(5L, nextTimeout(remaining = 10, delta = -5, allowCancel = false, maxMillis = max))
    }

    @Test
    fun `next timeout can end the timer only when allowed`() {
        assertEquals(-5L, nextTimeout(remaining = 5, delta = -10, allowCancel = true, maxMillis = max))
        assertEquals(5L, nextTimeout(remaining = 5, delta = -10, allowCancel = false, maxMillis = max))
        assertEquals(5L, nextTimeout(remaining = 5, delta = -5, allowCancel = false, maxMillis = max))
    }

    @Test
    fun `next timeout is capped`() = assertEquals(max, nextTimeout(remaining = max, delta = max, allowCancel = true, maxMillis = max))

    @Test
    fun `dismissal before the deadline is detected`() {
        assertTrue(isBeforeDeadline(now = 1_000, deadline = 60_000, tolerance = 5_000))
        assertTrue(isBeforeDeadline(now = 54_999, deadline = 60_000, tolerance = 5_000))
    }

    @Test
    fun `timeout around the deadline is not a dismissal`() {
        assertFalse(isBeforeDeadline(now = 55_000, deadline = 60_000, tolerance = 5_000))
        assertFalse(isBeforeDeadline(now = 60_000, deadline = 60_000, tolerance = 5_000))
        assertFalse(isBeforeDeadline(now = 90_000, deadline = 60_000, tolerance = 5_000))
    }

    @Test
    fun `missing deadline is never a dismissal`() = assertFalse(isBeforeDeadline(now = 1_000, deadline = 0, tolerance = 5_000))

    @Test
    fun `remaining time is rounded up to the minute`() {
        assertEquals(1L, ceilMinutes(1))
        assertEquals(1L, ceilMinutes(60_000))
        assertEquals(2L, ceilMinutes(60_001))
        assertEquals(30L, ceilMinutes(29 * 60_000L + 1_000))
    }

    @Test
    fun `remaining time is at least one minute`() {
        assertEquals(1L, ceilMinutes(0))
        assertEquals(1L, ceilMinutes(-5_000))
    }

    @Test
    fun `countdown under an hour`() {
        assertEquals("0:00", formatCountdown(0))
        assertEquals("0:01", formatCountdown(1))
        assertEquals("0:59", formatCountdown(59_000))
        assertEquals("1:00", formatCountdown(59_001))
        assertEquals("29:05", formatCountdown(29 * 60_000L + 5_000))
    }

    @Test
    fun `countdown over an hour`() {
        assertEquals("1:00:00", formatCountdown(60 * 60_000L))
        assertEquals("2:03:04", formatCountdown((2 * 3600 + 3 * 60 + 4) * 1000L))
    }

    @Test
    fun `countdown never goes negative`() = assertEquals("0:00", formatCountdown(-5_000))
}
