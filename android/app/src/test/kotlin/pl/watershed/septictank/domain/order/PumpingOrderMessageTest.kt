package pl.watershed.septictank.domain.order

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PumpingOrderMessageTest {

    @Test
    fun `message matches contracts sms-message table for every weekday`() {
        val expected = mapOf(
            DayOfWeek.MONDAY to "Poproszę o wywóz w poniedziałek",
            DayOfWeek.TUESDAY to "Poproszę o wywóz we wtorek",
            DayOfWeek.WEDNESDAY to "Poproszę o wywóz w środę",
            DayOfWeek.THURSDAY to "Poproszę o wywóz w czwartek",
            DayOfWeek.FRIDAY to "Poproszę o wywóz w piątek",
            DayOfWeek.SATURDAY to "Poproszę o wywóz w sobotę",
            DayOfWeek.SUNDAY to "Poproszę o wywóz w niedzielę",
        )
        DayOfWeek.entries.forEach { day -> assertEquals(expected.getValue(day), PumpingOrderMessage.forDay(day)) }
    }

    @Test
    fun `every message fits in a single UCS-2 SMS segment`() {
        DayOfWeek.entries.forEach { day -> assertTrue(PumpingOrderMessage.forDay(day).length <= 70) }
    }
}
