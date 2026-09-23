package pl.watershed.septictank.domain.history

import pl.watershed.septictank.data.db.entities.MeterReadingEntity
import pl.watershed.septictank.data.db.entities.PumpingEventEntity
import pl.watershed.septictank.data.db.entities.TankConfigurationEntity

/**
 * Converts the raw reading/pumping history into chart-ready [HistoryPoint]s (data-model.md ->
 * "Reguła wyliczania fillLiters"). Applies the same baseline rule as
 * [pl.watershed.septictank.domain.usage.UsageCalculator], but per historical point instead of
 * only for the latest reading: for each reading, the active baseline is the reading referenced by
 * the latest pumping event whose timestamp is at or before that reading's timestamp, or -- if no
 * such event exists -- the very first recorded reading overall.
 */
object HistoryTrendCalculator {

    fun calculate(
        readings: List<MeterReadingEntity>,
        pumpingEvents: List<PumpingEventEntity>,
        configuration: TankConfigurationEntity,
    ): List<HistoryPoint> {
        if (readings.isEmpty()) return emptyList()

        val readingsById = readings.associateBy { it.id }
        val sortedReadings = readings.sortedBy { it.timestampMillis }
        val sortedPumpingEvents = pumpingEvents.sortedBy { it.timestampMillis }
        val firstReadingValueLiters = sortedReadings.first().valueLiters
        val capacityLiters = configuration.capacityLiters

        fun fillPercent(fillLiters: Long): Double? =
            if (capacityLiters == null || capacityLiters <= 0) {
                null
            } else {
                fillLiters * 100.0 / capacityLiters
            }

        fun activeBaselineValueLiters(timestampMillis: Long): Long {
            val activeEvent = sortedPumpingEvents.lastOrNull { it.timestampMillis <= timestampMillis }
            val baselineReading = activeEvent?.let { readingsById[it.baselineReadingId] }
            return baselineReading?.valueLiters ?: firstReadingValueLiters
        }

        val readingPoints = sortedReadings.map { reading ->
            val fillLiters = (reading.valueLiters - activeBaselineValueLiters(reading.timestampMillis))
                .coerceAtLeast(0)
            HistoryPoint(
                timestampMillis = reading.timestampMillis,
                type = HistoryPointType.READING,
                fillLiters = fillLiters,
                fillPercentOfCapacity = fillPercent(fillLiters),
                sourceReadingId = reading.id,
                pumpingEventId = null,
                readingSource = reading.source,
                isAnomalous = reading.isAnomalous,
            )
        }

        val pumpingPoints = sortedPumpingEvents.map { event ->
            HistoryPoint(
                timestampMillis = event.timestampMillis,
                type = HistoryPointType.PUMPING,
                fillLiters = 0,
                fillPercentOfCapacity = fillPercent(0),
                sourceReadingId = event.baselineReadingId,
                pumpingEventId = event.id,
                readingSource = null,
                isAnomalous = false,
            )
        }

        return (readingPoints + pumpingPoints).sortedBy { it.timestampMillis }
    }
}
