package pl.watershed.septictank.data.db

import kotlinx.coroutines.flow.Flow
import pl.watershed.septictank.data.db.dao.TankConfigurationDao
import pl.watershed.septictank.data.db.entities.TankConfigurationEntity

/**
 * Tank configuration repository (data-model.md -> TankConfiguration, FR-008).
 * V1: a single record (singleton), per the "one tank/meter per installation" decision.
 */
class TankConfigurationRepository(private val dao: TankConfigurationDao) {

    suspend fun get(): TankConfigurationEntity = dao.get() ?: TankConfigurationEntity(capacityLiters = null)

    fun observe(): Flow<TankConfigurationEntity?> = dao.observe()

    /** @param capacityLiters MUST be > 0 (data-model.md validation rule for TankConfiguration.capacityM3). */
    suspend fun updateCapacity(capacityLiters: Long) {
        require(capacityLiters > 0) { "capacityLiters MUST być > 0" }
        dao.upsert(get().copy(capacityLiters = capacityLiters))
    }

    /** @param percent MUST be in the range 1-99 (data-model.md -> warningThresholdPercent). */
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

    /** @param phone normalized `+?[0-9]{9,15}` (data-model.md, spec 003), or `null` to clear it. */
    suspend fun updatePumpingCompanyPhone(phone: String?) {
        require(phone == null || NORMALIZED_PHONE.matches(phone)) {
            "pumpingCompanyPhone MUST mieć postać +?[0-9]{9,15}"
        }
        dao.upsert(get().copy(pumpingCompanyPhone = phone))
    }

    private companion object {
        val NORMALIZED_PHONE = Regex("^\\+?[0-9]{9,15}$")
    }
}
