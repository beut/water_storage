package pl.watershed.septictank.ui.history

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import pl.watershed.septictank.data.db.entities.ReadingSource
import pl.watershed.septictank.domain.history.HistoryPoint
import pl.watershed.septictank.domain.history.HistoryPointType

class TrendChartModelTest {

    private val zone = ZoneId.of("Europe/Warsaw")
    private lateinit var previousLocale: Locale

    @Before
    fun setLocale() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale("pl", "PL"))
    }

    @After
    fun restoreLocale() = Locale.setDefault(previousLocale)

    private fun millis(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun reading(id: Long, at: String, fillLiters: Long, capacity: Long = 10_000, isAnomalous: Boolean = false) = HistoryPoint(
        timestampMillis = millis(at),
        type = HistoryPointType.READING,
        fillLiters = fillLiters,
        fillPercentOfCapacity = fillLiters * 100.0 / capacity,
        sourceReadingId = id,
        pumpingEventId = null,
        readingSource = ReadingSource.MANUAL_ENTERED,
        isAnomalous = isAnomalous,
    )

    private fun pumping(id: Long, at: String, baselineReadingId: Long) = HistoryPoint(
        timestampMillis = millis(at),
        type = HistoryPointType.PUMPING,
        fillLiters = 0,
        fillPercentOfCapacity = 0.0,
        sourceReadingId = baselineReadingId,
        pumpingEventId = id,
        readingSource = null,
        isAnomalous = false,
    )

    // 20 days of daily readings, 100 l/day -- more than the old chart could fit without scrolling.
    private val dailyReadings = (0 until 20).map { day ->
        reading(id = day + 1L, at = LocalDateTime.parse("2026-09-01T08:00").plusDays(day.toLong()).toString(), fillLiters = day * 100L)
    }

    private fun build(points: List<HistoryPoint>, period: HistoryPeriod = HistoryPeriod.ALL, isPercent: Boolean = true, threshold: Int? = 80) =
        TrendChartModel.build(points, period, isPercent, threshold, zone)!!

    @Test
    fun `whole history fits one window without scrolling`() {
        val model = build(dailyReadings)
        assertEquals(millis("2026-09-01T08:00"), model.startMillis)
        assertEquals(millis("2026-09-20T08:00"), model.endMillis)
        assertEquals(20, model.markers.size)
        assertEquals(4, model.xTicks.size) // never more than 4 date labels, however long the history
    }

    @Test
    fun `period limits the window to the last N days`() {
        val model = build(dailyReadings, HistoryPeriod.WEEK)
        assertEquals(millis("2026-09-13T08:00"), model.startMillis)
        assertEquals(8, model.markers.size) // 13..20 September
    }

    @Test
    fun `period longer than the history starts at the first point`() {
        assertEquals(millis("2026-09-01T08:00"), build(dailyReadings, HistoryPeriod.QUARTER).startMillis)
    }

    @Test
    fun `pumping drops the line vertically to zero`() {
        val points = listOf(
            reading(1, "2026-09-01T08:00", 0),
            reading(2, "2026-09-10T08:00", 4_000),
            pumping(7, "2026-09-11T10:00", baselineReadingId = 2),
            reading(3, "2026-09-15T08:00", 500),
        )
        val pumpingTime = millis("2026-09-11T10:00")
        assertEquals(
            listOf(
                millis("2026-09-01T08:00") to 0.0,
                millis("2026-09-10T08:00") to 40.0,
                pumpingTime to 40.0,
                pumpingTime to 0.0,
                millis("2026-09-15T08:00") to 5.0,
            ),
            build(points).line,
        )
    }

    @Test
    fun `y scale fits the visible values in m3 without capacity`() {
        val model = build(dailyReadings, isPercent = false)
        assertEquals(2.0, model.yScale.max, 1e-9) // max 1.9 m3
        assertEquals(listOf(0.0, 0.5, 1.0, 1.5, 2.0), model.yScale.ticks)
        assertEquals(1, model.yScale.decimals)
        assertNull(model.thresholdValue) // threshold is a percentage, meaningless in m3
    }

    private fun assertScale(maxValue: Double, expectedMax: Double, expectedStep: Double, expectedDecimals: Int) {
        val scale = TrendChartModel.niceYScale(maxValue)
        assertEquals("max for $maxValue", expectedMax, scale.max, 1e-9)
        assertEquals("step for $maxValue", expectedStep, scale.step, 1e-9)
        assertEquals("decimals for $maxValue", expectedDecimals, scale.decimals)
    }

    @Test
    fun `nice y scale steps`() {
        assertScale(5.0, expectedMax = 6.0, expectedStep = 2.0, expectedDecimals = 0)
        assertScale(95.0, expectedMax = 100.0, expectedStep = 50.0, expectedDecimals = 0)
        assertScale(0.48, expectedMax = 0.6, expectedStep = 0.2, expectedDecimals = 1)
        assertScale(0.0, expectedMax = 1.0, expectedStep = 1.0, expectedDecimals = 0)
    }

    @Test
    fun `single point gets a one-day window around it`() {
        val model = build(listOf(reading(1, "2026-09-01T12:00", 0)))
        assertEquals(millis("2026-09-01T00:00"), model.startMillis)
        assertEquals(millis("2026-09-02T00:00"), model.endMillis)
        assertEquals(1, model.markers.size)
    }

    @Test
    fun `nearest marker picks the closest point in time`() {
        val model = build(dailyReadings)
        assertEquals(5L, model.nearestMarker(millis("2026-09-05T13:00"))!!.point.sourceReadingId)
        assertEquals(6L, model.nearestMarker(millis("2026-09-05T21:00"))!!.point.sourceReadingId)
        assertEquals(1L, model.nearestMarker(0L)!!.point.sourceReadingId)
    }

    @Test
    fun `no points gives no model`() {
        assertNull(TrendChartModel.build(emptyList(), HistoryPeriod.ALL, isPercent = true, thresholdPercent = 80, zone = zone))
    }

    @Test
    fun `details texts for reading and pumping`() {
        val points = listOf(
            reading(2, "2026-09-10T08:05", 4_000, isAnomalous = true),
            pumping(7, "2026-09-11T10:00", baselineReadingId = 2),
        )
        val readingDetails = HistoryTexts.details(points[0], points, zone)
        assertEquals("Odczyt licznika", readingDetails.title)
        assertEquals("czwartek, 10.09.2026, 08:05", readingDetails.dateTime)
        assertEquals(listOf("Wypełnienie" to "40% (4,000 m³)", "Źródło" to "wpisany ręcznie"), readingDetails.rows)
        assertEquals("Nietypowy odczyt (duży skok zużycia)", readingDetails.note)

        val pumpingDetails = HistoryTexts.details(points[1], points, zone)
        assertEquals("Wywóz ścieków", pumpingDetails.title)
        assertEquals(listOf("Wypełnienie przed wywozem" to "40% (4,000 m³)"), pumpingDetails.rows)
    }
}
