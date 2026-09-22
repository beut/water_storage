package pl.watershed.septictank.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tank configuration (data-model.md -> TankConfiguration, FR-008, FR-009, FR-010).
 *
 * V1 supports one tank/meter per installation (decision from the /speckit-clarify session),
 * so the table always holds at most one record with a fixed [id] = [SINGLETON_ID].
 */
@Entity(tableName = "tank_configuration")
data class TankConfigurationEntity(
    @PrimaryKey
    val id: Long = SINGLETON_ID,
    val capacityLiters: Long?,
    val warningThresholdPercent: Int = DEFAULT_WARNING_THRESHOLD_PERCENT,
    val reminderEnabled: Boolean = false,
    val reminderIntervalDays: Int? = null,
) {
    companion object {
        const val SINGLETON_ID = 1L
        const val DEFAULT_WARNING_THRESHOLD_PERCENT = 80
    }
}
