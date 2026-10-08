package pl.watershed.septictank.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import pl.watershed.septictank.SepticTankApplication
import pl.watershed.septictank.data.db.entities.ReadingSource
import pl.watershed.septictank.domain.history.HistoryPoint
import pl.watershed.septictank.domain.history.HistoryPointType
import pl.watershed.septictank.ui.common.AppCard

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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć") }
                Text("Historia zbiornika", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }

            if (state.chartPoints.isEmpty()) {
                // FR-007: friendly empty state instead of an empty/broken chart.
                AppCard {
                    Text(
                        "Brak jeszcze żadnych danych. Zrób zdjęcie licznika lub wpisz odczyt ręcznie, " +
                            "aby zobaczyć tutaj wykres wypełnienia zbiornika.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                HistoryContent(state)
            }
        }
    }
}

@Composable
private fun HistoryContent(state: HistoryUiState) {
    var period by rememberSaveable { mutableStateOf(HistoryPeriod.MONTH) }
    val zone = remember { ZoneId.systemDefault() }
    val model = remember(state, period) {
        TrendChartModel.build(state.chartPoints, period, state.isCapacityConfigured, state.warningThresholdPercent, zone)
    } ?: return
    // Default selection: the newest point, so the details card always shows something.
    var selectedId by remember(model) { mutableStateOf(model.markers.lastOrNull()?.point?.key()) }
    val selected = model.markers.firstOrNull { it.point.key() == selectedId }

    // Scrollable so the four chips never get squeezed on narrow phones.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        HistoryPeriod.entries.forEach { option ->
            FilterChip(selected = option == period, onClick = { period = option }, label = { Text(option.label) })
        }
    }

    AppCard {
        Text(
            if (state.isCapacityConfigured) "Wypełnienie zbiornika (%)" else "Wypełnienie zbiornika (m³)",
            style = MaterialTheme.typography.titleMedium,
        )
        if (!state.isCapacityConfigured) {
            // FR-004: percent fill requires a configured tank capacity.
            Text(
                "Skonfiguruj pojemność zbiornika w Ustawieniach, aby zobaczyć wypełnienie w procentach.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        TrendChart(
            model = model,
            isPercent = state.isCapacityConfigured,
            selected = selected,
            onSelect = { selectedId = it.point.key() },
            zone = zone,
        )
        HistoryLegend(hasThreshold = model.thresholdValue?.let { it <= model.yScale.max } == true)
    }

    selected?.let { PointDetailsCard(it.point, state.chartPoints) }
}

/** Stable identity of a chart point across recompositions. */
private fun HistoryPoint.key(): String =
    if (type == HistoryPointType.PUMPING) "p$pumpingEventId" else "r$sourceReadingId"

/**
 * Fill-level line chart that always fits the available width (FR-001/FR-002). Tap or drag
 * horizontally to select the nearest point (FR-005).
 */
