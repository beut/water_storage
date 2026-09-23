package pl.watershed.septictank.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.watershed.septictank.SepticTankApplication
import pl.watershed.septictank.domain.warning.WarningLevel

/** Home screen (US1, US2, US3): current usage, warning, photo action, pumping button. */
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
                    container.tankConfigurationRepository,
                    container.smsSender,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()

    // Spec 003 FR-005: SEND_SMS is requested only when the user confirms the first order.
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.onOrderConfirm() else viewModel.onOrderPermissionDenied() }
    val onOrderConfirm = {
        val hasSmsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
        if (hasSmsPermission) viewModel.onOrderConfirm() else smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
    }

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
                latestValueLiters = state.latestReadingLiters,
                hasPhoto = step.photoFile != null,
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
            Row(modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = viewModel::onManualEntry) { Text("Wpisz odczyt ręcznie") }
            }
            Row(modifier = Modifier.padding(top = 12.dp)) {
                Button(
                    enabled = state.hasAnyReading,
                    onClick = viewModel::registerPumping,
                ) { Text("Wywóz ścieków") }
            }
            // Spec 003 FR-001: orders pumping by SMS; does not register a pumping event (FR-009).
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = viewModel::onOrderPumpingClick) { Text("Zamów wywóz") }
            }
        }

        PumpingOrderDialog(
            state = state.pumpingOrderState,
            onDaySelected = viewModel::onOrderDaySelected,
            onConfirm = onOrderConfirm,
            onRetry = viewModel::onOrderRetry,
            onDismiss = viewModel::onOrderDismiss,
            onOpenSettings = {
                viewModel.onOrderDismiss()
                onOpenSettings()
            },
        )
    }
}

/** FR-010: distinguishes at least two warning urgency levels. */
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
