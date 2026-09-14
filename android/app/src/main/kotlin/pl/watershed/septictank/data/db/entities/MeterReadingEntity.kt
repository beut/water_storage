package pl.watershed.septictank.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Źródło pochodzenia wartości odczytu (data-model.md -> MeterReading.source, FR-003).
 */
enum class ReadingSource {
    AUTO_OCR,
    MANUAL_CORRECTED,
    MANUAL_ENTERED,
}

/**
 * Odczyt licznika wody (data-model.md -> MeterReading).
 *
 * [valueLiters] przechowuje wartość odczytu w litrach (liczba całkowita), co odpowiada
 * jednostce m3 z dokładnością do 0,001 m3 ustalonej w sesji /speckit-clarify (FR-002),
 * bez utraty precyzji charakterystycznej dla arytmetyki zmiennoprzecinkowej.
 *
 * [photoPath] jest `null` dla odczytów wpisanych bezpośrednio ręcznie (FR-015, bez zdjęcia) --
 * dla odczytów rozpoczętych od zdjęcia (AUTO_OCR / MANUAL_CORRECTED) jest zawsze ustawione.
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
