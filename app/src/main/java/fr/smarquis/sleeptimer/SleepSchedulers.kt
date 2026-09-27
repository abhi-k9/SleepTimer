package fr.smarquis.sleeptimer

import android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP
import android.app.PendingIntent
import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.CINNAMON_BUN
import android.widget.Toast
import androidx.annotation.RequiresApi
import fr.smarquis.sleeptimer.SleepAction.DISMISS

/**
 * Android implementations of [SleepScheduler], which also provide the `deleteIntent` of the timer notification.
 */
sealed class AndroidSleepScheduler(protected val context: Context) : SleepScheduler {

    companion object {
        /**
         * Android 17 introduced [background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio)
         * which prevents calling [android.media.AudioManager.adjustStreamVolume] from the background.
         */
        fun create(context: Context): AndroidSleepScheduler =
            if (SDK_INT >= CINNAMON_BUN) ExactAlarmScheduler(context) else NotificationTimeoutScheduler(context)
    }

    /** `deleteIntent` of the notification, sent when it times out or is dismissed by the user. */
    abstract fun deleteIntent(timer: Timer): PendingIntent
}

/**
 * Relies on the notification timeout ([android.app.Notification.Builder.setTimeoutAfter]): its `deleteIntent` directly
 * starts [SleepAudioService] in the background. The system temporarily allowlists the app when sending it.
 */
private class NotificationTimeoutScheduler(context: Context) : AndroidSleepScheduler(context) {
    override fun isAllowed() = true
    override fun schedule(timer: Timer) = Unit
    override fun cancel() = Unit
    override fun deleteIntent(timer: Timer) = SleepAudioService.pendingIntent(context, timer.deadline, foreground = false)
}

/**
 * Android 17 [background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio) requires a
 * foreground service to lower the volume, and a notification `deleteIntent` is not allowed to start one.
 * An exact alarm starts [SleepAudioService] as a foreground service instead, and the `deleteIntent` is only used to
 * detect user dismissals (see [SleepTimerController.onDeadline]).
 */
@RequiresApi(CINNAMON_BUN)
private class ExactAlarmScheduler(context: Context) : AndroidSleepScheduler(context) {
    override fun isAllowed() = context.alarmManager().canScheduleExactAlarms()

    override fun schedule(timer: Timer) {
        if (!isAllowed()) return Toast.makeText(context, R.string.toast_alarm_permission, Toast.LENGTH_LONG).show()
        val operation = SleepAudioService.pendingIntent(context, timer.deadline, foreground = true)
        context.alarmManager().setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, timer.deadline, operation)
    }

    override fun cancel() {
        SleepAudioService.existingPendingIntent(context, foreground = true)?.let(context.alarmManager()::cancel)
    }

    override fun deleteIntent(timer: Timer) = DISMISS.deleteIntent(context, timer.deadline)
}
