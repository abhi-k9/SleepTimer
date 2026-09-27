package fr.smarquis.sleeptimer.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Resources
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
import androidx.annotation.StringRes
import fr.smarquis.sleeptimer.R
import fr.smarquis.sleeptimer.prefs

enum class ThemeMode(@StringRes val label: Int, val nightMode: Int) {
    SYSTEM(R.string.theme_system, UiModeManager.MODE_NIGHT_AUTO),
    LIGHT(R.string.theme_light, UiModeManager.MODE_NIGHT_NO),
    DARK(R.string.theme_dark, UiModeManager.MODE_NIGHT_YES),
    ;

    fun isDark(resources: Resources): Boolean = when (this) {
        SYSTEM -> resources.configuration.uiMode and UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES
        LIGHT -> false
        DARK -> true
    }
}

object Appearance {
    private const val KEY_THEME = "theme"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"

    /** Material You colors extracted from the wallpaper, since Android 12. */
    val dynamicColorAvailable = SDK_INT >= S

    fun themeMode(context: Context): ThemeMode =
        context.prefs().getString(KEY_THEME, null)?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM

    fun setThemeMode(context: Context, mode: ThemeMode) {
        context.prefs().edit().putString(KEY_THEME, mode.name).apply()
        // Since Android 12, the system persists a per-app night mode, applied to every window of the app (and recreates them).
        if (SDK_INT >= S) context.getSystemService(UiModeManager::class.java).setApplicationNightMode(mode.nightMode)
    }

    fun dynamicColor(context: Context): Boolean = dynamicColorAvailable && context.prefs().getBoolean(KEY_DYNAMIC_COLOR, true)

    fun setDynamicColor(context: Context, enabled: Boolean) = context.prefs().edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
}
