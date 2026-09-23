package pl.watershed.septictank.domain.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.watershed.septictank.data.db.entities.MeterReadingEntity
import pl.watershed.septictank.data.db.entities.PumpingEventEntity
import pl.watershed.septictank.data.db.entities.ReadingSource
import pl.watershed.septictank.data.db.entities.TankConfigurationEntity

class HistoryTrendCalculatorTest {

    private fun reading(id: Long, timestampMillis: Long, valueLiters: Long, isAnomalous: Boolean = false) =
        MeterReadingEntity(
            id = id,
            timestampMillis = timestampMillis,
            valueLiters = valueLiters,
            photoPath = null,
            source = ReadingSource.MANUAL_ENTERED,
            isAnomalous = isAnomalous,
        )

    private fun configuration(capacityLiters: Long?) =
        TankConfigurationEntity(capacityLiters = capacityLiters)

    @Test
    fun `no pumping events - baseline is the first reading`() {
        val readings = listOf(
            reading(id = 1, timestampMillis = 1_000, valueLiters = 5_000),
            reading(id = 2, timestampMillis = 2_000, valueLiters = 5_300),
        )

        val points = HistoryTrendCalculator.calculate(readings, emptyList(), configuration(null))

        assertEquals(2, points.size)
        assertEquals(0L, points[0].fillLiters)
        assertEquals(300L, points[1].fillLiters)
    }

    @Test
    fun `single pumping cycle - fill resets at the pumping event`() {
        val readings = listOf(
            reading(id = 1, timestampMillis = 1_000, valueLiters = 5_000),
            reading(id = 2, timestampMillis = 2_000, valueLiters = 5_300),
            reading(id = 3, timestampMillis = 4_000, valueLiters = 5_450),
        )
        val pumpingEvents = listOf(
            PumpingEventEntity(id = 1, timestampMillis = 3_000, baselineReadingId = 2),
        )

        val points = HistoryTrendCalculator.calculate(readings, pumpingEvents, configuration(null))

        val fillByTimestamp = points.associate { it.timestampMillis to it.fillLiters }
        assertEquals(0L, fillByTimestamp[1_000])
        assertEquals(300L, fillByTimestamp[2_000])
        assertEquals(0L, fillByTimestamp[3_000]) // pumping point itself
        assertEquals(150L, fillByTimestamp[4_000]) // 5450 - 5300 (baseline reset at pumping)
    }

    @Test
    fun `multiple pumping cycles - each uses its own active baseline`() {
        val readings = listOf(
            reading(id = 1, timestampMillis = 1_000, valueLiters = 1_000),
            reading(id = 2, timestampMillis = 2_000, valueLiters = 1_200), // baseline for cycle 1
            reading(id = 3, timestampMillis = 4_000, valueLiters = 1_250),
            reading(id = 4, timestampMillis = 5_000, valueLiters = 1_400), // baseline for cycle 2
            reading(id = 5, timestampMillis = 7_000, valueLiters = 1_480),
        )
        val pumpingEvents = listOf(
            PumpingEventEntity(id = 1, timestampMillis = 3_000, baselineReadingId = 2),
            PumpingEventEntity(id = 2, timestampMillis = 6_000, baselineReadingId = 4),
        )

        val points = HistoryTrendCalculator.calculate(readings, pumpingEvents, configuration(null))

        val fillByTimestamp = points.associate { it.timestampMillis to it.fillLiters }
        assertEquals(0L, fillByTimestamp[1_000]) // before first pumping -> baseline = first reading
        assertEquals(200L, fillByTimestamp[2_000])
        assertEquals(50L, fillByTimestamp[4_000]) // 1250 - 1200 (cycle 1 baseline)
        // reading 4 becomes cycle 2's baseline only once pumping event 2 fires at 6_000; at its own
        // timestamp (5_000) it is still measured against cycle 1's baseline (1200), not itself.
        assertEquals(200L, fillByTimestamp[5_000]) // 1400 - 1200 (still cycle 1 baseline)
        assertEquals(80L, fillByTimestamp[7_000]) // 1480 - 1400 (cycle 2 baseline)
    }

    @Test
    fun `reading lower than baseline - fillLiters clamped to zero, not negative`() {
        val readings = listOf(
            reading(id = 1, timestampMillis = 1_000, valueLiters = 5_000),
            reading(id = 2, timestampMillis = 2_000, valueLiters = 4_900, isAnomalous = true),
        )

        val points = HistoryTrendCalculator.calculate(readings, emptyList(), configuration(null))

        assertEquals(0L, points[1].fillLiters)
        assertTrue(points[1].isAnomalous)
    }

    @Test
    fun `capacity not configured - fillPercentOfCapacity is null`() {
        val readings = listOf(reading(id = 1, timestampMillis = 1_000, valueLiters = 5_000))

        val pointsNoCapacity = HistoryTrendCalculator.calculate(readings, emptyList(), configuration(null))
        assertNull(pointsNoCapacity[0].fillPercentOfCapacity)

        val pointsZeroCapacity = HistoryTrendCalculator.calculate(readings, emptyList(), configuration(0))
        assertNull(pointsZeroCapacity[0].fillPercentOfCapacity)
    }

    @Test
    fun `capacity configured - fillPercentOfCapacity is computed`() {
        val readings = listOf(
            reading(id = 1, timestampMillis = 1_000, valueLiters = 1_000),
            reading(id = 2, timestampMillis = 2_000, valueLiters = 1_500),
        )

        val points = HistoryTrendCalculator.calculate(readings, emptyList(), configuration(2_000))

        assertEquals(25.0, points[1].fillPercentOfCapacity!!, 0.0001)
    }

    @Test
    fun `pumping point always has zero fill`() {
        val readings = listOf(reading(id = 1, timestampMillis = 1_000, valueLiters = 5_000))
        val pumpingEvents = listOf(
            PumpingEventEntity(id = 1, timestampMillis = 2_000, baselineReadingId = 1),
        )

        val points = HistoryTrendCalculator.calculate(readings, pumpingEvents, configuration(null))

        val pumpingPoint = points.single { it.type == HistoryPointType.PUMPING }
        assertEquals(0L, pumpingPoint.fillLiters)
        assertEquals(1L, pumpingPoint.pumpingEventId)
        assertEquals(1L, pumpingPoint.sourceReadingId) // baseline reading id, for the tap-detail marker
        assertNull(pumpingPoint.readingSource)
    }

    @Test
    fun `result is sorted chronologically regardless of input order`() {
        val readings = listOf(
            reading(id = 2, timestampMillis = 5_000, valueLiters = 200),
            reading(id = 1, timestampMillis = 1_000, valueLiters = 100),
        )
        val pumpingEvents = listOf(
            PumpingEventEntity(id = 1, timestampMillis = 3_000, baselineReadingId = 1),
        )

        val points = HistoryTrendCalculator.calculate(readings, pumpingEvents, configuration(null))

        assertEquals(listOf(1_000L, 3_000L, 5_000L), points.map { it.timestampMillis })
    }

    @Test
    fun `empty history returns an empty list`() {
        val points = HistoryTrendCalculator.calculate(emptyList(), emptyList(), configuration(null))

        assertTrue(points.isEmpty())
    }
}
