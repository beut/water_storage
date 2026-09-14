package pl.watershed.septictank.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.watershed.septictank.SepticTankApplication
import pl.watershed.septictank.domain.warning.WarningLevel

/** Ekran główny (US1, US2, US3): bieżące zużycie, ostrzeżenie, akcja zdjęcia, przycisk wywozu. */
@Composable
fun HomeScreen(onOpenHistory: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as SepticTankApplication).container
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    container.meterReadingRepository,
                    container.pumpingEventRepository,
                    container.usageCalculator,
                    container.photoStorage,
                    container.ocrReader,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()

    when (val step = state.readingFlowStep) {
        is ReadingFlowStep.Capturing -> {
            CameraCaptureScreen(
                photoStorage = container.photoStorage,
                onPhotoCaptured = viewModel::onPhotoCaptured,
                onCancel = viewModel::onCancelReadingFlow,
            )
            return
        }
        is ReadingFlowStep.Confirming -> {
            ReadingConfirmationScreen(
                suggestedLiters = step.suggestedLiters,
                isAnomalous = step.isAnomalous,
                ocrRawText = step.ocrRawText,
                onConfirm = viewModel::confirmReading,
                onCancel = viewModel::onCancelReadingFlow,
            )
            return
        }
        ReadingFlowStep.Idle -> Unit
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row {
                TextButton(onClick = onOpenHistory) { Text("Historia") }
                TextButton(onClick = onOpenSettings) { Text("Ustawienia") }
            }

            val usage = state.usageState
            Text(
                text = if (usage != null) {
                    "Bieżące zużycie: %.3f m³".format(usage.currentUsageLiters / 1000.0)
                } else {
                    "Brak jeszcze żadnego odczytu licznika."
                },
                modifier = Modifier.padding(top = 16.dp),
            )

            usage?.usagePercentOfCapacity?.let { percent ->
                Text("Wykorzystanie pojemności zbiornika: %.0f%%".format(percent))
            }

            if (state.missingCapacityWarning) {
                Text(
                    "Skonfiguruj pojemność zbiornika w Ustawieniach, aby zobaczyć ostrzeżenia o zapełnieniu.",
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            WarningBanner(usage?.warningLevel ?: WarningLevel.NONE)

            Row(modifier = Modifier.padding(top = 24.dp)) {
                Button(onClick = viewModel::onOpenCamera) { Text("Zrób zdjęcie licznika") }
            }
            Row(modifier = Modifier.padding(top = 12.dp)) {
                Button(
                    enabled = state.hasAnyReading,
                    onClick = viewModel::registerPumping,
                ) { Text("Wywóz ścieków") }
            }
        }
    }
}

/** FR-010: rozróżnia co najmniej dwa poziomy pilności ostrzeżenia. */
@Composable
private fun WarningBanner(level: WarningLevel) {
    val (message, color) = when (level) {
        WarningLevel.NONE -> return
        WarningLevel.APPROACHING -> "Zbiornik zbliża się do zapełnienia." to MaterialTheme.colorScheme.tertiary
        WarningLevel.EXCEEDED -> "Zbiornik prawdopodobnie przekroczył pojemność! Zaplanuj wywóz." to
            MaterialTheme.colorScheme.error
    }
    Text(text = message, color = color, modifier = Modifier.padding(top = 12.dp))
}
