package pl.watershed.septictank.data.photo

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Zapis i odczyt zdjęć licznika w pamięci wewnętrznej aplikacji (data-model.md ->
 * MeterReading.photoPath, FR-004). Zdjęcia nie opuszczają urządzenia (FR-014).
 */
class PhotoStorage(private val context: Context) {

    private val photosDir: File
        get() = File(context.filesDir, "meter_photos").apply { mkdirs() }

    /** Tworzy docelowy plik na nowe zdjęcie licznika i zwraca jego ścieżkę bezwzględną. */
    fun createPhotoFile(): File = File(photosDir, "${UUID.randomUUID()}.jpg")

    fun photoFile(photoPath: String): File = File(photoPath)

    fun delete(photoPath: String) {
        photoFile(photoPath).delete()
    }
}
