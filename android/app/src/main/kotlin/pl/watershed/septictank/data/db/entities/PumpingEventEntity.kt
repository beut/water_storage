package pl.watershed.septictank.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Pumping (septic tank emptying) event (data-model.md -> PumpingEvent, FR-006, FR-007).
 *
 * [baselineReadingId] MUST point to the latest available [MeterReadingEntity] at the moment the
 * "Empty tank" button is pressed -- enforced in PumpingEventRepository (T019/T020), because the
 * rule depends on the database state at save time, not just on the schema.
 */
@Entity(
    tableName = "pumping_events",
    foreignKeys = [
        ForeignKey(
            entity = MeterReadingEntity::class,
            parentColumns = ["id"],
            childColumns = ["baselineReadingId"],
        ),
    ],
)
data class PumpingEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampMillis: Long,
    val baselineReadingId: Long,
)
