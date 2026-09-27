package fr.smarquis.sleeptimer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import fr.smarquis.sleeptimer.SleepTimer.TIMEOUT_DECREMENT_MILLIS
import fr.smarquis.sleeptimer.SleepTimer.TIMEOUT_INCREMENT_MILLIS
import fr.smarquis.sleeptimer.SleepTimer.TIMEOUT_MAX_MILLIS
import java.util.concurrent.TimeUnit.MILLISECONDS

enum class SleepAction(private val value: String) {
    START("fr.smarquis.sleeptimer.action.START"),
    STOP("fr.smarquis.sleeptimer.action.STOP"),
    CANCEL("fr.smarquis.sleeptimer.action.CANCEL") {
        override fun title(context: Context) = context.getText(android.R.string.cancel)
    },
    INCREMENT("fr.smarquis.sleeptimer.action.INCREMENT") {
        override fun title(context: Context) = "+" + MILLISECONDS.toMinutes(TIMEOUT_INCREMENT_MILLIS)
    },
    DECREMENT("fr.smarquis.sleeptimer.action.DECREMENT") {
        override fun title(context: Context) = "-" + MILLISECONDS.toMinutes(TIMEOUT_DECREMENT_MILLIS)
    },
    UPDATE("fr.smarquis.sleeptimer.action.UPDATE"),
    ;

    companion object {
        private const val EXTRA_KEY_DURATION = "extras:duration"
        fun parse(intent: Intent?): SleepAction? = entries.firstOrNull { it.value == intent?.action }

        /**
         * @return the duration in milliseconds from the extra provided in seconds (as a `long` or an `int` extra), or `null` if missing.
         */
        fun duration(intent: Intent?): Long? {
            val extras = intent?.extras ?: return null
            val seconds = extras.getLong(EXTRA_KEY_DURATION, 0L).takeIf { it != 0L }
                ?: extras.getInt(EXTRA_KEY_DURATION, 0).toLong()
            return SleepMath.secondsToMillis(seconds, TIMEOUT_MAX_MILLIS)
        }
    }

    private fun intent(context: Context): Intent = Intent(context, SleepActionReceiver::class.java).setAction(value)

    private fun pendingIntent(context: Context, cancel: Boolean = false): PendingIntent? =
        PendingIntent.getBroadcast(context, 0, intent(context), PendingIntent.FLAG_IMMUTABLE).apply { if (cancel) cancel() }

    fun action(context: Context, cancel: Boolean = false): Notification.Action.Builder =
        Notification.Action.Builder(Icon.createWithResource(context, 0), title(context), pendingIntent(context, cancel))

    open fun title(context: Context): CharSequence? = null
}