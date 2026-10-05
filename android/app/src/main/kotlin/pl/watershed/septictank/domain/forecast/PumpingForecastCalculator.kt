package pl.watershed.septictank.domain.forecast

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import pl.watershed.septictank.data.db.entities.MeterReadingEntity

/**
 * Home-screen forecast: days since the last pumping, average daily fill and the estimated number
 * of days until the tank reaches [PumpingForecastCalculator.PUMPING_THRESHOLD_PERCENT].
 */
data class PumpingForecast(
    /** Calendar days since the latest pumping (0 = today); `null` when no pumping is recorded. */
    val daysSinceLastPumping: Long?,
    val lastPumpingMillis: Long?,
    /** `null` when the readings span less than [PumpingForecastCalculator.MIN_SPAN_DAYS]. */
    val averageDailyLiters: Double?,
    val averageDailyPercent: Double?,
    /** Fill now, extrapolated from the latest reading; `null` without capacity or average. */
    val estimatedFillPercentNow: Double?,
    /**
     * Days from now until the threshold is reached (0 = already reached); `null` without a
     * configured capacity, without enough data, or when the tank doesn't fill at all.
     */
    val daysUntilPumping: Double?,
)

object PumpingForecastCalculator {

    const val PUMPING_THRESHOLD_PERCENT = 90
    const val MIN_SPAN_DAYS = 1.0
    private const val MILLIS_PER_DAY = 86_400_000.0

    /**
     * @param currentUsageLiters tank fill at the latest reading (UsageCalculator's rule).
     * @param readings the whole reading history, any order.
     */
    fun calculate(
        readings: List<MeterReadingEntity>,
        lastPumpingMillis: Long?,
        currentUsageLiters: Long,
        capacityLiters: Long?,
        nowMillis: Long,
        zone: ZoneId,
    ): PumpingForecast {
        val daysSinceLastPumping = lastPumpingMillis?.let {
            ChronoUnit.DAYS.between(localDate(it, zone), localDate(nowMillis, zone)).coerceAtLeast(0)
        }

        val sorted = readings.sortedBy { it.timestampMillis }
        val averageDailyLiters = averageDailyLiters(sorted)
        val capacity = capacityLiters?.takeIf { it > 0 }

        val averageDailyPercent = if (averageDailyLiters != null && capacity != null) {
            averageDailyLiters * 100.0 / capacity
        } else {
            null
        }

        var estimatedFillPercentNow: Double? = null
        var daysUntilPumping: Double? = null
        if (averageDailyLiters != null && capacity != null) {
            val daysSinceLatestReading = ((nowMillis - sorted.last().timestampMillis) / MILLIS_PER_DAY).coerceAtLeast(0.0)
            val fillNowLiters = currentUsageLiters + averageDailyLiters * daysSinceLatestReading
            estimatedFillPercentNow = fillNowLiters * 100.0 / capacity
            val remainingLiters = capacity * PUMPING_THRESHOLD_PERCENT / 100.0 - fillNowLiters
            daysUntilPumping = when {
                remainingLiters <= 0 -> 0.0
                averageDailyLiters <= 0 -> null
                else -> remainingLiters / averageDailyLiters
            }
        }

        return PumpingForecast(
            daysSinceLastPumping = daysSinceLastPumping,
            lastPumpingMillis = lastPumpingMillis,
            averageDailyLiters = averageDailyLiters,
            averageDailyPercent = averageDailyPercent,
            estimatedFillPercentNow = estimatedFillPercentNow,
            daysUntilPumping = daysUntilPumping,
        )
    }

    /**
     * Water used over the whole history divided by its time span. Only increases between
     * consecutive readings count, so a replaced/reset meter doesn't produce a negative rate.
     */
    private fun averageDailyLiters(sorted: List<MeterReadingEntity>): Double? {
        if (sorted.size < 2) return null
        val spanDays = (sorted.last().timestampMillis - sorted.first().timestampMillis) / MILLIS_PER_DAY
        if (spanDays < MIN_SPAN_DAYS) return null
        val usedLiters = sorted.zipWithNext { previous, next -> (next.valueLiters - previous.valueLiters).coerceAtLeast(0) }.sum()
        return usedLiters / spanDays
    }

    private fun localDate(millis: Long, zone: ZoneId) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
