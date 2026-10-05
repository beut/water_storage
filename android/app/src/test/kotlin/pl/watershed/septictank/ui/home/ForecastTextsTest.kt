package pl.watershed.septictank.ui.home

import java.time.LocalDate
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import pl.watershed.septictank.domain.forecast.PumpingForecast
import pl.watershed.septictank.ui.home.ForecastRow.Emphasis

class ForecastTextsTest {

    private val today = LocalDate.parse("2026-10-05")
    private lateinit var previousLocale: Locale

    // Polish locale, as on the phone: decimal comma.
    @Before
    fun setLocale() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale("pl", "PL"))
    }

    @After
    fun restoreLocale() = Locale.setDefault(previousLocale)

    private fun forecast(
        daysSinceLastPumping: Long? = 12,
        averageDailyLiters: Double? = 200.0,
        averageDailyPercent: Double? = 2.0,
        estimatedFillPercentNow: Double? = 52.0,
        daysUntilPumping: Double? = 19.4,
    ) = PumpingForecast(
        daysSinceLastPumping = daysSinceLastPumping,
        lastPumpingMillis = null,
        averageDailyLiters = averageDailyLiters,
        averageDailyPercent = averageDailyPercent,
        estimatedFillPercentNow = estimatedFillPercentNow,
        daysUntilPumping = daysUntilPumping,
    )

    private fun rows(forecast: PumpingForecast) = ForecastTexts.rows(forecast, today)

    @Test
    fun `typical forecast with capacity configured`() = assertEquals(
        listOf(
            ForecastRow("Ostatni wywóz", "12 dni temu (23.09)"),
            ForecastRow("Średnio dziennie", "0,200 m³ (2,0%)"),
            ForecastRow("Wywóz przy 90%", "za ok. 19 dni (24.10)", Emphasis.STRONG),
        ),
        rows(forecast()),
    )

    @Test
    fun `pumping due now`() = assertEquals(
        ForecastRow("Wywóz przy 90%", "potrzebny teraz (ok. 93%)", Emphasis.ALERT),
        rows(forecast(estimatedFillPercentNow = 93.4, daysUntilPumping = 0.0)).last(),
    )

    @Test
    fun `one day uses singular`() = assertEquals(
        "za ok. 1 dzień (06.10)",
        rows(forecast(daysUntilPumping = 1.7)).last().value,
    )

    @Test
    fun `no growth cannot be forecast`() = assertEquals(
        ForecastRow("Wywóz przy 90%", "brak przyrostu"),
        rows(forecast(averageDailyLiters = 0.0, averageDailyPercent = 0.0, daysUntilPumping = null)).last(),
    )

    @Test
    fun `no capacity shows average without percent and no forecast row`() = assertEquals(
        listOf(
            ForecastRow("Ostatni wywóz", "12 dni temu (23.09)"),
            ForecastRow("Średnio dziennie", "0,200 m³"),
        ),
        rows(forecast(averageDailyPercent = null, estimatedFillPercentNow = null, daysUntilPumping = null)),
    )

    @Test
    fun `not enough data and no pumping yet`() = assertEquals(
        listOf(
            ForecastRow("Ostatni wywóz", "brak"),
            ForecastRow("Średnio dziennie", "za mało danych (min. 1 dzień odczytów)"),
        ),
        rows(
            forecast(
                daysSinceLastPumping = null,
                averageDailyLiters = null,
                averageDailyPercent = null,
                estimatedFillPercentNow = null,
                daysUntilPumping = null,
            ),
        ),
    )

    @Test
    fun `today and yesterday`() {
        assertEquals("dziś", rows(forecast(daysSinceLastPumping = 0)).first().value)
        assertEquals("wczoraj", rows(forecast(daysSinceLastPumping = 1)).first().value)
    }
}
