package pl.watershed.septictank.domain.forecast

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.watershed.septictank.data.db.entities.MeterReadingEntity
import pl.watershed.septictank.data.db.entities.ReadingSource

class PumpingForecastCalculatorTest {

    private val zone = ZoneId.of("Europe/Warsaw")

    private fun millis(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun reading(at: String, valueLiters: Long) = MeterReadingEntity(
        timestampMillis = millis(at),
        valueLiters = valueLiters,
        photoPath = null,
        source = ReadingSource.MANUAL_ENTERED,
        isAnomalous = false,
    )

    private fun calculate(
        readings: List<MeterReadingEntity>,
        lastPumping: String? = null,
        currentUsageLiters: Long = 0,
        capacityLiters: Long? = 10_000,
        now: String,
    ) = PumpingForecastCalculator.calculate(
        readings = readings,
        lastPumpingMillis = lastPumping?.let(::millis),
        currentUsageLiters = currentUsageLiters,
        capacityLiters = capacityLiters,
        nowMillis = millis(now),
        zone = zone,
    )

    @Test
    fun `days since pumping counts calendar days`() {
        val readings = listOf(reading("2026-10-01T08:00", 1_000))
        assertEquals(0L, calculate(readings, lastPumping = "2026-10-05T07:00", now = "2026-10-05T20:00").daysSinceLastPumping)
        assertEquals(1L, calculate(readings, lastPumping = "2026-10-04T23:00", now = "2026-10-05T08:00").daysSinceLastPumping)
        assertEquals(12L, calculate(readings, lastPumping = "2026-09-23T12:00", now = "2026-10-05T08:00").daysSinceLastPumping)
    }

    @Test
    fun `no pumping recorded gives null`() {
        assertNull(calculate(listOf(reading("2026-10-01T08:00", 1_000)), now = "2026-10-05T08:00").daysSinceLastPumping)
    }

    @Test
    fun `average daily fill uses the whole history`() {
        val readings = listOf(
            reading("2026-10-01T08:00", 1_000),
            reading("2026-10-03T08:00", 1_300),
            reading("2026-10-05T08:00", 1_800),
        )
        val forecast = calculate(readings, now = "2026-10-05T08:00")
        assertEquals(200.0, forecast.averageDailyLiters!!, 1e-6)
        assertEquals(2.0, forecast.averageDailyPercent!!, 1e-6)
    }

    @Test
    fun `meter decrease is ignored instead of making the rate negative`() {
        val readings = listOf(
            reading("2026-10-01T08:00", 5_000),
            reading("2026-10-02T08:00", 5_400),
            reading("2026-10-03T08:00", 100),
            reading("2026-10-05T08:00", 500),
        )
        assertEquals(200.0, calculate(readings, now = "2026-10-05T08:00").averageDailyLiters!!, 1e-6)
    }

    @Test
    fun `less than one day of readings is not enough for an average`() {
        val readings = listOf(reading("2026-10-05T08:00", 1_000), reading("2026-10-05T20:00", 1_200))
        val forecast = calculate(readings, now = "2026-10-05T20:00")
        assertNull(forecast.averageDailyLiters)
        assertNull(forecast.daysUntilPumping)
    }

    @Test
    fun `days until 90 percent extrapolates from the latest reading to now`() {
        // 200 l/day, capacity 10 000 l -> threshold 9 000 l. Fill 5 000 l at the latest reading,
        // one day ago -> ~5 200 l now -> (9 000 - 5 200) / 200 = 19 days.
        val readings = listOf(reading("2026-09-25T08:00", 1_000), reading("2026-10-04T08:00", 2_800))
        val forecast = calculate(readings, currentUsageLiters = 5_000, now = "2026-10-05T08:00")
        assertEquals(52.0, forecast.estimatedFillPercentNow!!, 1e-6)
        assertEquals(19.0, forecast.daysUntilPumping!!, 1e-6)
    }

    @Test
    fun `already above the threshold gives zero days`() {
        val readings = listOf(reading("2026-10-01T08:00", 1_000), reading("2026-10-05T08:00", 1_800))
        assertEquals(0.0, calculate(readings, currentUsageLiters = 9_500, now = "2026-10-05T08:00").daysUntilPumping!!, 1e-6)
    }

    @Test
    fun `no capacity gives average but no forecast`() {
        val readings = listOf(reading("2026-10-01T08:00", 1_000), reading("2026-10-05T08:00", 1_800))
        val forecast = calculate(readings, capacityLiters = null, now = "2026-10-05T08:00")
        assertEquals(200.0, forecast.averageDailyLiters!!, 1e-6)
        assertNull(forecast.averageDailyPercent)
        assertNull(forecast.daysUntilPumping)
    }

    @Test
    fun `zero usage cannot be forecast`() {
        val readings = listOf(reading("2026-10-01T08:00", 1_000), reading("2026-10-05T08:00", 1_000))
        val forecast = calculate(readings, currentUsageLiters = 0, now = "2026-10-05T08:00")
        assertEquals(0.0, forecast.averageDailyLiters!!, 1e-6)
        assertNull(forecast.daysUntilPumping)
    }
}
