package fr.smarquis.sleeptimer

import android.annotation.SuppressLint
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.getActivity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.TIRAMISU
import android.provider.Settings
import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import android.service.quicksettings.TileService
import android.widget.Toast
import java.text.DateFormat.SHORT
import java.text.DateFormat.getTimeInstance
import java.util.Date

fun Context.requestTileUpdate() = TileService.requestListeningState(this, ComponentName(this, SleepTileService::class.java))

class SleepTileService : TileService() {

    override fun onStartListening() = refreshTile()

    override fun onClick() {
        val timer = sleepTimer()
        when (timer.missingPermission()) {
            MissingPermission.NOTIFICATIONS -> requestNotificationsPermission()
            MissingPermission.EXACT_ALARMS -> requestScheduleExactAlarmsPermission()
            // The cancelled notification might still be considered active by NotificationManager... so we use an extra hint
            null -> refreshTile(endsAt = timer.toggle()?.endsAt)
        }
    }

    private fun refreshTile(endsAt: Long? = sleepTimer().endsAt()) = qsTile?.run {
        if (endsAt == null) {
            state = STATE_INACTIVE
            if (SDK_INT >= Q) subtitle = resources.getText(R.string.tile_subtitle)
        } else {
            state = STATE_ACTIVE
            if (SDK_INT >= Q) subtitle = getTimeInstance(SHORT).format(Date(endsAt))
        }
        updateTile()
    } ?: Unit

    private fun requestNotificationsPermission() {
        Toast.makeText(this, R.string.toast_notification_permission, Toast.LENGTH_LONG).show()
        startActivityAndCollapseCompat(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    private fun requestScheduleExactAlarmsPermission() {
        Toast.makeText(this, R.string.toast_alarm_permission, Toast.LENGTH_LONG).show()
        if (SDK_INT >= Build.VERSION_CODES.S) startActivityAndCollapseCompat(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
    }

    private fun startActivityAndCollapseCompat(intent: Intent) {
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
        // Settings can't be displayed on top of the keyguard, the device must be unlocked first.
        if (isLocked) return unlockAndRun { startActivityAndCollapseCompat(intent) }
        @SuppressLint("StartActivityAndCollapseDeprecated")
        if (SDK_INT <= TIRAMISU) @Suppress("DEPRECATION") startActivityAndCollapse(intent)
        else startActivityAndCollapse(getActivity(this, 0, intent, FLAG_IMMUTABLE))
    }
}
