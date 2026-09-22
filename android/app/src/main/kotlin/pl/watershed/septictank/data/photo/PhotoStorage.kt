package pl.watershed.septictank.data.photo

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Saves and reads meter photos in the app's internal storage (data-model.md ->
 * MeterReading.photoPath, FR-004). Photos never leave the device (FR-014).
 */
class PhotoStorage(private val context: Context) {

    private val photosDir: File
        get() = File(context.filesDir, "meter_photos").apply { mkdirs() }

    /** Creates the target file for a new meter photo and returns its absolute path. */
    fun createPhotoFile(): File = File(photosDir, "${UUID.randomUUID()}.jpg")

    fun photoFile(photoPath: String): File = File(photoPath)

    fun delete(photoPath: String) {
        photoFile(photoPath).delete()
    }
}
