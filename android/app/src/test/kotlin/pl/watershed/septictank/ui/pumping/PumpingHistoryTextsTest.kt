package pl.watershed.septictank.ui.pumping

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import pl.watershed.septictank.domain.pumping.PumpingHistoryEntry
import pl.watershed.septictank.domain.pumping.PumpingHistorySummary

class PumpingHistoryTextsTest {

    private val zone = ZoneId.of("Europe/Warsaw")
    private lateinit var previousLocale: Locale

    // Polish locale, as on the phone: decimal comma.
    @Before
    fun setLocale() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale("pl", "PL"))
    }

    @After
    fun restoreLocale() = Locale.setDefault(previousLocale)

    private fun entry(
        daysSincePrevious: Long? = 36,
        isFirstRecorded: Boolean = false,
        volumeLiters: Long? = 5_500,
        volumePercent: Double? = 55.0,
    ) = PumpingHistoryEntry(
        pumpingEventId = 1,
        timestampMillis = LocalDateTime.parse("2026-09-25T09:00").atZone(zone).toInstant().toEpochMilli(),
        daysSincePrevious = daysSincePrevious,
        isFirstRecorded = isFirstRecorded,
        volumeLiters = volumeLiters,
        volumePercentOfCapacity = volumePercent,
    )

    @Test
    fun `regular pumping`() = assertEquals(
        PumpingHistoryTexts.EntryTexts(
            date = "25.09.2026",
            weekday = "piątek",
            interval = "po 36 dniach",
            volume = "Wywieziono ok. 5,500 m³ (55%)",
        ),
        PumpingHistoryTexts.entry(entry(), zone),
    )

    @Test
    fun `one day uses singular`() = assertEquals("po 1 dniu", PumpingHistoryTexts.entry(entry(daysSincePrevious = 1), zone).interval)

    @Test
    fun `first recorded pumping shows neither interval nor volume`() {
        val texts = PumpingHistoryTexts.entry(
            entry(daysSincePrevious = null, isFirstRecorded = true, volumeLiters = null, volumePercent = null),
            zone,
        )
        assertEquals("pierwszy zarejestrowany", texts.interval)
        assertNull(texts.volume)
    }

    @Test
    fun `no capacity and unknown volume`() {
        assertEquals("Wywieziono ok. 5,500 m³", PumpingHistoryTexts.entry(entry(volumePercent = null), zone).volume)
        assertNull(PumpingHistoryTexts.entry(entry(volumeLiters = null, volumePercent = null), zone).volume)
    }

    @Test
    fun `summary rows`() {
        assertEquals(
            listOf("Liczba wywozów" to "3", "Średnio co" to "39 dni", "Średnio wywożone" to "6,000 m³"),
            PumpingHistoryTexts.summaryRows(PumpingHistorySummary(count = 3, averageIntervalDays = 38.5, averageVolumeLiters = 6_000.0)),
        )
        assertEquals(
            listOf("Liczba wywozów" to "1", "Średnio wywożone" to "6,000 m³"),
            PumpingHistoryTexts.summaryRows(PumpingHistorySummary(count = 1, averageIntervalDays = null, averageVolumeLiters = 6_000.0)),
        )
    }
}
