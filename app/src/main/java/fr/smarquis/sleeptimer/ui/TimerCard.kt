package fr.smarquis.sleeptimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.smarquis.sleeptimer.R
import fr.smarquis.sleeptimer.SleepMath
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit.MINUTES

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
internal fun TimerCard(state: SleepTimerUiState, actions: SleepTimerActions, now: () -> Long) = Card(
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
