package fr.smarquis.sleeptimer

import fr.smarquis.sleeptimer.SleepTimerController.Companion.DEADLINE_TOLERANCE_MILLIS
import fr.smarquis.sleeptimer.SleepTimerController.Companion.TIMEOUT_MAX_MILLIS
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS

/**
 * A running timer.
 * @property timeout duration until the end of the timer.
 * @property endsAt wall clock time of the end, for display purposes.
 * @property deadline `elapsedRealtime` based time of the end, immune to wall clock changes, used to trigger the sleep.
 */
data class Timer(val timeout: Long, val endsAt: Long, val deadline: Long)

interface Clock {
    fun currentTimeMillis(): Long
    fun elapsedRealtime(): Long
}

/**
 * The ongoing notification, which is also the source of truth of the running timer: the timer only exists while its
 * notification is posted, and it is removed by the system when the timer ends.
 */
interface TimerNotification {
    fun areEnabled(): Boolean

    /** @return the [Timer.endsAt] of the posted notification, or `null` when there is none. */
    fun endsAt(): Long?
    fun post(timer: Timer)
    fun remove()
}

/**
 * Triggers the sleep (fade out and pause playback) at the timer deadline.
 */
interface SleepScheduler {
    /** Whether the permissions required to trigger the sleep are granted. */
    fun isAllowed(): Boolean
    fun schedule(timer: Timer)
    fun cancel()
}

interface TimerSettings {
    val initialMillis: Long
    val incrementMillis: Long
    val decrementMillis: Long
}

enum class MissingPermission { NOTIFICATIONS, EXACT_ALARMS }

/**
 * All the timer operations, shared by the notification actions, the Quick Settings tile, automation broadcasts,
 * and the app screen. Free of Android dependencies.
 */
class SleepTimerController(
    private val notification: TimerNotification,
    private val scheduler: SleepScheduler,
    private val settings: TimerSettings,
    private val clock: Clock,
    /** Called after every change of the timer, to refresh the Quick Settings tile. */
    private val onChanged: () -> Unit = {},
) {

    companion object {
        /** Upper bound for any timer duration, to guard against overflows from externally provided durations. */
        val TIMEOUT_MAX_MILLIS = MINUTES.toMillis(24 * 60)

        /**
         * Slack allowed between the timer deadline and the moment the sleep is actually triggered.
         * A trigger happening earlier than that means the notification has been dismissed by the user.
         */
        val DEADLINE_TOLERANCE_MILLIS = SECONDS.toMillis(5)
    }

    fun endsAt(): Long? = notification.endsAt()

    fun remaining(): Long? = endsAt()?.let { it - clock.currentTimeMillis() }

    fun missingPermission(): MissingPermission? = when {
        !notification.areEnabled() -> MissingPermission.NOTIFICATIONS
        !scheduler.isAllowed() -> MissingPermission.EXACT_ALARMS
        else -> null
    }

    /**
     * Starts a new timer, or replaces the running one. A non positive [timeout] stops the timer.
     * @return the new [Timer], or `null` when stopped.
     */
    fun start(timeout: Long = settings.initialMillis): Timer? {
        if (timeout <= 0) return null.also { stop() }
        val clamped = timeout.coerceAtMost(TIMEOUT_MAX_MILLIS)
        val timer = Timer(
            timeout = clamped,
            endsAt = clock.currentTimeMillis() + clamped,
            deadline = clock.elapsedRealtime() + clamped,
        )
        scheduler.schedule(timer)
        notification.post(timer)
        onChanged()
        return timer
    }

    fun stop() {
        // Removing the notification from the app does not send its `deleteIntent`, the scheduler must be cancelled too.
        notification.remove()
        scheduler.cancel()
        onChanged()
    }

    /**
     * @return the new [Timer], or `null` when stopped.
     */
    fun toggle(): Timer? = if (endsAt() == null) start() else null.also { stop() }

    /**
     * Adds [delta] to the remaining time of the running timer, if any.
     * @param allowCancel whether the [delta] is allowed to end the timer, otherwise it is ignored.
     */
    fun adjust(delta: Long, allowCancel: Boolean = true) {
        val remaining = remaining() ?: return
        start(SleepMath.nextTimeout(remaining, delta, allowCancel, TIMEOUT_MAX_MILLIS))
    }

    fun extend() = adjust(settings.incrementMillis)

    /**
     * The notification action is only disabled when (re)posted, it can become stale as time goes by.
     * Never let it end the timer without pausing playback: ignore it (but refresh the notification to disable it).
     */
    fun reduce() = adjust(-settings.decrementMillis, allowCancel = false)

    /** Re-posts the running timer, e.g. to update the notification actions after a settings change. */
    fun refreshNotification() = adjust(0L)

    /**
     * Called when the notification is removed (its `deleteIntent` is sent), or when the scheduled trigger fires.
     * @return `true` when the [deadline] is reached and playback should be paused. `false` when the notification has
     * been dismissed by the user before the deadline (possible since Android 14): this cancels the timer, unless a
     * new one has been started in the meantime (which updated the deadline and rescheduled the trigger).
     */
    fun onDeadline(deadline: Long): Boolean {
        if (!SleepMath.isBeforeDeadline(clock.elapsedRealtime(), deadline, DEADLINE_TOLERANCE_MILLIS)) return true
        if (endsAt() == null) {
            scheduler.cancel()
            onChanged()
        }
        return false
    }
}
