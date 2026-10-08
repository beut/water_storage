package pl.watershed.septictank.domain.pumping

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.watershed.septictank.data.db.entities.MeterReadingEntity
import pl.watershed.septictank.data.db.entities.PumpingEventEntity
import pl.watershed.septictank.data.db.entities.ReadingSource

class PumpingHistoryCalculatorTest {

    private val zone = ZoneId.of("Europe/Warsaw")

    private fun millis(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun reading(id: Long, at: String, valueLiters: Long) = MeterReadingEntity(
        id = id,
        timestampMillis = millis(at),
        valueLiters = valueLiters,
        photoPath = null,
        source = ReadingSource.MANUAL_ENTERED,
        isAnomalous = false,
    )

    private fun pumping(id: Long, at: String, baselineReadingId: Long) =
        PumpingEventEntity(id = id, timestampMillis = millis(at), baselineReadingId = baselineReadingId)

    private val readings = listOf(
        reading(1, "2026-06-01T08:00", 10_000),
        reading(2, "2026-07-10T08:00", 16_000),
        reading(3, "2026-08-20T08:00", 22_500),
        reading(4, "2026-09-25T08:00", 28_000),
    )

    private val pumpings = listOf(
        // Deliberately unsorted.
        pumping(12, "2026-08-20T15:00", baselineReadingId = 3),
        pumping(11, "2026-07-10T12:00", baselineReadingId = 2),
        pumping(13, "2026-09-25T09:00", baselineReadingId = 4),
    )

    @Test
    fun `entries are newest first with days and volume between pumpings`() {
        val history = PumpingHistoryCalculator.calculate(readings, pumpings, capacityLiters = 10_000, zone = zone)

        assertEquals(listOf(13L, 12L, 11L), history.entries.map { it.pumpingEventId })

        val newest = history.entries[0]
        assertEquals(36L, newest.daysSincePrevious) // 20.08 -> 25.09
        assertFalse(newest.isFirstRecorded)
        assertEquals(5_500L, newest.volumeLiters) // 28 000 - 22 500
        assertEquals(55.0, newest.volumePercentOfCapacity!!, 1e-6)

        val middle = history.entries[1]
        assertEquals(41L, middle.daysSincePrevious) // 10.07 -> 20.08
        assertEquals(6_500L, middle.volumeLiters)
    }

    @Test
    fun `first recorded pumping has no interval nor volume`() {
        // The tank wasn't necessarily empty at the first reading, so nothing is measured from it.
        val first = PumpingHistoryCalculator.calculate(readings, pumpings, capacityLiters = 10_000, zone = zone).entries.last()
        assertTrue(first.isFirstRecorded)
        assertNull(first.daysSincePrevious)
        assertNull(first.volumeLiters)
        assertNull(first.volumePercentOfCapacity)
    }

    @Test
    fun `summary averages only full cycles between pumpings`() {
        val summary = PumpingHistoryCalculator.calculate(readings, pumpings, capacityLiters = 10_000, zone = zone).summary
        assertEquals(3, summary.count)
        assertEquals(38.5, summary.averageIntervalDays!!, 1e-6) // (41 + 36) / 2
        assertEquals(6_000.0, summary.averageVolumeLiters!!, 1e-6) // (6 500 + 5 500) / 2
    }

    @Test
    fun `single pumping has nothing to average`() {
        val history = PumpingHistoryCalculator.calculate(readings, listOf(pumpings[1]), capacityLiters = 10_000, zone = zone)
        assertEquals(PumpingHistorySummary(count = 1, averageIntervalDays = null, averageVolumeLiters = null), history.summary)
    }

    @Test
    fun `no reading between pumpings leaves the volume unknown`() {
        val samePumpingBaseline = listOf(
            pumping(21, "2026-07-10T12:00", baselineReadingId = 2),
            pumping(22, "2026-08-01T12:00", baselineReadingId = 2),
        )
        val history = PumpingHistoryCalculator.calculate(readings, samePumpingBaseline, capacityLiters = 10_000, zone = zone)
        val newest = history.entries.first()
        assertEquals(22L, newest.daysSincePrevious)
        assertNull(newest.volumeLiters)
        assertNull(history.summary.averageVolumeLiters)
    }

    @Test
    fun `no capacity gives volume without percent`() {
        val entry = PumpingHistoryCalculator.calculate(readings, pumpings, capacityLiters = null, zone = zone).entries.first()
        assertEquals(5_500L, entry.volumeLiters)
        assertNull(entry.volumePercentOfCapacity)
    }

    @Test
    fun `no pumpings gives an empty history`() {
        val history = PumpingHistoryCalculator.calculate(readings, emptyList(), capacityLiters = 10_000, zone = zone)
        assertTrue(history.entries.isEmpty())
        assertEquals(PumpingHistorySummary(count = 0, averageIntervalDays = null, averageVolumeLiters = null), history.summary)
    }
}
