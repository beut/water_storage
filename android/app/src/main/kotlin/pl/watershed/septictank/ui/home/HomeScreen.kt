package pl.watershed.septictank.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import pl.watershed.septictank.SepticTankApplication
import pl.watershed.septictank.domain.forecast.PumpingForecast
import pl.watershed.septictank.domain.forecast.PumpingForecastCalculator
import pl.watershed.septictank.domain.usage.UsageState
import pl.watershed.septictank.domain.warning.WarningLevel
import pl.watershed.septictank.ui.common.AppCard
import pl.watershed.septictank.ui.common.AppScaffold
import pl.watershed.septictank.ui.common.CardTitle
import pl.watershed.septictank.ui.common.CenteredContent
import pl.watershed.septictank.ui.common.IconBadge
import pl.watershed.septictank.ui.common.InfoRow
import pl.watershed.septictank.ui.common.NavigationRow
import pl.watershed.septictank.ui.common.NoticeBanner
import pl.watershed.septictank.ui.common.ScreenHorizontalPadding
import pl.watershed.septictank.ui.common.SectionHeader
import pl.watershed.septictank.ui.common.rememberSnackbarHostState
import pl.watershed.septictank.ui.theme.AppIcons
import pl.watershed.septictank.ui.theme.AppTheme

/** Home screen (US1, US2, US3): current usage, warning, photo action, pumping button. */
@Composable
fun HomeScreen(
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPumpingHistory: () -> Unit,
) {
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
    val snackbarHostState = rememberSnackbarHostState()
    val scope = rememberCoroutineScope()
    var confirmPumping by rememberSaveable { mutableStateOf(false) }

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

    AppScaffold(
        title = "Monitor szamba",
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Ustawienia")
            }
        },
    ) { innerPadding ->
        CenteredContent(modifier = Modifier.padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenHorizontalPadding)
                    .padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TankStatusCard(state.usageState, state.missingCapacityWarning, onOpenSettings)

                state.forecast?.let { ForecastCard(it) }

                SectionHeader("Odczyt licznika")
                ReadingActionsCard(
                    hasAnyReading = state.hasAnyReading,
                    onTakePhoto = viewModel::onOpenCamera,
                    onManualEntry = viewModel::onManualEntry,
                )

                SectionHeader("Wywóz")
                PumpingActionsCard(
                    canRegister = state.hasAnyReading,
                    onOrder = viewModel::onOrderPumpingClick,
                    onRegister = { confirmPumping = true },
                )

                SectionHeader("Historia")
                NavigationRow(
                    icon = AppIcons.ShowChart,
                    title = "Wykres wypełnienia",
                    description = "Odczyty i wywozy na osi czasu",
                    onClick = onOpenHistory,
                )
                NavigationRow(
                    icon = Icons.Filled.DateRange,
                    title = "Historia wywozów",
                    description = "Kiedy i ile wywieziono",
                    onClick = onOpenPumpingHistory,
                )
            }
        }

        if (confirmPumping) {
            // Registering a pumping resets the fill level, so it is confirmed first (it was a
            // single unguarded tap before).
            AlertDialog(
                onDismissRequest = { confirmPumping = false },
                icon = { Icon(AppIcons.LocalShipping, contentDescription = null) },
                title = { Text("Zarejestrować wywóz?") },
                text = {
                    Text(
                        "Zbiornik zostanie oznaczony jako opróżniony dzisiaj, a wypełnienie będzie " +
                            "liczone od ostatniego odczytu licznika.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmPumping = false
                        viewModel.registerPumping()
                        scope.launch { snackbarHostState.showSnackbar("Zarejestrowano wywóz") }
                    }) { Text("Zarejestruj") }
                },
                dismissButton = { TextButton(onClick = { confirmPumping = false }) { Text("Anuluj") } },
            )
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

