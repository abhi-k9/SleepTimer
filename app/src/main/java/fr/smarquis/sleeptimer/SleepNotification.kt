package fr.smarquis.sleeptimer

import android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP
import android.app.Notification
import android.app.Notification.CATEGORY_EVENT
import android.app.Notification.VISIBILITY_PUBLIC
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_LOW
import android.content.Context
import android.os.Build.VERSION.SDK_INT_FULL
import android.os.Build.VERSION_CODES_FULL.BAKLAVA_1
import android.os.SystemClock.elapsedRealtime
import android.widget.Toast
import fr.smarquis.sleeptimer.SleepAction.CANCEL
import fr.smarquis.sleeptimer.SleepAction.DECREMENT
import fr.smarquis.sleeptimer.SleepAction.DISMISS
import fr.smarquis.sleeptimer.SleepAction.INCREMENT
import fr.smarquis.sleeptimer.SleepTimer.REQUIRES_FOREGROUND_SERVICE
import fr.smarquis.sleeptimer.SleepTimer.TIMEOUT_MAX_MILLIS
import java.lang.System.currentTimeMillis
import java.text.DateFormat
import java.text.DateFormat.SHORT
import java.util.Date

object SleepNotification {

    fun Context.find() = notificationManager().activeNotifications?.firstOrNull { it.id == R.id.notification_id }?.notification

    /**
     * @return the newly created [Notification], or `null` when cancelling.
     */
    fun Context.toggle(): Notification? = if (find() == null) show() else null.also { cancel() }

    fun Context.cancel() {
        // Cancelling the notification from the app does not send its `deleteIntent`.
        notificationManager().cancel(R.id.notification_id)
        if (REQUIRES_FOREGROUND_SERVICE) SleepAudioService.existingPendingIntent(this)?.let(alarmManager()::cancel)
    }

    /**
     * @return the remaining time of the running timer, or `null` if there is none.
     */
    fun Context.remaining(): Long? = find()?.let { it.`when` - currentTimeMillis() }

    /**
     * @param allowCancel whether the [delta] is allowed to end the timer, otherwise it is ignored.
     */
    fun Context.update(delta: Long, allowCancel: Boolean = true) = remaining()?.let { remaining ->
        show(timeout = SleepMath.nextTimeout(remaining, delta, allowCancel, TIMEOUT_MAX_MILLIS))
    }

    fun Context.show(timeout: Long = SleepSetting.INITIAL.millis(this)): Notification? {
        if (timeout <= 0) return null.also { cancel() }
        @Suppress("NAME_SHADOWING") val timeout = timeout.coerceAtMost(TIMEOUT_MAX_MILLIS)
        val eta = currentTimeMillis() + timeout
        val deadline = elapsedRealtime() + timeout
        // Updates the deadline of the (unique) PendingIntent, including the one referenced by an existing notification.
        val sleepPendingIntent = SleepAudioService.pendingIntent(this, deadline)
        val notification = Notification.Builder(this, getString(R.string.notification_channel_id))
            .setCategory(CATEGORY_EVENT)
            .setContentIntent(SleepTimerActivity.pendingIntent(this))
            .setVisibility(VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_tile)
            // A title is required for Live Updates: https://developer.android.com/develop/ui/views/notifications/live-update
            .setContentTitle(getString(R.string.app_name))
            .setSubText(DateFormat.getTimeInstance(SHORT).format(Date(eta)))
            .setShowWhen(true).setWhen(eta)
            .setUsesChronometer(true).setChronometerCountDown(true)
            .setTimeoutAfter(timeout)
            .apply {
                // The system does not allow a notification `deleteIntent` to start a foreground service, an exact alarm
                // is scheduled instead (see below), and the `deleteIntent` is only used to detect user dismissals.
                if (!REQUIRES_FOREGROUND_SERVICE) setDeleteIntent(sleepPendingIntent)
                else setDeleteIntent(DISMISS.deleteIntent(this@show, deadline))
            }
            .apply {
                // Live Updates (promoted ongoing notifications) are only available since Android 16 QPR2 (API 36.1)
                if (SDK_INT_FULL >= BAKLAVA_1) setRequestPromotedOngoing(true)
            }
            .addAction(INCREMENT.action(this).build())
            .addAction(DECREMENT.action(this, cancel = timeout <= SleepSetting.DECREMENT.millis(this)).build())
            .addAction(CANCEL.action(this).build())
            .build()
        createNotificationChannel()
        notificationManager().notify(R.id.notification_id, notification)

        if (REQUIRES_FOREGROUND_SERVICE) {
            if (alarmManager().canScheduleExactAlarms().not()) Toast.makeText(this, R.string.toast_alarm_permission, Toast.LENGTH_LONG).show()
            else alarmManager().setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, deadline, sleepPendingIntent)
        }
        return notification
    }

    private fun Context.createNotificationChannel() {
        val id = getString(R.string.notification_channel_id)
        val name: CharSequence = getString(R.string.app_name)
        val channel = NotificationChannel(id, name, IMPORTANCE_LOW).apply {
            lockscreenVisibility = VISIBILITY_PUBLIC
        }
        notificationManager().createNotificationChannel(channel)
    }

}
