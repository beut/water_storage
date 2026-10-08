package pl.watershed.septictank.ui.history

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import pl.watershed.septictank.domain.history.HistoryPoint
import pl.watershed.septictank.domain.history.HistoryPointType

internal const val MILLIS_PER_DAY = 86_400_000L

/** Time window shown on the History chart; [days] = `null` means the whole history. */
internal enum class HistoryPeriod(val label: String, val days: Long?) {
    WEEK("7 dni", 7),
    MONTH("30 dni", 30),
    QUARTER("90 dni", 90),
    ALL("Wszystko", null),
}

/** A selectable point on the chart (reading or pumping) with its plotted value. */
internal data class ChartMarker(val point: HistoryPoint, val value: Double)

/** Y axis from 0 to [max] with a label every [step] (1/2/5 x 10^n), [decimals] digits per label. */
internal data class YScale(val max: Double, val step: Double, val decimals: Int) {
    val ticks: List<Double> get() = (0..Math.round(max / step).toInt()).map { it * step }
}

/**
 * Everything the History chart draws, in data coordinates (time in millis, value in % or m3).
 * The whole window always fits the chart width -- no horizontal scrolling.
 */
internal data class TrendChartModel(
    val startMillis: Long,
    val endMillis: Long,
    val yScale: YScale,
    /** Polyline vertices: readings, plus a vertical drop to 0 at every pumping (sawtooth). */
    val line: List<Pair<Long, Double>>,
    /** Points inside the window, oldest first. */
    val markers: List<ChartMarker>,
    val thresholdValue: Double?,
    /** Local-midnight timestamps for the date labels. */
    val xTicks: List<Long>,
) {
    /** Marker closest in time to [timeMillis], for tap/drag selection. */
    fun nearestMarker(timeMillis: Long): ChartMarker? = markers.minByOrNull { abs(it.point.timestampMillis - timeMillis) }

    companion object {
        private const val MAX_X_TICKS = 4

        /**
         * @param isPercent plot % of capacity (capacity configured) instead of m3.
         * @return `null` when there is nothing to draw.
         */
        fun build(
            points: List<HistoryPoint>,
            period: HistoryPeriod,
            isPercent: Boolean,
            thresholdPercent: Int?,
            zone: ZoneId,
        ): TrendChartModel? {
            if (points.isEmpty()) return null
            val sorted = points.sortedBy { it.timestampMillis }
            fun valueOf(point: HistoryPoint) =
                if (isPercent) point.fillPercentOfCapacity ?: 0.0 else point.fillLiters / 1000.0

            var endMillis = sorted.last().timestampMillis
            var startMillis = period.days?.let { maxOf(endMillis - it * MILLIS_PER_DAY, sorted.first().timestampMillis) }
                ?: sorted.first().timestampMillis
            if (endMillis - startMillis < MILLIS_PER_DAY) {
                // A single point or a very short span: centre it in a one-day window.
                val middle = (startMillis + endMillis) / 2
                startMillis = middle - MILLIS_PER_DAY / 2
                endMillis = middle + MILLIS_PER_DAY / 2
            }

            val line = mutableListOf<Pair<Long, Double>>()
            for (point in sorted) {
                val time = point.timestampMillis
                when (point.type) {
                    HistoryPointType.READING -> line += time to valueOf(point)
                    HistoryPointType.PUMPING -> {
                        line.lastOrNull()?.let { (_, lastValue) -> line += time to lastValue }
                        line += time to 0.0
                    }
                }
            }

            val markers = sorted.filter { it.timestampMillis in startMillis..endMillis }.map { ChartMarker(it, valueOf(it)) }

            // Scale to what is visible: vertices in the window plus the one leading into it.
            val firstVisible = line.indexOfFirst { it.first >= startMillis }.let { if (it == -1) line.size else it }
            val visibleValues = line.drop((firstVisible - 1).coerceAtLeast(0)).map { it.second }
            val thresholdValue = if (isPercent) thresholdPercent?.toDouble() else null

            return TrendChartModel(
                startMillis = startMillis,
                endMillis = endMillis,
                yScale = niceYScale(visibleValues.maxOrNull() ?: 0.0),
                line = line,
                markers = markers,
                thresholdValue = thresholdValue,
                xTicks = dayTicks(startMillis, endMillis, zone),
            )
        }

        /** 0..[maxValue] rounded up to a 1/2/5 x 10^n step, aiming for about 4 intervals. */
        fun niceYScale(maxValue: Double): YScale {
            if (maxValue <= 0.0) return YScale(max = 1.0, step = 1.0, decimals = 0)
            val rawStep = maxValue / 4.0
            val magnitude = 10.0.pow(floor(log10(rawStep)))
            val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * magnitude }.first { it >= rawStep - 1e-12 }
            val intervals = ceil(maxValue / step - 1e-9).toInt().coerceAtLeast(1)
            val decimals = (-floor(log10(step))).toInt().coerceAtLeast(0)
            return YScale(max = intervals * step, step = step, decimals = decimals)
        }

        /** Up to [MAX_X_TICKS] local midnights inside [startMillis, endMillis], evenly spaced in whole days. */
        fun dayTicks(startMillis: Long, endMillis: Long, zone: ZoneId): List<Long> {
            val firstDay = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
            val lastDay = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
            val spanDays = lastDay.toEpochDay() - firstDay.toEpochDay()
            val stepDays = ceil((spanDays + 1) / MAX_X_TICKS.toDouble()).toLong().coerceAtLeast(1)
            return generateSequence(firstDay) { it.plusDays(stepDays) }
                .takeWhile { !it.isAfter(lastDay) }
                .map { it.atStartOfDay(zone).toInstant().toEpochMilli() }
                // A midnight before the window start would be clipped; nudge it to the window start.
                .map { maxOf(it, startMillis) }
                .distinct()
                .toList()
        }
    }
}
