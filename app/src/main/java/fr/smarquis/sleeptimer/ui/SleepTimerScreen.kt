package fr.smarquis.sleeptimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.smarquis.sleeptimer.R
import fr.smarquis.sleeptimer.SleepSetting

@Immutable
data class SleepTimerUiState(
    /** Wall clock time at which the running timer ends, `null` when there is no timer. */
    val endsAt: Long? = null,
    /** [endsAt] formatted with the user's time format. */
    val endsAtText: String? = null,
    val initialMinutes: Int = SleepSetting.INITIAL.defaultMinutes,
    val incrementMinutes: Int = SleepSetting.INCREMENT.defaultMinutes,
    val decrementMinutes: Int = SleepSetting.DECREMENT.defaultMinutes,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val dynamicColorAvailable: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val exactAlarmsAllowed: Boolean = true,
) {
    val running: Boolean get() = endsAt != null
}

interface SleepTimerActions {
    fun start(minutes: Int)
    fun stop()
    fun extend()
    fun reduce()
    fun setMinutes(setting: SleepSetting, minutes: Int)
    fun setThemeMode(mode: ThemeMode)
    fun setDynamicColor(enabled: Boolean)
    fun requestNotifications()
    fun requestExactAlarms()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerScreen(
    state: SleepTimerUiState,
    actions: SleepTimerActions,
    now: () -> Long = System::currentTimeMillis,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editing by rememberSaveable { mutableStateOf<SleepSetting?>(null) }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(title = { Text(stringResource(R.string.app_name)) }, scrollBehavior = scrollBehavior)
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.notificationsEnabled) WarningCard(
                    title = R.string.warning_notifications_title,
                    body = R.string.warning_notifications_body,
                    onClick = actions::requestNotifications,
                )
                if (!state.exactAlarmsAllowed) WarningCard(
                    title = R.string.warning_alarms_title,
                    body = R.string.warning_alarms_body,
                    onClick = actions::requestExactAlarms,
                )
                TimerCard(state, actions, now)
                SetTimerCard(state, actions, now)
                SectionHeader(R.string.section_settings)
                SettingsCard(state, onEdit = { editing = it })
                SectionHeader(R.string.section_appearance)
                AppearanceCard(state, actions)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    editing?.let { setting ->
        MinutesDialog(
            setting = setting,
            initial = state.minutes(setting),
            onDismiss = { editing = null },
            onConfirm = {
                actions.setMinutes(setting, it)
                editing = null
            },
        )
    }
}

private fun SleepTimerUiState.minutes(setting: SleepSetting) = when (setting) {
    SleepSetting.INITIAL -> initialMinutes
    SleepSetting.INCREMENT -> incrementMinutes
    SleepSetting.DECREMENT -> decrementMinutes
}

//region Previews
private object PreviewActions : SleepTimerActions {
    override fun start(minutes: Int) = Unit
    override fun stop() = Unit
    override fun extend() = Unit
    override fun reduce() = Unit
    override fun setMinutes(setting: SleepSetting, minutes: Int) = Unit
    override fun setThemeMode(mode: ThemeMode) = Unit
    override fun setDynamicColor(enabled: Boolean) = Unit
    override fun requestNotifications() = Unit
    override fun requestExactAlarms() = Unit
}

@Preview(name = "Idle")
@Composable
private fun IdlePreview() = SleepTimerTheme(ThemeMode.LIGHT, dynamicColor = false) {
    SleepTimerScreen(SleepTimerUiState(notificationsEnabled = false), PreviewActions)
}

@Preview(name = "Running (dark)")
@Composable
private fun RunningPreview() = SleepTimerTheme(ThemeMode.DARK, dynamicColor = false) {
    SleepTimerScreen(SleepTimerUiState(endsAt = 23 * 60_000L + 41_000L, endsAtText = "23:45"), PreviewActions, now = { 0L })
}
//endregion
