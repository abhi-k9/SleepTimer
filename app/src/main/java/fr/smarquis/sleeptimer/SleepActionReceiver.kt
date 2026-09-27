package fr.smarquis.sleeptimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.smarquis.sleeptimer.SleepAction.CANCEL
import fr.smarquis.sleeptimer.SleepAction.DECREMENT
import fr.smarquis.sleeptimer.SleepAction.DISMISS
import fr.smarquis.sleeptimer.SleepAction.INCREMENT
import fr.smarquis.sleeptimer.SleepAction.START
import fr.smarquis.sleeptimer.SleepAction.STOP
import fr.smarquis.sleeptimer.SleepAction.UPDATE

/**
 * Handles the notification actions and automation broadcasts (see README).
 */
class SleepActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timer = context.sleepTimer()
        when (SleepAction.parse(intent)) {
            START -> when (val duration = SleepAction.duration(intent)) {
                null -> timer.start()
                else -> timer.start(duration)
            }
            UPDATE -> timer.adjust(delta = SleepAction.duration(intent) ?: 0L)
            INCREMENT -> timer.extend()
            DECREMENT -> timer.reduce()
            STOP, CANCEL -> timer.stop()
            DISMISS -> timer.onDeadline(SleepAction.deadline(intent))
            null -> Unit
        }
    }
}
