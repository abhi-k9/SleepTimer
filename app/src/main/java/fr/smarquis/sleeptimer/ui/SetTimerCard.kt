package fr.smarquis.sleeptimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.smarquis.sleeptimer.R
import fr.smarquis.sleeptimer.SleepMath

private val PRESETS_MINUTES = listOf(15, 30, 45, 60, 90, 120)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SetTimerCard(state: SleepTimerUiState, actions: SleepTimerActions, now: () -> Long) = Card(
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
