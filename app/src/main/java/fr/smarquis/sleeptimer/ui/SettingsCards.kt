package fr.smarquis.sleeptimer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.smarquis.sleeptimer.R
import fr.smarquis.sleeptimer.SleepSetting

@Composable
internal fun SettingsCard(state: SleepTimerUiState, onEdit: (SleepSetting) -> Unit) = Card(
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
internal fun AppearanceCard(state: SleepTimerUiState, actions: SleepTimerActions) = Card(
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
internal fun MinutesDialog(setting: SleepSetting, initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
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
