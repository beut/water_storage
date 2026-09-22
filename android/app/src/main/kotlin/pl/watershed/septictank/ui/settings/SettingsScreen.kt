package pl.watershed.septictank.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.watershed.septictank.AppContainer
import pl.watershed.septictank.SepticTankApplication

@Composable
private fun rememberSettingsViewModel(container: AppContainer): SettingsViewModel {
    val context = LocalContext.current
    return viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(container.tankConfigurationRepository, context.applicationContext) }
        },
    )
}

/** FR-008, FR-013: tank capacity, warning threshold and reminder configuration. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as SepticTankApplication).container
    val viewModel = rememberSettingsViewModel(container)
    val state by viewModel.uiState.collectAsState()

    var capacityText by remember { mutableStateOf(state.capacityM3Text) }
    LaunchedEffect(state.capacityM3Text) { capacityText = state.capacityM3Text }
    var capacityError by remember { mutableStateOf(false) }

    var reminderEnabled by remember { mutableStateOf(state.reminderEnabled) }
    var reminderIntervalDays by remember { mutableStateOf(state.reminderIntervalDays) }
    LaunchedEffect(state.reminderEnabled, state.reminderIntervalDays) {
        reminderEnabled = state.reminderEnabled
        reminderIntervalDays = state.reminderIntervalDays
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp)) {
            TextButton(onClick = onBack) { Text("Wróć") }

            Text("Pojemność zbiornika (m³)", modifier = Modifier.padding(top = 16.dp))
            OutlinedTextField(
                value = capacityText,
                onValueChange = { capacityText = it },
                isError = capacityError,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { capacityError = !viewModel.saveCapacity(capacityText) },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Zapisz pojemność") }

            Text("Próg ostrzegawczy: ${state.warningThresholdPercent}%", modifier = Modifier.padding(top = 24.dp))
            Slider(
                value = state.warningThresholdPercent.toFloat(),
                valueRange = 1f..99f,
                onValueChange = { viewModel.saveWarningThreshold(it.toInt()) },
            )

            Row(modifier = Modifier.padding(top = 24.dp)) {
                Text("Cykliczne przypomnienia o zdjęciu licznika")
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = {
                        reminderEnabled = it
                        viewModel.setReminder(it, reminderIntervalDays)
                    },
                )
            }
            if (reminderEnabled) {
                Text("Co ile dni: $reminderIntervalDays")
                Slider(
                    value = reminderIntervalDays.toFloat(),
                    valueRange = 1f..30f,
                    onValueChange = {
                        reminderIntervalDays = it.toInt()
                        viewModel.setReminder(true, it.toInt())
                    },
                )
            }
        }
    }
}
