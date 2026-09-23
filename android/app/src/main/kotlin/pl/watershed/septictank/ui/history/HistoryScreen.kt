package pl.watershed.septictank.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.patrykandpatrick.vico.compose.axis.axisGuidelineComponent
import com.patrykandpatrick.vico.compose.axis.axisLabelComponent
import com.patrykandpatrick.vico.compose.axis.axisLineComponent
import com.patrykandpatrick.vico.compose.axis.axisTickComponent
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollSpec
import com.patrykandpatrick.vico.compose.component.lineComponent
import com.patrykandpatrick.vico.compose.component.shapeComponent
import com.patrykandpatrick.vico.compose.component.textComponent
import com.patrykandpatrick.vico.compose.dimensions.dimensionsOf
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.DefaultPointConnector
import com.patrykandpatrick.vico.core.chart.decoration.ThresholdLine
import com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.component.marker.MarkerComponent
import com.patrykandpatrick.vico.core.entry.ChartEntry
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.marker.MarkerLabelFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import pl.watershed.septictank.SepticTankApplication
import pl.watershed.septictank.data.db.entities.ReadingSource
import pl.watershed.septictank.domain.history.HistoryPoint
import pl.watershed.septictank.domain.history.HistoryPointType

private const val MILLIS_PER_DAY = 86_400_000L

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as SepticTankApplication).container
    val viewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HistoryViewModel(
                    container.meterReadingRepository,
                    container.pumpingEventRepository,
                    container.tankConfigurationRepository,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            TextButton(onClick = onBack) { Text("Wróć") }

            Text("Historia zbiornika", modifier = Modifier.padding(top = 12.dp))

            if (state.chartPoints.isEmpty()) {
                // FR-007: friendly empty state instead of an empty/broken chart.
                Text(
                    "Brak jeszcze żadnych danych. Zrób zdjęcie licznika lub wpisz odczyt ręcznie, " +
                        "aby zobaczyć tutaj wykres wypełnienia zbiornika.",
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                HistoryChart(state)
            }
        }
    }
}

