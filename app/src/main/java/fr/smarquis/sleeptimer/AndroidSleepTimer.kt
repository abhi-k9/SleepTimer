package fr.smarquis.sleeptimer

import android.content.Context
import android.os.SystemClock

private object AndroidClock : Clock {
    override fun currentTimeMillis() = System.currentTimeMillis()
    override fun elapsedRealtime() = SystemClock.elapsedRealtime()
}

private class AndroidTimerSettings(private val context: Context) : TimerSettings {
    override val initialMillis get() = SleepSetting.INITIAL.millis(context)
    override val incrementMillis get() = SleepSetting.INCREMENT.millis(context)
    override val decrementMillis get() = SleepSetting.DECREMENT.millis(context)
}

/**
 * Entry point of all timer operations, wired to the Android implementations.
 * Cheap to create: all the state lives in the system (notification, alarm) and the preferences.
 */
fun Context.sleepTimer(): SleepTimerController {
    val context = applicationContext
    val scheduler = AndroidSleepScheduler.create(context)
    return SleepTimerController(
        notification = SleepNotification(context, scheduler),
        scheduler = scheduler,
        settings = AndroidTimerSettings(context),
        clock = AndroidClock,
        onChanged = { context.requestTileUpdate() },
    )
}
