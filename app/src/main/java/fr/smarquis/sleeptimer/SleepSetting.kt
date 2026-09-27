package fr.smarquis.sleeptimer

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.TimeUnit.MINUTES

/**
 * User configurable durations, stored in minutes.
 */
enum class SleepSetting(private val key: String, val defaultMinutes: Int, val range: IntRange) {
    /** Duration of a timer started from the tile, or without an explicit duration. */
    INITIAL("timeout_initial_minutes", defaultMinutes = 30, range = 1..12 * 60),

    /** Step of the notification "+" action. */
    INCREMENT("timeout_increment_minutes", defaultMinutes = 10, range = 1..120),

    /** Step of the notification "-" action. */
    DECREMENT("timeout_decrement_minutes", defaultMinutes = 10, range = 1..120),
    ;

    fun minutes(context: Context): Int = context.prefs().getInt(key, defaultMinutes).coerceIn(range)

    fun millis(context: Context): Long = MINUTES.toMillis(minutes(context).toLong())

    fun set(context: Context, minutes: Int) = context.prefs().edit().putInt(key, minutes.coerceIn(range)).apply()

}

/** Preferences storing all the user settings. */
fun Context.prefs(): SharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