@Composable
private fun HistoryChart(state: HistoryUiState) {
    if (!state.isCapacityConfigured) {
        // FR-004: percent fill requires a configured tank capacity; show m3 and explain why.
        Text(
            "Skonfiguruj pojemność zbiornika w Ustawieniach, aby zobaczyć wypełnienie w procentach.",
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }

    val points = remember(state.chartPoints) { state.chartPoints.sortedBy { it.timestampMillis } }
    // x = whole days since local midnight of the first point. Vico 1.x derives its x step from the
    // GCD of all x values, so fractional day offsets (e.g. 0.347) yield a near-zero step and the
    // chart tries to lay out hundreds of thousands of steps, freezing the UI thread.
    val firstDayStartMillis = remember(points) { startOfDay(points.first().timestampMillis) }

    // Always-visible numeric preview (m3 and, when available, %) -- not only on tap, so the exact
    // current value is never hidden behind a gesture the user has to discover.
    points.lastOrNull { it.type == HistoryPointType.READING }?.let { latest ->
        Text(
            "Aktualne wypełnienie: ${formatValue(latest)}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
    }

    val readingEntries = remember(points) {
        points.filter { it.type == HistoryPointType.READING }
            .map { toEntry(it, firstDayStartMillis, state.isCapacityConfigured) }
    }
    val pumpingEntries = remember(points) {
        points.filter { it.type == HistoryPointType.PUMPING }
            .map { toEntry(it, firstDayStartMillis, state.isCapacityConfigured) }
    }
    val hasPumpingPoints = pumpingEntries.isNotEmpty()

    // A pumping point's baseline reading value (FR-010: no information lost vs. the old text view).
    val readingPointsBySourceId = remember(points) {
        points.filter { it.type == HistoryPointType.READING }.associateBy { it.sourceReadingId }
    }

    val modelProducer = remember { ChartEntryModelProducer() }
    remember(readingEntries, pumpingEntries) {
        modelProducer.setEntries(if (hasPumpingPoints) listOf(readingEntries, pumpingEntries) else listOf(readingEntries))
    }

    val readingColor = MaterialTheme.colorScheme.primary
    val pumpingColor = MaterialTheme.colorScheme.secondary
    val thresholdColor = MaterialTheme.colorScheme.tertiary
    // Explicit MaterialTheme colors for the axis: Vico's own default axis style picks colors based
    // on the *system* dark-mode setting, but this app's MaterialTheme is always light (MainActivity
    // never passes a dark colorScheme) -- on a phone with system dark mode on, Vico's un-styled
    // default axis would render light-on-light (invisible: "just lines, no scale").
    val axisLabelColor = MaterialTheme.colorScheme.onSurface
    val axisLineColor = MaterialTheme.colorScheme.outline

    // FR-002 / Clarifications 2026-09-22: pumping events use a distinct shape (diamond), not just color.
    val readingPoint = shapeComponent(Shapes.pillShape, readingColor)
    val pumpingPoint = shapeComponent(Shapes.cutCornerShape(allPercent = 50), pumpingColor)

    // Straight segments: Vico's default cubic smoothing adds bumps that suggest fill values the
    // tank never had between readings.
    val straightConnector = remember { DefaultPointConnector(cubicStrength = 0f) }
    val readingLine = lineSpec(lineColor = readingColor, point = readingPoint, pointSize = 8.dp, pointConnector = straightConnector)
    val pumpingLine = lineSpec(lineColor = Color.Transparent, point = pumpingPoint, pointSize = 12.dp, pointConnector = straightConnector)

    // Y axis from 0 to a "nice" maximum with one label per nice step (e.g. 0..5% every 1%), so tick
    // values are round and never repeat after formatting.
    val yScale = remember(readingEntries, pumpingEntries) {
        niceYScale((readingEntries + pumpingEntries).maxOfOrNull { it.y } ?: 0f)
    }
    val axisValuesOverrider = remember(yScale) { AxisValuesOverrider.fixed(minY = 0f, maxY = yScale.max) }
    val startAxisItemPlacer = remember(yScale) { AxisItemPlacer.Vertical.default(maxItemCount = yScale.labelCount) }

    val axisDateFormat = remember { SimpleDateFormat("dd.MM.yy", Locale.getDefault()) }
    val detailDateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    val bottomAxisFormatter = remember(firstDayStartMillis) {
        AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
            axisDateFormat.format(Date(firstDayStartMillis + (value * MILLIS_PER_DAY).toLong()))
        }
    }
    val startAxisFormatter = remember(state.isCapacityConfigured, yScale) {
        AxisValueFormatter<AxisPosition.Vertical.Start> { value, _ ->
            val number = "%.${yScale.decimals}f".format(value)
            if (state.isCapacityConfigured) "$number%" else "$number m³"
        }
    }

    val markerLabel = textComponent(
        color = MaterialTheme.colorScheme.onSurface,
        background = shapeComponent(Shapes.roundedCornerShape(allPercent = 20), MaterialTheme.colorScheme.surfaceVariant),
        padding = dimensionsOf(horizontal = 8.dp, vertical = 4.dp),
        lineCount = 3,
    )
    val markerIndicator = shapeComponent(Shapes.pillShape, readingColor)
    val markerGuideline = lineComponent(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), thickness = 1.dp)
    val marker = remember(markerLabel, markerIndicator, markerGuideline, readingPointsBySourceId) {
        MarkerComponent(markerLabel, markerIndicator, markerGuideline).apply {
            indicatorSizeDp = 12f
            // FR-005 / User Story 3: exact date, time and value (both m3 and %) for the tapped point.
            // Several points can share one day (x); the latest of them is shown.
            labelFormatter = MarkerLabelFormatter { markedEntries, _ ->
                val point = markedEntries
                    .mapNotNull { (it.entry as? HistoryChartEntry)?.point }
                    .maxByOrNull { it.timestampMillis }
                point?.let { formatPointDetails(it, detailDateFormat, readingPointsBySourceId) } ?: ""
            }
        }
    }

    val decorations = state.warningThresholdPercent?.let { thresholdPercent ->
        // FR-009: warning-threshold reference line, same color as the HomeScreen warning banner.
        listOf(
            ThresholdLine(
                thresholdValue = thresholdPercent.toFloat(),
                thresholdLabel = "Próg ostrzegawczy: $thresholdPercent%",
                lineComponent = shapeComponent(color = thresholdColor),
                labelComponent = textComponent(
                    color = thresholdColor,
                    background = shapeComponent(Shapes.rectShape, thresholdColor.copy(alpha = 0.1f)),
                    padding = dimensionsOf(horizontal = 6.dp, vertical = 2.dp),
                ),
            ),
        )
    } ?: emptyList()

    Chart(
        chart = lineChart(
            lines = if (hasPumpingPoints) listOf(readingLine, pumpingLine) else listOf(readingLine),
            decorations = decorations,
            axisValuesOverrider = axisValuesOverrider,
        ),
        chartModelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(top = 16.dp),
        startAxis = rememberStartAxis(
            valueFormatter = startAxisFormatter,
            itemPlacer = startAxisItemPlacer,
            label = axisLabelComponent(color = axisLabelColor),
            axis = axisLineComponent(color = axisLineColor, shape = Shapes.rectShape),
            tick = axisTickComponent(color = axisLineColor, shape = Shapes.rectShape),
            guideline = axisGuidelineComponent(color = axisLineColor.copy(alpha = 0.3f)),
        ),
        bottomAxis = rememberBottomAxis(
            valueFormatter = bottomAxisFormatter,
            label = axisLabelComponent(color = axisLabelColor),
            axis = axisLineComponent(color = axisLineColor, shape = Shapes.rectShape),
            tick = axisTickComponent(color = axisLineColor, shape = Shapes.rectShape),
            guideline = axisGuidelineComponent(color = axisLineColor.copy(alpha = 0.3f)),
        ),
        marker = marker,
        // FR-006 / FR-008: horizontal pan and pinch-to-zoom across the full history (both default to
        // enabled in Vico, set explicitly here to document the requirement).
        chartScrollSpec = rememberChartScrollSpec(isScrollEnabled = true),
        isZoomEnabled = true,
    )

    HistoryLegend(readingColor = readingColor, pumpingColor = pumpingColor)
}

