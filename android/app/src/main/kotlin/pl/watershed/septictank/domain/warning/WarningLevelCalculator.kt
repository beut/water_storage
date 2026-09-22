package pl.watershed.septictank.domain.warning

/** Urgency level of the tank-fill warning (data-model.md -> UsageState.warningLevel, FR-010). */
enum class WarningLevel {
    NONE,
    APPROACHING,
    EXCEEDED,
}

/**
 * Determines the [WarningLevel] from the percentage of tank capacity used (FR-009, FR-010).
 * Returns [WarningLevel.NONE] when the tank capacity isn't configured yet (Edge Case from
 * spec.md: no warnings without a configured capacity, but readings are still saved).
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
