package pl.watershed.septictank.domain.usage

import pl.watershed.septictank.domain.warning.WarningLevel

/**
 * Bieżący stan zużycia (data-model.md -> UsageState) -- encja wyliczana, nieprzechowywana
 * bezpośrednio w bazie, tylko w warstwie domenowej na potrzeby UI.
 */
data class UsageState(
    val currentUsageLiters: Long,
    val capacityLiters: Long?,
    val usagePercentOfCapacity: Double?,
    val warningLevel: WarningLevel,
)
