package pl.watershed.septictank.domain.order

import java.time.DayOfWeek
import java.time.LocalDate

private const val ORDER_DAY_COUNT = 5
private const val MIN_DAYS_AHEAD = 2L

/**
 * Days offered in the "Zamów wywóz" dialog (spec 003 FR-004, research.md §3): the next
 * [ORDER_DAY_COUNT] working days (Mon-Fri), starting no earlier than the day after tomorrow --
 * there are no pumpings on weekends, and ordering for tomorrow is too short notice.
 *
 * The list always spans under 7 calendar days, so the weekday name alone in the SMS is unambiguous.
 */
fun availableOrderDays(today: LocalDate): List<LocalDate> =
    generateSequence(today.plusDays(MIN_DAYS_AHEAD)) { it.plusDays(1) }
        .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
        .take(ORDER_DAY_COUNT)
        .toList()
