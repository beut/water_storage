package pl.watershed.septictank.domain.order

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderDaysTest {

    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun `every weekday as today yields 5 distinct working days within one week from the day after tomorrow`() {
        // 2026-09-21 is a Monday, 2026-09-27 a Sunday.
        for (offset in 0L..6L) {
            val today = date("2026-09-21").plusDays(offset)
            val days = availableOrderDays(today)

            assertEquals("count for $today", 5, days.size)
            assertTrue("no weekends for $today", days.none { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY })
            assertTrue("starts at day after tomorrow for $today", !days.first().isBefore(today.plusDays(2)))
            assertTrue("spans under 7 days for $today", ChronoUnit.DAYS.between(days.first(), days.last()) < 7)
            assertEquals("ascending for $today", days.sorted(), days)
            assertEquals("unique weekday names for $today", 5, days.map { it.dayOfWeek }.distinct().size)
        }
    }

    @Test
    fun `wednesday starts on friday`() = assertEquals(
        listOf("2026-09-25", "2026-09-28", "2026-09-29", "2026-09-30", "2026-10-01").map(::date),
        availableOrderDays(date("2026-09-23")),
    )

    @Test
    fun `thursday skips the weekend and starts on monday`() = assertEquals(
        listOf("2026-09-28", "2026-09-29", "2026-09-30", "2026-10-01", "2026-10-02").map(::date),
        availableOrderDays(date("2026-09-24")),
    )

    @Test
    fun `friday and saturday start on monday`() {
        val mondayToFriday = listOf("2026-09-28", "2026-09-29", "2026-09-30", "2026-10-01", "2026-10-02").map(::date)
        assertEquals(mondayToFriday, availableOrderDays(date("2026-09-25")))
        assertEquals(mondayToFriday, availableOrderDays(date("2026-09-26")))
    }

    @Test
    fun `sunday starts on tuesday and wraps to next monday`() = assertEquals(
        listOf("2026-09-29", "2026-09-30", "2026-10-01", "2026-10-02", "2026-10-05").map(::date),
        availableOrderDays(date("2026-09-27")),
    )
}
