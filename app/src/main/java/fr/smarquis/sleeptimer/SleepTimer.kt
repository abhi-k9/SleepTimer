package fr.smarquis.sleeptimer

import android.os.Build
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS

object SleepTimer {
    /**
     * Android 17 introduced [Background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio) which prevents calling [android.media.AudioManager.adjustStreamVolume] from a background thread.
     */
    val REQUIRES_FOREGROUND_SERVICE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN

    val TIMEOUT_INITIAL_MILLIS = MINUTES.toMillis(30)
    val TIMEOUT_INCREMENT_MILLIS = MINUTES.toMillis(10)
    val TIMEOUT_DECREMENT_MILLIS = MINUTES.toMillis(10)

    /** Upper bound for any timer duration, to guard against overflows from externally provided durations. */
    val TIMEOUT_MAX_MILLIS = MINUTES.toMillis(24 * 60)

    /**
     * Slack allowed between the scheduled deadline and the moment the sleep is actually triggered.
     * A trigger happening earlier than that means the notification has been dismissed by the user.
     */
    val DEADLINE_TOLERANCE_MILLIS = SECONDS.toMillis(5)
}
