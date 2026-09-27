package fr.smarquis.sleeptimer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.smarquis.sleeptimer.R
import fr.smarquis.sleeptimer.SleepMath
import fr.smarquis.sleeptimer.SleepSetting
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit.MINUTES

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

private val PRESETS_MINUTES = listOf(15, 30, 45, 60, 90, 120)

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

@Composable
private fun SectionHeader(title: Int) = Text(
    text = stringResource(title),
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier
        .padding(start = 16.dp, top = 12.dp)
        .semantics { heading() },
)

@Composable
private fun WarningCard(title: Int, body: Int, onClick: () -> Unit) = Card(
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ),
) {
    Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.warning_action))
        }
    }
}

/**
 * Remaining time of the timer, ticking every second (aligned on [endsAt] so the displayed seconds are exact).
 */
@Composable
private fun rememberRemaining(endsAt: Long, now: () -> Long): Long {
    val remaining by produceState(endsAt - now(), endsAt) {
        while (true) {
            value = endsAt - now()
            val untilNextSecond = value % 1000
            delay(if (untilNextSecond > 0) untilNextSecond else 1000)
        }
    }
    return remaining
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimerCard(state: SleepTimerUiState, actions: SleepTimerActions, now: () -> Long) = Card(
    colors = if (state.running) CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val endsAt = state.endsAt
        if (endsAt == null) {
            Text(stringResource(R.string.timer_idle_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.timer_idle_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            return@Column
        }
        val remaining = rememberRemaining(endsAt, now)
        val countdown = SleepMath.formatCountdown(remaining)
        val remainingDescription = stringResource(R.string.timer_remaining_description, formatDuration(SleepMath.ceilMinutes(remaining).toInt()))
        Text(stringResource(R.string.timer_remaining), style = MaterialTheme.typography.labelLarge)
        Text(
            text = countdown,
            style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
            // Don't read every second tick out loud.
            modifier = Modifier.semantics { contentDescription = remainingDescription },
        )
        state.endsAtText?.let { Text(stringResource(R.string.timer_ends_at, it), style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = actions::reduce,
                enabled = remaining > MINUTES.toMillis(state.decrementMinutes.toLong()),
            ) { Text(stringResource(R.string.action_reduce, state.decrementMinutes)) }
            FilledTonalButton(onClick = actions::extend) { Text(stringResource(R.string.action_extend, state.incrementMinutes)) }
            OutlinedButton(onClick = actions::stop) { Text(stringResource(R.string.action_stop)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SetTimerCard(state: SleepTimerUiState, actions: SleepTimerActions, now: () -> Long) = Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(if (state.running) R.string.set_timer_title_running else R.string.set_timer_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        // Prefilled with the remaining time (rounded up to the minute) when running, or the default duration.
        val initial = remember { state.endsAt?.let { SleepMath.ceilMinutes(it - now()).toInt() } ?: state.initialMinutes }
        val picker = rememberTimePickerState(initialHour = (initial / 60).coerceAtMost(23), initialMinute = initial % 60, is24Hour = true)
        val minutes = picker.hour * 60 + picker.minute
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESETS_MINUTES.forEach { preset ->
                FilterChip(
                    selected = minutes == preset,
                    onClick = {
                        picker.hour = preset / 60
                        picker.minute = preset % 60
                    },
                    label = { Text(formatDuration(preset)) },
                )
            }
        }
        TimeInput(state = picker, modifier = Modifier.align(Alignment.CenterHorizontally))
        Button(
            onClick = { actions.start(minutes) },
            enabled = minutes > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val duration = formatDuration(minutes)
            Text(stringResource(if (state.running) R.string.action_set else R.string.action_start, duration))
        }
    }
}

@Composable
private fun SettingsCard(state: SleepTimerUiState, onEdit: (SleepSetting) -> Unit) = Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
) {
    SettingItem(R.string.settings_initial, R.string.settings_initial_description, formatDuration(state.initialMinutes)) { onEdit(SleepSetting.INITIAL) }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
    SettingItem(R.string.settings_increment, R.string.settings_increment_description, stringResource(R.string.action_extend, state.incrementMinutes)) { onEdit(SleepSetting.INCREMENT) }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
    SettingItem(R.string.settings_decrement, R.string.settings_decrement_description, stringResource(R.string.action_reduce, state.decrementMinutes)) { onEdit(SleepSetting.DECREMENT) }
}

@Composable
private fun SettingItem(title: Int, description: Int, value: String, onClick: () -> Unit) = ListItem(
    headlineContent = { Text(stringResource(title)) },
    supportingContent = { Text(stringResource(description)) },
    trailingContent = { Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.clickable(onClick = onClick),
)

@Composable
private fun AppearanceCard(state: SleepTimerUiState, actions: SleepTimerActions) = Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
) {
    val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ListItem(
        headlineContent = { Text(stringResource(R.string.theme_title)) },
        supportingContent = {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.themeMode == mode,
                        onClick = { actions.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                        label = { Text(stringResource(mode.label)) },
                    )
                }
            }
        },
        colors = colors,
    )
    if (state.dynamicColorAvailable) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
        ListItem(
            headlineContent = { Text(stringResource(R.string.dynamic_color_title)) },
            supportingContent = { Text(stringResource(R.string.dynamic_color_description)) },
            trailingContent = { Switch(checked = state.dynamicColor, onCheckedChange = null) },
            colors = colors,
            modifier = Modifier.toggleable(value = state.dynamicColor, role = Role.Switch, onValueChange = actions::setDynamicColor),
        )
    }
}

@Composable
private fun MinutesDialog(setting: SleepSetting, initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()?.takeIf { it in setting.range }
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(setting.title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter(Char::isDigit).take(4) },
                singleLine = true,
                suffix = { Text(stringResource(R.string.unit_minutes)) },
                isError = value == null,
                supportingText = { Text(stringResource(R.string.settings_range, setting.range.first, setting.range.last)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { value?.let(onConfirm) }),
                modifier = Modifier.focusRequester(focusRequester),
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onConfirm) }, enabled = value != null) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

private val SleepSetting.title: Int
    get() = when (this) {
        SleepSetting.INITIAL -> R.string.settings_initial
        SleepSetting.INCREMENT -> R.string.settings_increment
        SleepSetting.DECREMENT -> R.string.settings_decrement
    }

@Composable
private fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> stringResource(R.string.duration_minutes, rest)
        rest == 0 -> stringResource(R.string.duration_hours, hours)
        else -> stringResource(R.string.duration_hours_minutes, hours, rest)
    }
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
