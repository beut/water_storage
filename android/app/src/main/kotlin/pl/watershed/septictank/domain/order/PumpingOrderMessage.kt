package pl.watershed.septictank.domain.order

import java.time.DayOfWeek

/**
 * SMS text for ordering pumping (spec 003 FR-003, contracts/sms-message.md).
 *
 * A fixed table rather than the locale's day names: those are nominative ("środa"), while after
 * the preposition Polish needs the accusative ("w środę") plus the "we wtorek" exception.
 */
object PumpingOrderMessage {

    fun dayPhrase(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "w poniedziałek"
        DayOfWeek.TUESDAY -> "we wtorek"
        DayOfWeek.WEDNESDAY -> "w środę"
        DayOfWeek.THURSDAY -> "w czwartek"
        DayOfWeek.FRIDAY -> "w piątek"
        DayOfWeek.SATURDAY -> "w sobotę"
        DayOfWeek.SUNDAY -> "w niedzielę"
    }

    fun forDay(day: DayOfWeek): String = "Poproszę o wywóz ${dayPhrase(day)}"
}
