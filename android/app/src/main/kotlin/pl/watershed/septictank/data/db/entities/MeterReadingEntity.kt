package pl.watershed.septictank.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Origin of the reading value (data-model.md -> MeterReading.source, FR-003).
 */
enum class ReadingSource {
    AUTO_OCR,
    MANUAL_CORRECTED,
    MANUAL_ENTERED,
}

/**
 * Water meter reading (data-model.md -> MeterReading).
 *
 * [valueLiters] stores the reading value in liters (integer), matching the m3 unit with 0.001 m3
 * precision decided in the /speckit-clarify session (FR-002), without the precision loss typical
 * of floating-point arithmetic.
 *
 * [photoPath] is `null` for readings entered directly by hand (FR-015, no photo) -- for readings
 * that started from a photo (AUTO_OCR / MANUAL_CORRECTED) it is always set.
 */
@Entity(tableName = "meter_readings")
data class MeterReadingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampMillis: Long,
    val valueLiters: Long,
    val photoPath: String?,
    val source: ReadingSource,
    val isAnomalous: Boolean = false,
    val anomalyAcknowledged: Boolean = false,
)
