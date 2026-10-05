package pl.watershed.septictank.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import pl.watershed.septictank.SepticTankApplication
import pl.watershed.septictank.domain.forecast.PumpingForecast
import pl.watershed.septictank.domain.forecast.PumpingForecastCalculator
import pl.watershed.septictank.domain.usage.UsageState
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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Monitor szamba",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NavigationButton("Historia", Icons.AutoMirrored.Filled.List, onOpenHistory, Modifier.weight(1f))
                NavigationButton("Ustawienia", Icons.Filled.Settings, onOpenSettings, Modifier.weight(1f))
            }

            TankStatusCard(state.usageState, state.missingCapacityWarning)

            state.forecast?.let { ForecastCard(it) }

            SectionLabel("Odczyt licznika")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::onOpenCamera, modifier = Modifier.fillMaxWidth()) {
                    Text("Zrób zdjęcie licznika")
                }
                OutlinedButton(onClick = viewModel::onManualEntry, modifier = Modifier.fillMaxWidth()) {
                    Text("Wpisz odczyt ręcznie")
                }
            }

            SectionLabel("Wywóz")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    enabled = state.hasAnyReading,
                    onClick = viewModel::registerPumping,
                    modifier = Modifier.weight(1f),
                ) { Text("Wywóz ścieków") }
                // Spec 003 FR-001: orders pumping by SMS; does not register a pumping event (FR-009).
                Button(onClick = viewModel::onOrderPumpingClick, modifier = Modifier.weight(1f)) {
                    Text("Zamów wywóz")
                }
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

@Composable
private fun NavigationButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun HomeCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

/** Big fill percent + progress bar coloured by warning level (FR-009, FR-010). */
@Composable
private fun TankStatusCard(usage: UsageState?, missingCapacityWarning: Boolean) {
    HomeCard {
        Text("Stan zbiornika", style = MaterialTheme.typography.titleMedium)
        if (usage == null) {
            Text("Brak jeszcze żadnego odczytu licznika.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@HomeCard
        }

        val usedM3 = "%.3f".format(usage.currentUsageLiters / 1000.0)
        val percent = usage.usagePercentOfCapacity
        val levelColor = warningColor(usage.warningLevel)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (percent != null) "${"%.0f".format(percent)}%" else "$usedM3 m³",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = levelColor,
            )
            Spacer(Modifier.weight(1f))
            if (percent != null && usage.capacityLiters != null) {
                Text(
                    "$usedM3 / ${"%.3f".format(usage.capacityLiters / 1000.0)} m³",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        if (percent != null) {
            LinearProgressIndicator(
                progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
                color = levelColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().height(10.dp),
            )
        }

        warningMessage(usage.warningLevel)?.let { message ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = levelColor, modifier = Modifier.size(18.dp))
                Text(message, color = levelColor, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (missingCapacityWarning) {
            Text(
                "Skonfiguruj pojemność zbiornika w Ustawieniach, aby zobaczyć wypełnienie i ostrzeżenia.",
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun warningColor(level: WarningLevel): Color = when (level) {
    WarningLevel.NONE -> MaterialTheme.colorScheme.primary
    WarningLevel.APPROACHING -> MaterialTheme.colorScheme.tertiary
    WarningLevel.EXCEEDED -> MaterialTheme.colorScheme.error
}

/** FR-010: distinguishes at least two warning urgency levels. */
private fun warningMessage(level: WarningLevel): String? = when (level) {
    WarningLevel.NONE -> null
    WarningLevel.APPROACHING -> "Zbiornik zbliża się do zapełnienia."
    WarningLevel.EXCEEDED -> "Zbiornik prawdopodobnie przekroczył pojemność! Zaplanuj wywóz."
}

/** Days since the last pumping, average daily fill and the days-until-pumping estimate. */
@Composable
private fun ForecastCard(forecast: PumpingForecast) {
    val rows = remember(forecast) { ForecastTexts.rows(forecast, LocalDate.now()) }
    HomeCard {
        Text("Prognoza", style = MaterialTheme.typography.titleMedium)
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    row.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    row.value,
                    textAlign = TextAlign.End,
                    fontWeight = if (row.emphasis == ForecastRow.Emphasis.NORMAL) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (row.emphasis == ForecastRow.Emphasis.ALERT) MaterialTheme.colorScheme.error else Color.Unspecified,
                    modifier = Modifier.weight(1.3f),
                )
            }
        }
    }
}

/** One aligned label/value line of the forecast card. */
internal data class ForecastRow(val label: String, val value: String, val emphasis: Emphasis = Emphasis.NORMAL) {
    enum class Emphasis { NORMAL, STRONG, ALERT }
}

/** Forecast card content as plain strings, so every branch is covered by unit tests. */
internal object ForecastTexts {
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM")

    fun rows(forecast: PumpingForecast, today: LocalDate): List<ForecastRow> {
        fun dateIn(days: Long) = today.plusDays(days).format(DATE_FORMAT)

        val lastPumping = when (val days = forecast.daysSinceLastPumping) {
            null -> "brak"
            0L -> "dziś"
            1L -> "wczoraj"
            else -> "$days dni temu (${dateIn(-days)})"
        }

        val average = forecast.averageDailyLiters
        val averageText = if (average == null) {
            "za mało danych (min. 1 dzień odczytów)"
        } else {
            // Each number is formatted on its own: the '%' sign must never reach format() as text.
            val percent = forecast.averageDailyPercent?.let { " (${"%.1f".format(it)}%)" } ?: ""
            "${"%.3f".format(average / 1000.0)} m³$percent"
        }

        val rows = mutableListOf(
            ForecastRow("Ostatni wywóz", lastPumping),
            ForecastRow("Średnio dziennie", averageText),
        )

        val daysUntil = forecast.daysUntilPumping
        val fillNow = forecast.estimatedFillPercentNow
        if (average != null && fillNow != null) {
            val label = "Wywóz przy ${PumpingForecastCalculator.PUMPING_THRESHOLD_PERCENT}%"
            rows += when {
                daysUntil == null -> ForecastRow(label, "brak przyrostu")
                daysUntil < 1.0 -> ForecastRow(label, "potrzebny teraz (ok. ${"%.0f".format(fillNow)}%)", ForecastRow.Emphasis.ALERT)
                else -> {
                    val days = daysUntil.toLong()
                    val dayWord = if (days == 1L) "dzień" else "dni"
                    ForecastRow(label, "za ok. $days $dayWord (${dateIn(days)})", ForecastRow.Emphasis.STRONG)
                }
            }
        }
        return rows
    }
}
