package pl.watershed.septictank.domain.pumping

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import pl.watershed.septictank.data.db.entities.MeterReadingEntity
import pl.watershed.septictank.data.db.entities.PumpingEventEntity

/** One pumping on the pumping history screen. */
data class PumpingHistoryEntry(
    val pumpingEventId: Long,
    val timestampMillis: Long,
    /** Calendar days since the previous pumping; `null` for the first recorded pumping. */
    val daysSincePrevious: Long?,
    /** The first recorded pumping has no previous one, so its interval and volume are unknown. */
    val isFirstRecorded: Boolean,
    /**
     * Water used between the previous pumping and this one, i.e. the fill when it was emptied;
     * `null` for the first pumping or when no new reading was taken in between.
     */
    val volumeLiters: Long?,
    val volumePercentOfCapacity: Double?,
)

data class PumpingHistorySummary(
    val count: Int,
    /** Average days between consecutive pumpings; `null` with fewer than two pumpings. */
    val averageIntervalDays: Double?,
    val averageVolumeLiters: Double?,
)

data class PumpingHistory(
    /** Newest first. */
    val entries: List<PumpingHistoryEntry>,
    val summary: PumpingHistorySummary,
)

/**
 * Builds the pumping history from the stored events. A pumping's volume is the difference between
 * its baseline reading and the previous pumping's baseline -- the same baseline rule as
 * UsageCalculator. The first recorded pumping gets neither interval nor volume: the tank wasn't
 * necessarily empty at the first reading, so there is no known cycle start to measure from.
 */
object PumpingHistoryCalculator {

    fun calculate(
        readings: List<MeterReadingEntity>,
        pumpingEvents: List<PumpingEventEntity>,
        capacityLiters: Long?,
        zone: ZoneId,
    ): PumpingHistory {
        val readingsById = readings.associateBy { it.id }
        val capacity = capacityLiters?.takeIf { it > 0 }
        val sortedEvents = pumpingEvents.sortedBy { it.timestampMillis }

        val entries = sortedEvents.mapIndexed { index, event ->
            val previous = sortedEvents.getOrNull(index - 1)
            // Same baseline reading as the previous pumping = no reading in between, volume unknown.
            val volumeLiters = if (previous == null || previous.baselineReadingId == event.baselineReadingId) {
                null
            } else {
                val startLiters = readingsById[previous.baselineReadingId]?.valueLiters
                val endLiters = readingsById[event.baselineReadingId]?.valueLiters
                if (startLiters != null && endLiters != null) (endLiters - startLiters).coerceAtLeast(0) else null
            }
            PumpingHistoryEntry(
                pumpingEventId = event.id,
                timestampMillis = event.timestampMillis,
                daysSincePrevious = previous?.let { calendarDaysBetween(it.timestampMillis, event.timestampMillis, zone) },
                isFirstRecorded = previous == null,
                volumeLiters = volumeLiters,
                volumePercentOfCapacity = if (volumeLiters != null && capacity != null) volumeLiters * 100.0 / capacity else null,
            )
        }

        val intervals = entries.mapNotNull { it.daysSincePrevious }
        val volumes = entries.mapNotNull { it.volumeLiters }
        return PumpingHistory(
            entries = entries.reversed(),
            summary = PumpingHistorySummary(
                count = entries.size,
                averageIntervalDays = intervals.takeIf { it.isNotEmpty() }?.average(),
                averageVolumeLiters = volumes.takeIf { it.isNotEmpty() }?.average(),
            ),
        )
    }

    private fun calendarDaysBetween(fromMillis: Long, toMillis: Long, zone: ZoneId): Long =
        ChronoUnit.DAYS.between(localDate(fromMillis, zone), localDate(toMillis, zone)).coerceAtLeast(0)

    private fun localDate(millis: Long, zone: ZoneId) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