/** FR-002 accessibility intent: shape + text legend, not color alone, readable by screen readers. */
@Composable
private fun HistoryLegend(readingColor: Color, pumpingColor: Color) {
    Row(modifier = Modifier.padding(top = 8.dp)) {
        LegendItem(color = readingColor, shape = CircleShape, label = "Odczyt licznika")
        LegendItem(color = pumpingColor, shape = CutCornerShape(50), label = "Wywóz ścieków", modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun LegendItem(color: Color, shape: androidx.compose.ui.graphics.Shape, label: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, shape)
                .clearAndSetSemantics {},
        )
        Text(label, modifier = Modifier.padding(start = 4.dp))
    }
}

private class YScale(val max: Float, val labelCount: Int, val decimals: Int)

/** 0..[maxValue] rounded up to a 1/2/5 x 10^n step, aiming for about 5 intervals. */
private fun niceYScale(maxValue: Float): YScale {
    if (maxValue <= 0f) return YScale(max = 1f, labelCount = 2, decimals = 0)
    val rawStep = maxValue / 5.0
    val magnitude = 10.0.pow(floor(log10(rawStep)))
    val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * magnitude }.first { it >= rawStep }
    val intervals = ceil(maxValue / step - 1e-6).toInt().coerceAtLeast(1)
    val decimals = (-floor(log10(step))).toInt().coerceAtLeast(0)
    return YScale(max = (intervals * step).toFloat(), labelCount = intervals + 1, decimals = decimals)
}

/** Chart entry that carries its [HistoryPoint], so the marker can show the point's details. */
private class HistoryChartEntry(
    val point: HistoryPoint,
    override val x: Float,
    override val y: Float,
) : ChartEntry {
    override fun withY(y: Float): ChartEntry = HistoryChartEntry(point, x, y)
}

private fun startOfDay(timestampMillis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestampMillis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Whole days (integer-valued, see HistoryChart) between [firstDayStartMillis] and the point's day. */
private fun dayIndex(timestampMillis: Long, firstDayStartMillis: Long): Float =
    // round() absorbs the +/-1h day-length shift across DST changes.
    Math.round((startOfDay(timestampMillis) - firstDayStartMillis) / MILLIS_PER_DAY.toDouble()).toFloat()

private fun fillValue(point: HistoryPoint, isCapacityConfigured: Boolean): Float =
    if (isCapacityConfigured) (point.fillPercentOfCapacity ?: 0.0).toFloat() else point.fillLiters / 1000f

private fun toEntry(point: HistoryPoint, firstDayStartMillis: Long, isCapacityConfigured: Boolean) = HistoryChartEntry(
    point = point,
    x = dayIndex(point.timestampMillis, firstDayStartMillis),
    y = fillValue(point, isCapacityConfigured),
)

/** Shows both units together (percent and m3) whenever the percent is known, per user request. */
private fun formatValue(point: HistoryPoint): String {
    val m3 = "%.3f m³".format(point.fillLiters / 1000.0)
    val percent = point.fillPercentOfCapacity
    return if (percent != null) "%.0f%% ($m3)".format(percent) else m3
}

private fun readingSourceLabel(source: ReadingSource?): String = when (source) {
    ReadingSource.AUTO_OCR -> "odczyt automatyczny (OCR)"
    ReadingSource.MANUAL_CORRECTED -> "OCR poprawiony ręcznie"
    ReadingSource.MANUAL_ENTERED -> "wpisany ręcznie"
    null -> ""
}

private fun formatPointDetails(
    point: HistoryPoint,
    dateFormat: SimpleDateFormat,
    readingPointsBySourceId: Map<Long?, HistoryPoint>,
): String {
    val date = dateFormat.format(Date(point.timestampMillis))
    return when (point.type) {
        HistoryPointType.READING -> {
            val anomalyMark = if (point.isAnomalous) " (nietypowy odczyt)" else ""
            "$date\n${formatValue(point)} — ${readingSourceLabel(point.readingSource)}$anomalyMark"
        }
        HistoryPointType.PUMPING -> {
            val baseline = readingPointsBySourceId[point.sourceReadingId]
            val baselineText = baseline?.let { "\nOdczyt bazowy: ${formatValue(it)}" } ?: ""
            "Wywóz ścieków\n$date$baselineText"
        }
    }
}
