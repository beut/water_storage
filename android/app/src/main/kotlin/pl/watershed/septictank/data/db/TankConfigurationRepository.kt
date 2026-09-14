package pl.watershed.septictank.data.db

import kotlinx.coroutines.flow.Flow
import pl.watershed.septictank.data.db.dao.TankConfigurationDao
import pl.watershed.septictank.data.db.entities.TankConfigurationEntity

/**
 * Repozytorium konfiguracji zbiornika (data-model.md -> TankConfiguration, FR-008).
 * V1: pojedynczy rekord (singleton), zgodnie z decyzją "jeden zbiornik/licznik na instalację".
 */
class TankConfigurationRepository(private val dao: TankConfigurationDao) {

    suspend fun get(): TankConfigurationEntity = dao.get() ?: TankConfigurationEntity(capacityLiters = null)

    fun observe(): Flow<TankConfigurationEntity?> = dao.observe()

    /** @param capacityLiters MUST być > 0 (data-model.md regułą walidacji TankConfiguration.capacityM3). */
    suspend fun updateCapacity(capacityLiters: Long) {
        require(capacityLiters > 0) { "capacityLiters MUST być > 0" }
        dao.upsert(get().copy(capacityLiters = capacityLiters))
    }

    /** @param percent MUST mieścić się w zakresie 1-99 (data-model.md -> warningThresholdPercent). */
    suspend fun updateWarningThreshold(percent: Int) {
        require(percent in 1..99) { "warningThresholdPercent MUST być w zakresie 1-99" }
        dao.upsert(get().copy(warningThresholdPercent = percent))
    }

    suspend fun updateReminder(enabled: Boolean, intervalDays: Int?) {
        require(!enabled || (intervalDays != null && intervalDays > 0)) {
            "reminderIntervalDays jest wymagany, gdy reminderEnabled = true"
        }
        dao.upsert(get().copy(reminderEnabled = enabled, reminderIntervalDays = intervalDays))
    }
}
