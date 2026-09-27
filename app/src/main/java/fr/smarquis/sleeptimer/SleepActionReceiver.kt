package fr.smarquis.sleeptimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock.elapsedRealtime
import fr.smarquis.sleeptimer.SleepAction.CANCEL
import fr.smarquis.sleeptimer.SleepAction.DECREMENT
import fr.smarquis.sleeptimer.SleepAction.DISMISS
import fr.smarquis.sleeptimer.SleepAction.INCREMENT
import fr.smarquis.sleeptimer.SleepAction.START
import fr.smarquis.sleeptimer.SleepAction.STOP
import fr.smarquis.sleeptimer.SleepAction.UPDATE
import fr.smarquis.sleeptimer.SleepNotification.cancel
import fr.smarquis.sleeptimer.SleepNotification.find
import fr.smarquis.sleeptimer.SleepNotification.show
import fr.smarquis.sleeptimer.SleepNotification.update
import fr.smarquis.sleeptimer.SleepTileService.Companion.requestTileUpdate
import fr.smarquis.sleeptimer.SleepTimer.DEADLINE_TOLERANCE_MILLIS
import fr.smarquis.sleeptimer.SleepTimer.REQUIRES_FOREGROUND_SERVICE
import fr.smarquis.sleeptimer.SleepTimer.TIMEOUT_DECREMENT_MILLIS
import fr.smarquis.sleeptimer.SleepTimer.TIMEOUT_INCREMENT_MILLIS
import fr.smarquis.sleeptimer.SleepTimer.TIMEOUT_INITIAL_MILLIS

class SleepActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.handle(intent)
        context.requestTileUpdate()
    }

    private fun Context.handle(intent: Intent?) = when (SleepAction.parse(intent)) {
        START -> show(timeout = SleepAction.duration(intent) ?: TIMEOUT_INITIAL_MILLIS)
        UPDATE -> update(delta = SleepAction.duration(intent) ?: 0L)
        INCREMENT -> update(TIMEOUT_INCREMENT_MILLIS)
        // The notification action is only disabled when (re)posted, it can become stale as time goes by.
        // Never let it end the timer without pausing playback: ignore it (but refresh the notification to disable it).
        DECREMENT -> update(-TIMEOUT_DECREMENT_MILLIS, allowCancel = false)
        STOP, CANCEL -> cancel()
        DISMISS -> onDismiss(SleepAction.deadline(intent))
        null -> Unit
    }

    /**
     * Only relevant when [REQUIRES_FOREGROUND_SERVICE]: the notification has been removed, but the alarm is still scheduled.
     * - At the deadline (notification timeout), the alarm is about to pause playback: nothing to do.
     * - Before the deadline, the user dismissed the notification (possible since Android 14): cancel the alarm.
     *   Unless a new timer has been started in the meantime, which updated the deadline and rescheduled the alarm.
     */
    private fun Context.onDismiss(deadline: Long) {
        if (!REQUIRES_FOREGROUND_SERVICE) return
        if (!SleepMath.isBeforeDeadline(elapsedRealtime(), deadline, DEADLINE_TOLERANCE_MILLIS)) return
        if (find() != null) return
        cancel()
    }
}
