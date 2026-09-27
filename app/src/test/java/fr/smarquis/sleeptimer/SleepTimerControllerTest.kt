package fr.smarquis.sleeptimer

import fr.smarquis.sleeptimer.SleepTimerController.Companion.TIMEOUT_MAX_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerControllerTest {

    private class FakeClock(var now: Long = 1_000_000L, var elapsed: Long = 50_000L) : Clock {
        override fun currentTimeMillis() = now
        override fun elapsedRealtime() = elapsed
        fun advance(millis: Long) {
            now += millis
            elapsed += millis
        }
    }

    private class FakeNotification(var enabled: Boolean = true) : TimerNotification {
        var posted: Timer? = null
        override fun areEnabled() = enabled
        override fun endsAt() = posted?.endsAt
        override fun post(timer: Timer) {
            posted = timer
        }

        override fun remove() {
            posted = null
        }
    }

    private class FakeScheduler(var allowed: Boolean = true) : SleepScheduler {
        var scheduled: Timer? = null
        var cancellations = 0
        override fun isAllowed() = allowed
        override fun schedule(timer: Timer) {
            scheduled = timer
        }

        override fun cancel() {
            scheduled = null
            cancellations++
        }
    }

    private object FakeSettings : TimerSettings {
        override val initialMillis = 30 * MINUTE
        override val incrementMillis = 10 * MINUTE
        override val decrementMillis = 5 * MINUTE
    }

    private val clock = FakeClock()
    private val notification = FakeNotification()
    private val scheduler = FakeScheduler()
    private var changes = 0
    private val controller = SleepTimerController(notification, scheduler, FakeSettings, clock, onChanged = { changes++ })

    //region start / stop
    @Test
    fun `start posts and schedules the timer`() {
        val timer = controller.start(20 * MINUTE)!!

        assertEquals(Timer(timeout = 20 * MINUTE, endsAt = clock.now + 20 * MINUTE, deadline = clock.elapsed + 20 * MINUTE), timer)
        assertEquals(timer, notification.posted)
        assertEquals(timer, scheduler.scheduled)
        assertEquals(1, changes)
    }

    @Test
    fun `start uses the default duration`() = assertEquals(30 * MINUTE, controller.start()!!.timeout)

    @Test
    fun `start is capped`() = assertEquals(TIMEOUT_MAX_MILLIS, controller.start(Long.MAX_VALUE / 2)!!.timeout)

    @Test
    fun `start without duration stops the timer`() {
        controller.start()
        assertNull(controller.start(0))
        assertNull(notification.posted)
        assertEquals(1, scheduler.cancellations)
    }

    @Test
    fun `stop removes the notification and cancels the scheduler`() {
        controller.start()
        controller.stop()

        assertNull(notification.posted)
        assertNull(scheduler.scheduled)
        assertNull(controller.endsAt())
        assertEquals(2, changes)
    }

    @Test
    fun `toggle starts then stops`() {
        assertEquals(30 * MINUTE, controller.toggle()!!.timeout)
        assertNull(controller.toggle())
        assertNull(notification.posted)
    }
    //endregion

    //region adjustments
    @Test
    fun `remaining time follows the clock`() {
        controller.start(20 * MINUTE)
        clock.advance(5 * MINUTE)
        assertEquals(15 * MINUTE, controller.remaining())
    }

    @Test
    fun `extend adds the increment to the remaining time`() {
        controller.start(20 * MINUTE)
        clock.advance(5 * MINUTE)
        controller.extend()
        assertEquals(25 * MINUTE, notification.posted!!.timeout)
    }

    @Test
    fun `reduce subtracts the decrement from the remaining time`() {
        controller.start(20 * MINUTE)
        controller.reduce()
        assertEquals(15 * MINUTE, notification.posted!!.timeout)
    }

    @Test
    fun `reduce never ends the timer`() {
        controller.start(3 * MINUTE)
        controller.reduce()
        assertEquals(3 * MINUTE, notification.posted!!.timeout)
        assertEquals(0, scheduler.cancellations)
    }

    @Test
    fun `adjust can end the timer`() {
        controller.start(3 * MINUTE)
        controller.adjust(-5 * MINUTE)
        assertNull(notification.posted)
        assertEquals(1, scheduler.cancellations)
    }

    @Test
    fun `adjustments are ignored without timer`() {
        controller.extend()
        controller.reduce()
        controller.refreshNotification()
        assertNull(notification.posted)
        assertEquals(0, changes)
    }

    @Test
    fun `refresh re-posts the running timer`() {
        controller.start(20 * MINUTE)
        clock.advance(MINUTE)
        controller.refreshNotification()
        assertEquals(19 * MINUTE, notification.posted!!.timeout)
    }
    //endregion

    //region deadline
    @Test
    fun `deadline reached triggers the sleep`() {
        val timer = controller.start(20 * MINUTE)!!
        clock.advance(20 * MINUTE)
        notification.remove() // timed out
        assertTrue(controller.onDeadline(timer.deadline))
        assertEquals(0, scheduler.cancellations)
    }

    @Test
    fun `deadline reached within the tolerance triggers the sleep`() {
        val timer = controller.start(20 * MINUTE)!!
        clock.advance(20 * MINUTE - 1_000)
        assertTrue(controller.onDeadline(timer.deadline))
    }

    @Test
    fun `missing deadline triggers the sleep`() = assertTrue(controller.onDeadline(0L))

    @Test
    fun `dismissal before the deadline cancels the timer`() {
        val timer = controller.start(20 * MINUTE)!!
        clock.advance(5 * MINUTE)
        notification.remove() // dismissed by the user
        assertFalse(controller.onDeadline(timer.deadline))
        assertEquals(1, scheduler.cancellations)
        assertNull(scheduler.scheduled)
    }

    @Test
    fun `dismissal does not cancel a timer started in the meantime`() {
        val old = controller.start(20 * MINUTE)!!
        notification.remove() // dismissed by the user...
        val new = controller.start(30 * MINUTE)!! // ...then a new timer is started before the dismissal is handled
        assertFalse(controller.onDeadline(old.deadline))
        assertEquals(0, scheduler.cancellations)
        assertEquals(new, scheduler.scheduled)
    }
    //endregion

    //region permissions
    @Test
    fun `no missing permission`() = assertNull(controller.missingPermission())

    @Test
    fun `notifications are required first`() {
        notification.enabled = false
        scheduler.allowed = false
        assertEquals(MissingPermission.NOTIFICATIONS, controller.missingPermission())
    }

    @Test
    fun `exact alarms are required by the scheduler`() {
        scheduler.allowed = false
        assertEquals(MissingPermission.EXACT_ALARMS, controller.missingPermission())
    }
    //endregion

    private companion object {
        const val MINUTE = 60_000L
    }
}
