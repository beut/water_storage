package pl.watershed.septictank.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Zdarzenie wywozu ścieków (data-model.md -> PumpingEvent, FR-006, FR-007).
 *
 * [baselineReadingId] MUST wskazywać na najnowszy dostępny [MeterReadingEntity] w momencie
 * naciśnięcia przycisku "Wywóz ścieków" -- egzekwowane w PumpingEventRepository (T019/T020),
 * ponieważ reguła zależy od stanu bazy w chwili zapisu, a nie tylko od schematu.
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
