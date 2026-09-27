package fr.smarquis.sleeptimer

import android.app.Notification
import android.app.Notification.CATEGORY_SERVICE
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_NO_CREATE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
import android.media.AudioManager
import android.media.AudioManager.ADJUST_LOWER
import android.media.AudioManager.STREAM_MUSIC
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.P
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.KeyEvent
import android.view.KeyEvent.ACTION_DOWN
import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Fades out the volume, pauses playback, then restores the volume, on a worker thread.
 * Started at the timer deadline by the [SleepScheduler].
 */
class SleepAudioService : Service() {

    companion object {
        private val RESTORE_VOLUME_MILLIS = SECONDS.toMillis(2)
        private const val DEADLINE_EXTRA_KEY = "extras:deadline"
        private const val FOREGROUND_EXTRA_KEY = "extras:foreground"

        private fun intent(context: Context) = Intent(context, SleepAudioService::class.java)

        private fun pendingIntent(context: Context, intent: Intent, foreground: Boolean, flags: Int): PendingIntent? =
            if (foreground) PendingIntent.getForegroundService(context, 0, intent, FLAG_IMMUTABLE or flags)
            else PendingIntent.getService(context, 0, intent, FLAG_IMMUTABLE or flags)

        /**
         * There is only ever one such [PendingIntent]: [FLAG_UPDATE_CURRENT] updates the [deadline] of the instance
         * already referenced by the posted notification (or the scheduled alarm).
         * @param deadline [android.os.SystemClock.elapsedRealtime] based deadline of the timer.
         * @param foreground whether the service is started as a foreground service, and must call [startForeground].
         */
        fun pendingIntent(context: Context, deadline: Long, foreground: Boolean): PendingIntent {
            val intent = intent(context).putExtra(DEADLINE_EXTRA_KEY, deadline).putExtra(FOREGROUND_EXTRA_KEY, foreground)
            return pendingIntent(context, intent, foreground, FLAG_UPDATE_CURRENT)!!
        }

        fun existingPendingIntent(context: Context, foreground: Boolean): PendingIntent? =
            pendingIntent(context, intent(context), foreground, FLAG_NO_CREATE)
    }

    /** Single thread: requests are handled sequentially. */
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called right away when started with `startForegroundService()`.
        if (intent?.getBooleanExtra(FOREGROUND_EXTRA_KEY, false) == true) {
            startForeground(R.id.notification_service_id, notification(), FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        }
        val deadline = intent?.getLongExtra(DEADLINE_EXTRA_KEY, 0L) ?: 0L
        worker.execute {
            try {
                if (sleepTimer().onDeadline(deadline)) audioManager().sleep()
            } finally {
                requestTileUpdate()
                // Stops the service (and removes the foreground notification) unless it has been started again since.
                main.post { stopSelf(startId) }
            }
        }
        return START_NOT_STICKY
    }

    /**
     * `shortService` foreground services must stop within a few seconds after this callback, or the app will ANR.
     */
    override fun onTimeout(startId: Int, fgsType: Int) = stopSelf()

    override fun onDestroy() {
        // Lets a running sleep finish (and restore the volume), but rejects new ones.
        worker.shutdown()
        super.onDestroy()
    }

    private fun AudioManager.sleep() {
        // compute volume to restore (at the time of the fade, the user may have changed it while the timer was running)
        val volumeToRestore = getStreamVolume(STREAM_MUSIC)
        try {
            // fade out volume (pointless when nothing is playing locally, e.g. when casting)
            if (isMusicActive && !isVolumeFixed) fadeOut()
            // pause media
            dispatchMediaKeyEvent(KeyEvent(ACTION_DOWN, KEYCODE_MEDIA_PAUSE))
            dispatchMediaKeyEvent(KeyEvent(ACTION_UP, KEYCODE_MEDIA_PAUSE))
        } finally {
            restoreVolume(volumeToRestore)
        }
    }

    private fun AudioManager.fadeOut() {
        val min = if (SDK_INT >= P) getStreamMinVolume(STREAM_MUSIC) else 0
        // Bounded number of iterations: the volume may never reach `min` (fixed volume, user interaction, OEM policies…)
        val steps = getStreamVolume(STREAM_MUSIC) - min
        val delay = SleepMath.fadeStepDelayMillis(steps)
        repeat(steps) {
            adjustStreamVolume(STREAM_MUSIC, ADJUST_LOWER, 0)
            Thread.sleep(delay)
            if (getStreamVolume(STREAM_MUSIC) <= min) return
        }
    }

    private fun AudioManager.restoreVolume(volume: Int) {
        if (getStreamVolume(STREAM_MUSIC) == volume) return
        Thread.sleep(RESTORE_VOLUME_MILLIS)
        // The player ignored the pause request: keep it quiet rather than blasting audio at full volume.
        if (isMusicActive) return
        setStreamVolume(STREAM_MUSIC, volume, 0)
    }

    /**
     * Foreground notification to display during [sleep].
     */
    private fun notification() = Notification.Builder(this, getString(R.string.notification_channel_id))
        .setCategory(CATEGORY_SERVICE)
        .setSmallIcon(R.drawable.ic_tile)
        .setContentTitle(getString(R.string.app_name))
        .setProgress(100, 50, true)
        .setOngoing(true)
        .build()

}