@Composable
private fun TrendChart(
    model: TrendChartModel,
    isPercent: Boolean,
    selected: ChartMarker?,
    onSelect: (ChartMarker) -> Unit,
    zone: ZoneId,
) {
    val textMeasurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.primary
    val pumpingColor = MaterialTheme.colorScheme.secondary
    val anomalyColor = MaterialTheme.colorScheme.error
    val thresholdColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val labelStyle = TextStyle(fontSize = 11.sp, color = labelColor)
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd.MM") }

    val yLabels = model.yScale.ticks.map { formatAxisValue(it, model.yScale.decimals, isPercent) }
    val axisWidthPx = yLabels.maxOf { textMeasurer.measure(it, labelStyle).size.width }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .semantics { contentDescription = "Wykres wypełnienia zbiornika. Dotknij lub przesuń palcem, aby zobaczyć szczegóły punktu." }
            .pointerInput(model) {
                val plotLeft = axisWidthPx + 8.dp.toPx()
                fun select(x: Float) {
                    val fraction = ((x - plotLeft) / (size.width - plotLeft)).coerceIn(0f, 1f)
                    val time = model.startMillis + ((model.endMillis - model.startMillis) * fraction).toLong()
                    model.nearestMarker(time)?.let(onSelect)
                }
                detectTapGestures { select(it.x) }
            }
            .pointerInput(model) {
                val plotLeft = axisWidthPx + 8.dp.toPx()
                detectHorizontalDragGestures { change, _ ->
                    val fraction = ((change.position.x - plotLeft) / (size.width - plotLeft)).coerceIn(0f, 1f)
                    val time = model.startMillis + ((model.endMillis - model.startMillis) * fraction).toLong()
                    model.nearestMarker(time)?.let(onSelect)
                }
            },
    ) {
        val plotLeft = axisWidthPx + 8.dp.toPx()
        val plotTop = 8.dp.toPx()
        val plotRight = size.width - 6.dp.toPx()
        val plotBottom = size.height - 22.dp.toPx()
        val span = (model.endMillis - model.startMillis).toFloat()
        fun x(time: Long) = plotLeft + (time - model.startMillis) / span * (plotRight - plotLeft)
        fun y(value: Double) = plotBottom - (value / model.yScale.max).toFloat().coerceIn(0f, 1.05f) * (plotBottom - plotTop)

        // Horizontal grid + Y labels.
        model.yScale.ticks.forEachIndexed { index, tick ->
            val gy = y(tick)
            drawLine(gridColor, Offset(plotLeft, gy), Offset(plotRight, gy), strokeWidth = 1.dp.toPx())
            val layout = textMeasurer.measure(yLabels[index], labelStyle)
            drawText(layout, topLeft = Offset(plotLeft - 8.dp.toPx() - layout.size.width, gy - layout.size.height / 2f))
        }

        // Date labels, clamped so the outermost ones stay inside the canvas.
        model.xTicks.forEach { tick ->
            val text = Instant.ofEpochMilli(tick).atZone(zone).format(dateFormat)
            val layout = textMeasurer.measure(text, labelStyle)
            val left = (x(tick) - layout.size.width / 2f).coerceIn(plotLeft - layout.size.width / 2f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(left, plotBottom + 6.dp.toPx()))
        }

        // Warning threshold (FR-009), only when it falls inside the visible scale.
        model.thresholdValue?.takeIf { it <= model.yScale.max }?.let { threshold ->
            val ty = y(threshold)
            drawLine(
                thresholdColor,
                Offset(plotLeft, ty),
                Offset(plotRight, ty),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
            )
        }

        clipRect(left = plotLeft, top = 0f, right = plotRight, bottom = plotBottom + 1.dp.toPx()) {
            drawFillLine(model, ::x, ::y, plotBottom, lineColor)
        }

        // Points (FR-002: pumpings use a distinct diamond shape, not only colour).
        model.markers.forEach { marker ->
            val center = Offset(x(marker.point.timestampMillis), y(marker.value))
            if (marker.point.type == HistoryPointType.PUMPING) {
                drawDiamond(center, 7.dp.toPx(), pumpingColor, surfaceColor)
            } else {
                drawCircle(surfaceColor, radius = 4.5.dp.toPx(), center = center)
                drawCircle(if (marker.point.isAnomalous) anomalyColor else lineColor, radius = 3.dp.toPx(), center = center)
            }
        }

        // Selection: vertical guide + highlighted point.
        selected?.let { marker ->
            val center = Offset(x(marker.point.timestampMillis), y(marker.value))
            drawLine(labelColor.copy(alpha = 0.5f), Offset(center.x, plotTop), Offset(center.x, plotBottom), strokeWidth = 1.dp.toPx())
            val color = if (marker.point.type == HistoryPointType.PUMPING) pumpingColor else lineColor
            drawCircle(color.copy(alpha = 0.25f), radius = 12.dp.toPx(), center = center)
            drawCircle(surfaceColor, radius = 6.dp.toPx(), center = center)
            drawCircle(color, radius = 4.5.dp.toPx(), center = center)
        }
    }
}

