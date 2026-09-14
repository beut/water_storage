package pl.watershed.septictank.domain.warning

/** Poziom pilności ostrzeżenia o zapełnieniu zbiornika (data-model.md -> UsageState.warningLevel, FR-010). */
enum class WarningLevel {
    NONE,
    APPROACHING,
    EXCEEDED,
}

/**
 * Wyznacza [WarningLevel] na podstawie procentu wykorzystania pojemności zbiornika (FR-009, FR-010).
 * Zwraca [WarningLevel.NONE], gdy pojemność zbiornika nie jest jeszcze skonfigurowana (Edge Case
 * ze spec.md: brak ostrzeżeń bez skonfigurowanej pojemności, ale odczyty nadal są zapisywane).
 */
object WarningLevelCalculator {
    fun calculate(usagePercentOfCapacity: Double?, warningThresholdPercent: Int): WarningLevel {
        if (usagePercentOfCapacity == null) return WarningLevel.NONE
        return when {
            usagePercentOfCapacity >= 100.0 -> WarningLevel.EXCEEDED
            usagePercentOfCapacity >= warningThresholdPercent -> WarningLevel.APPROACHING
            else -> WarningLevel.NONE
        }
    }
}
