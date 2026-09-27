package fr.smarquis.sleeptimer

import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Pure timing helpers, free of Android dependencies so they can be unit tested on the JVM.
 */
object SleepMath {

    val FADE_STEP_MAX_MILLIS = SECONDS.toMillis(1)
    val FADE_TOTAL_MAX_MILLIS = SECONDS.toMillis(30)

    /**
     * Delay between two volume steps: one step per second, but the whole fade never exceeds [FADE_TOTAL_MAX_MILLIS].
     * Devices exposing a lot of volume steps would otherwise take minutes to fade out, and exceed the time budget of
     * background and `shortService` foreground services.
     */
    fun fadeStepDelayMillis(steps: Int): Long =
        if (steps <= 0) 0L else minOf(FADE_STEP_MAX_MILLIS, FADE_TOTAL_MAX_MILLIS / steps)

    /**
     * Converts an externally provided duration (in seconds) to milliseconds, clamped to `±maxMillis` to avoid overflows.
     * @return `null` when the duration is missing (`0`).
     */
    fun secondsToMillis(seconds: Long, maxMillis: Long): Long? {
        if (seconds == 0L) return null
        val maxSeconds = SECONDS.convert(maxMillis, MILLISECONDS)
        return SECONDS.toMillis(seconds.coerceIn(-maxSeconds, maxSeconds))
    }

    /**
     * Computes the next timeout after applying [delta] to the [remaining] time.
     * When [allowCancel] is `false`, a delta that would end the timer is ignored and [remaining] is kept.
     */
    fun nextTimeout(remaining: Long, delta: Long, allowCancel: Boolean, maxMillis: Long): Long {
        val next = (remaining + delta).coerceAtMost(maxMillis)
        return if (next <= 0 && !allowCancel) remaining else next
    }

    /**
     * Rounds a positive duration up to the next whole minute, e.g. `29:01` → `30`, and at least `1`.
     */
    fun ceilMinutes(millis: Long): Long {
        val minute = MINUTES.toMillis(1)
        return ((millis + minute - 1) / minute).coerceAtLeast(1)
    }

    /**
     * Whether the sleep has been triggered before its [deadline] (minus [tolerance]).
     * This happens when the user dismisses the notification, which also fires its `deleteIntent`.
     * A missing deadline (`0`) is never considered early.
     */
    fun isBeforeDeadline(now: Long, deadline: Long, tolerance: Long): Boolean =
        deadline > 0 && now < deadline - tolerance
}
