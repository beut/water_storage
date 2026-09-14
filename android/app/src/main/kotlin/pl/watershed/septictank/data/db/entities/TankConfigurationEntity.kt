package pl.watershed.septictank.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Konfiguracja zbiornika (data-model.md -> TankConfiguration, FR-008, FR-009, FR-010).
 *
 * V1 obsługuje jeden zbiornik/licznik na instalację (decyzja z sesji /speckit-clarify),
 * dlatego tabela zawsze przechowuje co najwyżej jeden rekord o stałym [id] = [SINGLETON_ID].
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
