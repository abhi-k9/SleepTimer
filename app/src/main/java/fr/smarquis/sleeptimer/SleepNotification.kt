package fr.smarquis.sleeptimer

import android.app.Notification
import android.app.Notification.CATEGORY_EVENT
import android.app.Notification.VISIBILITY_PUBLIC
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_LOW
import android.content.Context
import android.os.Build.VERSION.SDK_INT_FULL
import android.os.Build.VERSION_CODES_FULL.BAKLAVA_1
import fr.smarquis.sleeptimer.SleepAction.CANCEL
import fr.smarquis.sleeptimer.SleepAction.DECREMENT
import fr.smarquis.sleeptimer.SleepAction.INCREMENT
import fr.smarquis.sleeptimer.ui.SleepTimerActivity
import java.text.DateFormat
import java.text.DateFormat.SHORT
import java.util.Date

/**
 * The ongoing notification of the running timer, removed by the system when the timer ends.
 */
class SleepNotification(
    private val context: Context,
    private val scheduler: AndroidSleepScheduler,
) : TimerNotification {

    private val manager = context.notificationManager()

    override fun areEnabled() = manager.areNotificationsEnabled()

    override fun endsAt(): Long? = manager.activeNotifications?.firstOrNull { it.id == R.id.notification_id }?.notification?.`when`

    override fun post(timer: Timer) {
        createChannel()
        manager.notify(R.id.notification_id, build(timer))
    }

    // Removing the notification from the app does not send its `deleteIntent`.
    override fun remove() = manager.cancel(R.id.notification_id)

    private fun build(timer: Timer): Notification = with(context) {
        Notification.Builder(this, getString(R.string.notification_channel_id))
            .setCategory(CATEGORY_EVENT)
            .setContentIntent(SleepTimerActivity.pendingIntent(this))
            .setDeleteIntent(scheduler.deleteIntent(timer))
            .setVisibility(VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_tile)
            // A title is required for Live Updates: https://developer.android.com/develop/ui/views/notifications/live-update
            .setContentTitle(getString(R.string.app_name))
            .setSubText(DateFormat.getTimeInstance(SHORT).format(Date(timer.endsAt)))
            .setShowWhen(true).setWhen(timer.endsAt)
            .setUsesChronometer(true).setChronometerCountDown(true)
            .setTimeoutAfter(timer.timeout)
            .apply {
                // Live Updates (promoted ongoing notifications) are only available since Android 16 QPR2 (API 36.1)
                if (SDK_INT_FULL >= BAKLAVA_1) setRequestPromotedOngoing(true)
            }
            .addAction(INCREMENT.action(this).build())
            .addAction(DECREMENT.action(this, cancel = timer.timeout <= SleepSetting.DECREMENT.millis(this)).build())
            .addAction(CANCEL.action(this).build())
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            context.getString(R.string.notification_channel_id),
            context.getString(R.string.app_name),
            IMPORTANCE_LOW,
        ).apply { lockscreenVisibility = VISIBILITY_PUBLIC }
        manager.createNotificationChannel(channel)
    }
}