private fun DrawScope.drawFillLine(
    model: TrendChartModel,
    x: (Long) -> Float,
    y: (Double) -> Float,
    plotBottom: Float,
    color: Color,
) {
    if (model.line.isEmpty()) return
    val stroke = Path()
    model.line.forEachIndexed { index, (time, value) ->
        if (index == 0) stroke.moveTo(x(time), y(value)) else stroke.lineTo(x(time), y(value))
    }
    val area = Path().apply {
        addPath(stroke)
        lineTo(x(model.line.last().first), plotBottom)
        lineTo(x(model.line.first().first), plotBottom)
        close()
    }
    drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f))))
    drawPath(stroke, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawDiamond(center: Offset, radius: Float, color: Color, outline: Color) {
    fun diamond(r: Float) = Path().apply {
        moveTo(center.x, center.y - r)
        lineTo(center.x + r, center.y)
        lineTo(center.x, center.y + r)
        lineTo(center.x - r, center.y)
        close()
    }
    drawPath(diamond(radius + 1.5.dp.toPx()), outline)
    drawPath(diamond(radius), color)
}

/** FR-002 accessibility intent: shape + text legend, not colour alone. */
@Composable
private fun HistoryLegend(hasThreshold: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(MaterialTheme.colorScheme.primary, CircleShape, "Odczyt")
        LegendItem(MaterialTheme.colorScheme.secondary, CutCornerShape(50), "Wywóz")
        if (hasThreshold) LegendItem(MaterialTheme.colorScheme.tertiary, null, "Próg ostrzeżenia")
    }
}

@Composable
private fun LegendItem(color: Color, shape: Shape?, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .clearAndSetSemantics {}
                .then(if (shape != null) Modifier.size(10.dp).background(color, shape) else Modifier.size(width = 14.dp, height = 2.dp).background(color)),
        )
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp))
    }
}

/** FR-005 / FR-010: exact date, time and value of the selected point -- nothing lost vs. the old lists. */
@Composable
private fun PointDetailsCard(point: HistoryPoint, allPoints: List<HistoryPoint>) {
    val details = remember(point, allPoints) { HistoryTexts.details(point, allPoints, ZoneId.systemDefault()) }
    AppCard {
        Text(details.title, style = MaterialTheme.typography.titleMedium)
        Text(details.dateTime, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        details.rows.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(value, fontWeight = FontWeight.SemiBold)
            }
        }
        details.note?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

// Each number is formatted on its own: a '%' must never reach format() as part of the pattern.
private fun formatAxisValue(value: Double, decimals: Int, isPercent: Boolean): String {
    val number = "%.${decimals}f".format(value)
    return if (isPercent) "$number%" else number
}

/** Details-card strings, kept plain so they can be unit-tested. */
internal object HistoryTexts {
    private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy, HH:mm", Locale("pl", "PL"))

    data class Details(val title: String, val dateTime: String, val rows: List<Pair<String, String>>, val note: String?)

    fun details(point: HistoryPoint, allPoints: List<HistoryPoint>, zone: ZoneId): Details {
        val dateTime = Instant.ofEpochMilli(point.timestampMillis).atZone(zone).format(DATE_TIME_FORMAT)
        return when (point.type) {
            HistoryPointType.READING -> Details(
                title = "Odczyt licznika",
                dateTime = dateTime,
                rows = buildList {
                    add("Wypełnienie" to fill(point))
                    readingSourceLabel(point.readingSource)?.let { add("Źródło" to it) }
                },
                note = if (point.isAnomalous) "Nietypowy odczyt (duży skok zużycia)" else null,
            )
            HistoryPointType.PUMPING -> {
                val baseline = allPoints.firstOrNull { it.type == HistoryPointType.READING && it.sourceReadingId == point.sourceReadingId }
                Details(
                    title = "Wywóz ścieków",
                    dateTime = dateTime,
                    rows = listOfNotNull(baseline?.let { "Wypełnienie przed wywozem" to fill(it) }),
                    note = null,
                )
            }
        }
    }

    /** "52% (5,200 m³)" or "5,200 m³" without capacity. */
    fun fill(point: HistoryPoint): String {
        val m3 = "${"%.3f".format(point.fillLiters / 1000.0)} m³"
        return point.fillPercentOfCapacity?.let { "${"%.0f".format(it)}% ($m3)" } ?: m3
    }

    private fun readingSourceLabel(source: ReadingSource?): String? = when (source) {
        ReadingSource.AUTO_OCR -> "zdjęcie (OCR)"
        ReadingSource.MANUAL_CORRECTED -> "zdjęcie, poprawione ręcznie"
        ReadingSource.MANUAL_ENTERED -> "wpisany ręcznie"
        null -> null
    }
}