/** Hero card: ring gauge with the fill percent, status pill and volume (FR-009, FR-010). */
@Composable
private fun TankStatusCard(usage: UsageState?, missingCapacityWarning: Boolean, onOpenSettings: () -> Unit) {
    AppCard {
        CardTitle("Stan zbiornika", icon = AppIcons.WaterDrop)
        if (usage == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(AppIcons.PhotoCamera)
                Text(
                    "Brak jeszcze żadnego odczytu licznika. Zrób zdjęcie licznika lub wpisz odczyt, " +
                        "aby zobaczyć stan zbiornika.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
            return@AppCard
        }

        val usedM3 = "%.3f".format(usage.currentUsageLiters / 1000.0)
        val percent = usage.usagePercentOfCapacity
        val status = statusStyle(usage.warningLevel)

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            if (percent != null) {
                FillGauge(percent = percent, color = status.color, modifier = Modifier.padding(vertical = 4.dp))
                if (usage.capacityLiters != null) {
                    Text(
                        "$usedM3 z ${"%.3f".format(usage.capacityLiters / 1000.0)} m³",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    "$usedM3 m³",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "zużyto od ostatniego wywozu",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (percent != null) {
            NoticeBanner(
                icon = status.icon,
                text = status.message,
                containerColor = status.containerColor,
                contentColor = status.onContainerColor,
            )
        }
        if (missingCapacityWarning) {
            NoticeBanner(
                icon = Icons.Filled.Info,
                text = "Podaj pojemność zbiornika, aby zobaczyć wypełnienie w procentach i ostrzeżenia.",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                action = { TextButton(onClick = onOpenSettings) { Text("Ustawienia") } },
            )
        }
    }
}

/** 270° ring with the fill percent in its centre; announced to TalkBack as one value. */
@Composable
private fun FillGauge(percent: Double, color: Color, modifier: Modifier = Modifier) {
    val target = (percent / 100.0).toFloat().coerceIn(0f, 1f)
    val progress by animateFloatAsState(targetValue = target, animationSpec = tween(durationMillis = 700), label = "fill")
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val percentText = "${"%.0f".format(percent)}%"

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(176.dp)
            .clearAndSetSemantics { contentDescription = "Wypełnienie zbiornika $percentText" },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 16.dp.toPx()
            val inset = strokeWidth / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            if (progress > 0f) {
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(percentText, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("wypełnienia", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class StatusStyle(
    val color: Color,
    val containerColor: Color,
    val onContainerColor: Color,
    val icon: ImageVector,
    val message: String,
)

/** FR-010: distinguishes at least two warning urgency levels -- by colour, icon and text. */
@Composable
private fun statusStyle(level: WarningLevel): StatusStyle {
    val status = AppTheme.statusColors
    return when (level) {
        WarningLevel.NONE -> StatusStyle(
            color = MaterialTheme.colorScheme.primary,
            containerColor = status.goodContainer,
            onContainerColor = status.onGoodContainer,
            icon = Icons.Filled.CheckCircle,
            message = "Wszystko w porządku — zbiornik ma jeszcze zapas.",
        )
        WarningLevel.APPROACHING -> StatusStyle(
            color = status.warning,
            containerColor = status.warningContainer,
            onContainerColor = status.onWarningContainer,
            icon = Icons.Filled.Warning,
            message = "Zbiornik zbliża się do zapełnienia.",
        )
        WarningLevel.EXCEEDED -> StatusStyle(
            color = status.critical,
            containerColor = status.criticalContainer,
            onContainerColor = status.onCriticalContainer,
            icon = Icons.Filled.Warning,
            message = "Zbiornik prawdopodobnie przekroczył pojemność! Zaplanuj wywóz.",
        )
    }
}

/** Days since the last pumping, average daily fill and the days-until-pumping estimate. */
@Composable
private fun ForecastCard(forecast: PumpingForecast) {
    val rows = remember(forecast) { ForecastTexts.rows(forecast, LocalDate.now()) }
    AppCard {
        CardTitle("Prognoza", icon = AppIcons.Speed)
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                InfoRow(
                    label = row.label,
                    value = row.value,
                    emphasized = row.emphasis != ForecastRow.Emphasis.NORMAL,
                    valueColor = when (row.emphasis) {
                        ForecastRow.Emphasis.ALERT -> MaterialTheme.colorScheme.error
                        ForecastRow.Emphasis.STRONG -> MaterialTheme.colorScheme.primary
                        ForecastRow.Emphasis.NORMAL -> Color.Unspecified
                    },
                )
            }
        }
    }
}

/** Primary action of the app: register a new meter reading (photo first, manual as fallback). */
@Composable
private fun ReadingActionsCard(hasAnyReading: Boolean, onTakePhoto: () -> Unit, onManualEntry: () -> Unit) {
    AppCard {
        Text(
            if (hasAnyReading) {
                "Regularne odczyty poprawiają dokładność prognozy."
            } else {
                "Zacznij od pierwszego odczytu licznika wody."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            ButtonIcon(AppIcons.PhotoCamera)
            Text("Zrób zdjęcie licznika")
        }
        OutlinedButton(onClick = onManualEntry, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            ButtonIcon(Icons.Filled.Edit)
            Text("Wpisz odczyt ręcznie")
        }
    }
}

@Composable
private fun PumpingActionsCard(canRegister: Boolean, onOrder: () -> Unit, onRegister: () -> Unit) {
    AppCard {
        // Spec 003 FR-001: orders pumping by SMS; does not register a pumping event (FR-009).
        FilledTonalButton(onClick = onOrder, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            ButtonIcon(Icons.AutoMirrored.Filled.Send)
            Text("Zamów wywóz SMS-em")
        }
        OutlinedButton(
            enabled = canRegister,
            onClick = onRegister,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            ButtonIcon(AppIcons.LocalShipping)
            Text("Zarejestruj wykonany wywóz")
        }
        if (!canRegister) {
            Text(
                "Rejestracja wywozu wymaga co najmniej jednego odczytu licznika.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ButtonIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
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
