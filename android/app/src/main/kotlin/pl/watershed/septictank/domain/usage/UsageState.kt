package pl.watershed.septictank.domain.usage

import pl.watershed.septictank.domain.warning.WarningLevel

/**
 * Current usage state (data-model.md -> UsageState) -- a computed entity, not stored directly in
 * the database, only in the domain layer for the UI.
 */
data class UsageState(
    val currentUsageLiters: Long,
    val capacityLiters: Long?,
    val usagePercentOfCapacity: Double?,
    val warningLevel: WarningLevel,
)
