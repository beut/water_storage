package pl.watershed.septictank.domain.usage

import pl.watershed.septictank.data.db.MeterReadingRepository
import pl.watershed.septictank.data.db.PumpingEventRepository
import pl.watershed.septictank.data.db.TankConfigurationRepository
import pl.watershed.septictank.domain.warning.WarningLevel
import pl.watershed.septictank.domain.warning.WarningLevelCalculator

/**
 * Computes [UsageState] (data-model.md -> UsageState, FR-005): the difference between the latest
 * reading and the baseline reading from the most recent [pl.watershed.septictank.data.db.entities.PumpingEventEntity],
 * or, if no pumping has happened yet, against the first recorded reading (Edge Case).
 */
class UsageCalculator(
    private val meterReadingRepository: MeterReadingRepository,
    private val pumpingEventRepository: PumpingEventRepository,
    private val tankConfigurationRepository: TankConfigurationRepository,
) {
    suspend fun calculate(): UsageState? {
        val latestReading = meterReadingRepository.latest() ?: return null

        val baselineLiters = pumpingEventRepository.latest()?.let { event ->
            meterReadingRepository.byId(event.baselineReadingId)?.valueLiters
        } ?: meterReadingRepository.first()?.valueLiters ?: latestReading.valueLiters

        val currentUsageLiters = (latestReading.valueLiters - baselineLiters).coerceAtLeast(0)

        val configuration = tankConfigurationRepository.get()
        val capacityLiters = configuration.capacityLiters
        val usagePercent = capacityLiters?.let { capacity ->
            if (capacity <= 0) null else currentUsageLiters * 100.0 / capacity
        }

        val warningLevel: WarningLevel =
            WarningLevelCalculator.calculate(usagePercent, configuration.warningThresholdPercent)

        return UsageState(
            currentUsageLiters = currentUsageLiters,
            capacityLiters = capacityLiters,
            usagePercentOfCapacity = usagePercent,
            warningLevel = warningLevel,
        )
    }
}
