package fr.smarquis.sleeptimer.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Color
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import fr.smarquis.sleeptimer.MissingPermission
import fr.smarquis.sleeptimer.SleepSetting
import fr.smarquis.sleeptimer.SleepTimerController
import fr.smarquis.sleeptimer.sleepTimer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Starts the timer with an exact duration, controls the running timer, and holds the settings.
 * Opened from the launcher, the notification, or by long-pressing the Quick Settings tile.
 */
class SleepTimerActivity : ComponentActivity() {

    companion object {
        /** The notification and the timer can change behind our back (notification actions, timeout, tile). */
        private val REFRESH_INTERVAL_MILLIS = SECONDS.toMillis(1)

        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, SleepTimerActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private val timer: SleepTimerController by lazy { sleepTimer() }
    private var state by mutableStateOf(SleepTimerUiState())

    private val notificationPermission = registerForActivityResult(RequestPermission()) { granted ->
        refresh()
        // The permission can be denied without any prompt (e.g. after being denied twice): fallback to the settings.
        if (!granted || timer.missingPermission() == MissingPermission.NOTIFICATIONS) openNotificationSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySystemBars()
        super.onCreate(savedInstanceState)
        refresh()
        setContent {
            SleepTimerTheme(themeMode = state.themeMode, dynamicColor = state.dynamicColor) {
                SleepTimerScreen(state = state, actions = actions)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    refresh()
                    delay(REFRESH_INTERVAL_MILLIS)
                }
            }
        }
    }

    /**
     * Edge-to-edge, with system bar icons matching the selected [ThemeMode] (not only the system one).
     */
    private fun applySystemBars() {
        val mode = Appearance.themeMode(this)
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { mode.isDark(it) }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun refresh() {
        val endsAt = timer.endsAt()
        // One warning at a time, in the order they must be fixed.
        val missingPermission = timer.missingPermission()
        state = SleepTimerUiState(
            endsAt = endsAt,
            endsAtText = endsAt?.let { DateFormat.getTimeFormat(this).format(Date(it)) },
            initialMinutes = SleepSetting.INITIAL.minutes(this),
            incrementMinutes = SleepSetting.INCREMENT.minutes(this),
            decrementMinutes = SleepSetting.DECREMENT.minutes(this),
            themeMode = Appearance.themeMode(this),
            dynamicColor = Appearance.dynamicColor(this),
            dynamicColorAvailable = Appearance.dynamicColorAvailable,
            notificationsEnabled = missingPermission != MissingPermission.NOTIFICATIONS,
            exactAlarmsAllowed = missingPermission != MissingPermission.EXACT_ALARMS,
        )
    }

    /** Runs a [timer] operation, then refreshes the screen (the controller refreshes the tile). */
    private inline fun update(operation: () -> Unit) {
        operation()
        refresh()
    }

    private val actions = object : SleepTimerActions {
        override fun start(minutes: Int) = when (timer.missingPermission()) {
            MissingPermission.NOTIFICATIONS -> requestNotifications()
            MissingPermission.EXACT_ALARMS -> requestExactAlarms()
            null -> update { timer.start(MINUTES.toMillis(minutes.toLong())) }
        }

        override fun stop() = update { timer.stop() }

        override fun extend() = update { timer.extend() }

        override fun reduce() = update { timer.reduce() }

        override fun setMinutes(setting: SleepSetting, minutes: Int) = update {
            setting.set(this@SleepTimerActivity, minutes)
            // Refresh the notification actions ("+N", "-N") of a running timer with the new steps.
            if (setting != SleepSetting.INITIAL) timer.refreshNotification()
        }

        override fun setThemeMode(mode: ThemeMode) {
            Appearance.setThemeMode(this@SleepTimerActivity, mode)
            // Since Android 12 the activity is recreated with the new night mode, before that Compose handles it alone.
            applySystemBars()
            refresh()
        }

        override fun setDynamicColor(enabled: Boolean) {
            Appearance.setDynamicColor(this@SleepTimerActivity, enabled)
            refresh()
        }

        override fun requestNotifications() {
            if (SDK_INT >= TIRAMISU && checkSelfPermission(POST_NOTIFICATIONS) != PERMISSION_GRANTED) notificationPermission.launch(POST_NOTIFICATIONS)
            else openNotificationSettings()
        }

        override fun requestExactAlarms() {
            if (SDK_INT >= S) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
    }

    private fun openNotificationSettings() =
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
}
