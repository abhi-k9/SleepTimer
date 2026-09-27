package fr.smarquis.sleeptimer

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.NumberPicker
import android.widget.Toast
import fr.smarquis.sleeptimer.SleepNotification.cancel
import fr.smarquis.sleeptimer.SleepNotification.find
import fr.smarquis.sleeptimer.SleepNotification.remaining
import fr.smarquis.sleeptimer.SleepNotification.show
import fr.smarquis.sleeptimer.SleepNotification.update
import fr.smarquis.sleeptimer.SleepTileService.Companion.requestTileUpdate
import fr.smarquis.sleeptimer.SleepTimer.REQUIRES_FOREGROUND_SERVICE
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MINUTES

/**
 * Sets the timer to an exact duration, and configures the default duration and the notification steps.
 * Opened from the notification, or by long-pressing the Quick Settings tile.
 */
class SleepTimerActivity : Activity() {

    companion object {
        private const val REQUEST_CODE_NOTIFICATIONS = 1

        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, SleepTimerActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private lateinit var hours: NumberPicker
    private lateinit var minutes: NumberPicker
    private lateinit var start: Button
    private var stepsChanged = false
    private var pendingTimeout: Long? = null

    private val timeout: Long
        get() = HOURS.toMillis(hours.value.toLong()) + MINUTES.toMillis(minutes.value.toLong())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep_timer)

        // Timer: prefilled with the remaining time (rounded up to the minute) when running, or the default duration.
        val remaining = remaining()
        val running = remaining != null
        val initialMinutes = remaining?.let(SleepMath::ceilMinutes) ?: SleepSetting.INITIAL.minutes(this).toLong()
        start = findViewById(R.id.timer_start)
        hours = findViewById<NumberPicker>(R.id.timer_hours).configure(0..23, (initialMinutes / 60).toInt()) { refreshStartButton() }
        minutes = findViewById<NumberPicker>(R.id.timer_minutes).configure(0..59, (initialMinutes % 60).toInt()) { refreshStartButton() }
        minutes.wrapSelectorWheel = true
        start.setText(if (running) R.string.timer_set else R.string.timer_start)
        start.setOnClickListener { start(timeout) }
        findViewById<Button>(R.id.timer_stop).apply {
            visibility = if (running) View.VISIBLE else View.GONE
            setOnClickListener {
                cancel()
                requestTileUpdate()
                finish()
            }
        }
        refreshStartButton()

        // Settings
        bind(R.id.settings_initial, SleepSetting.INITIAL)
        bind(R.id.settings_increment, SleepSetting.INCREMENT)
        bind(R.id.settings_decrement, SleepSetting.DECREMENT)
    }

    override fun onStop() {
        super.onStop()
        // Refresh the notification actions ("+N", "-N") of a running timer with the new steps.
        if (stepsChanged && find() != null) update(delta = 0L)
        stepsChanged = false
    }

    private fun NumberPicker.configure(range: IntRange, value: Int, onChange: (Int) -> Unit): NumberPicker = apply {
        minValue = range.first
        maxValue = range.last
        wrapSelectorWheel = false
        this.value = value.coerceIn(range)
        setOnValueChangedListener { _, _, new -> onChange(new) }
    }

    private fun bind(id: Int, setting: SleepSetting) {
        findViewById<NumberPicker>(id).configure(setting.range, setting.minutes(this)) {
            setting.set(this, it)
            if (setting != SleepSetting.INITIAL) stepsChanged = true
        }
    }

    private fun refreshStartButton() {
        start.isEnabled = timeout > 0
    }

    private fun start(timeout: Long) {
        when {
            !notificationManager().areNotificationsEnabled() -> requestNotifications(timeout)
            REQUIRES_FOREGROUND_SERVICE && !alarmManager().canScheduleExactAlarms() -> requestScheduleExactAlarms()
            else -> {
                show(timeout)
                requestTileUpdate()
                finish()
            }
        }
    }

    private fun requestNotifications(timeout: Long) {
        if (SDK_INT >= TIRAMISU && checkSelfPermission(POST_NOTIFICATIONS) != PERMISSION_GRANTED) {
            pendingTimeout = timeout
            requestPermissions(arrayOf(POST_NOTIFICATIONS), REQUEST_CODE_NOTIFICATIONS)
        } else openNotificationSettings()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE_NOTIFICATIONS) return
        val timeout = pendingTimeout.also { pendingTimeout = null } ?: return
        // The permission can be denied without any prompt (e.g. after being denied twice): fallback to the settings.
        if (grantResults.firstOrNull() == PERMISSION_GRANTED && notificationManager().areNotificationsEnabled()) start(timeout)
        else openNotificationSettings()
    }

    private fun openNotificationSettings() {
        Toast.makeText(this, R.string.toast_notification_permission, Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    private fun requestScheduleExactAlarms() {
        Toast.makeText(this, R.string.toast_alarm_permission, Toast.LENGTH_LONG).show()
        if (SDK_INT >= S) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
    }
}
