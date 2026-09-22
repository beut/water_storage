package pl.watershed.septictank.data.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.IOException
import kotlinx.coroutines.tasks.await

/**
 * Recognizes the meter reading value from a photo fully on-device, using the ML Kit Text
 * Recognition model bundled with the app (research.md -> "Meter reading recognition (OCR)",
 * FR-002, FR-014). Makes no network calls.
 *
 * LIMITATION (confirmed on a real device): ML Kit Text Recognition only reads printed text. It
 * cannot read the position of red pointers on analog dials (x0.1 .. x0.0001) -- that would
 * require separate image analysis (pointer angle), outside the scope of text OCR. On some meters
 * (mechanical digit drums behind dirty/reflective glass) the generic model may fail to recognize
 * the main odometer window digits at all -- in that case `suggestedLiters` will be `null`, which
 * is the expected behavior (safer than guessing); the user enters the reading manually on the
 * confirmation screen (FR-003).
 *
 * EXIF ORIENTATION CORRECTION: phone cameras very often save JPEG pixels in a single orientation,
 * describing the actual rotation only in the EXIF `Orientation` metadata -- neither
 * `BitmapFactory` nor ML Kit's `InputImage.fromBitmap(bitmap, rotationDegrees)` with
 * `rotationDegrees = 0` read that flag automatically. A meter photo rotated 90°/270° from upright
 * looks equally bad to both OCR passes (0° and 180°, see below) -- the 180° retry doesn't fix an
 * error that's actually caused by a 90° rotation. [decodeUprightBitmap] straightens the image per
 * EXIF before further processing (same issue and same fix as in the fuel_management project ->
 * `ReceiptOcrReader.decodeUprightBitmap`).
 */
class MeterOcrReader {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * [suggestedLiters]: `null` when a number couldn't be unambiguously extracted (requires
     * manual entry, FR-003). [rawText]: the full text recognized by ML Kit (both orientation
     * attempts) -- shown in ReadingConfirmationScreen as diagnostics when the automatic reading
     * is wrong, so the user can understand what OCR actually sees on the dial.
     */
    data class OcrResult(val suggestedLiters: Long?, val rawText: String)

    suspend fun recognize(photoFile: File): OcrResult {
        val bitmap = decodeUprightBitmap(photoFile)
            ?: return OcrResult(null, "")

        // Meters are sometimes mounted/photographed "upside down" (e.g. when the pipe comes in
        // from above) -- generic OCR handles text rotated 180 degrees poorly, so we try both
        // orientations and merge the candidates. This does NOT replace the EXIF correction above
        // -- that one straightens the photo to upright, this one additionally tries physically
        // flipping the meter itself.
        val upright = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        val rotatedBitmap = rotate180(bitmap)
        val rotated = recognizer.process(InputImage.fromBitmap(rotatedBitmap, 0)).await()
        rotatedBitmap.recycle()

        val rawText = "-- oryginał --\n${upright.text}\n-- obrócone 180° --\n${rotated.text}"
        val candidates = extractCandidates(upright) + extractCandidates(rotated)
        val suggestedLiters = pickBest(candidates)?.let { candidate ->
            val valueM3 = candidate.text.replace(',', '.').toDoubleOrNull() ?: return@let null
            Math.round(valueM3 * LITERS_PER_M3)
        }
        return OcrResult(suggestedLiters, rawText)
    }

    /**
     * Decodes the photo and rotates it according to the EXIF `Orientation` flag, if set (see the
     * class doc above). Without this correction, an image rotated 90°/270° by the phone camera
     * gives OCR text running vertically -- neither the 0° pass nor the 180° retry fixes that.
     */
    private fun decodeUprightBitmap(file: File): Bitmap? {
        val original = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val exifRotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        if (exifRotationDegrees == 0) return original
        val rotated = rotate(original, exifRotationDegrees.toFloat())
        original.recycle()
        return rotated
    }

    private fun rotate180(bitmap: Bitmap): Bitmap = rotate(bitmap, 180f)

    private fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private data class Candidate(val text: String, val height: Int)

    /**
     * The meter dial contains many other numbers besides the reading (serial number, parameters
     * like "H-R=160", dial-multiplier labels "x0.1"/"x0.01" etc.) -- taking the first number from
     * the whole recognized text was accidentally catching these labels instead of the reading.
     * Instead: only take lines that are entirely a number (no letters/symbols -- this rejects
     * labels like "H-R=160" or "TCM 142/08"), 4-8 digits long (rejects single digits from dial
     * markings and the very long serial number).
     */
    private fun extractCandidates(result: Text): List<Candidate> =
        result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val cleaned = line.text.replace(" ", "")
            if (!PURE_NUMBER_REGEX.matches(cleaned)) return@mapNotNull null
            val digitCount = cleaned.count { it.isDigit() }
            if (digitCount !in MIN_ODOMETER_DIGITS..MAX_ODOMETER_DIGITS) return@mapNotNull null
            val value = cleaned.replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
            if (isDialCalibrationConstant(value)) return@mapNotNull null
            val height = line.boundingBox?.height() ?: return@mapNotNull null
            Candidate(cleaned, height)
        }

    /**
     * The meter dial's multiplier markings are labeled "x0.1" / "x0.01" / "x0.001" / "x0.0001"
     * (the "x"/"*" is often lost by OCR). Observed on a real device: these numbers pass the
     * pure-number 4-8 digit line filter and look identical to a real fractional reading -- we
     * explicitly reject them as known calibration constants, almost certainly not a reading.
     */
    private fun isDialCalibrationConstant(value: Double): Boolean =
        DIAL_CALIBRATION_VALUES.any { kotlin.math.abs(value - it) < CALIBRATION_EPSILON }

    /**
     * Household water usage grows slowly over years -- the meter reading MUST start with "0" for
     * a very long time still (before it approaches 1000 m3), so candidates starting with "0" are
     * more trustworthy than random numbers of similar length picked up from the dial. Among those
     * (or, if none starts with zero, among all of them) we pick the one with the tallest bounding
     * box -- the reading window has physically the largest digits on the dial.
     */
    private fun pickBest(candidates: List<Candidate>): Candidate? {
        val startingWithZero = candidates.filter { it.text.startsWith("0") }
        val pool = startingWithZero.ifEmpty { candidates }
        return pool.maxByOrNull { it.height }
    }

    companion object {
        private val PURE_NUMBER_REGEX = Regex("""\d{1,8}([.,]\d{1,4})?""")
        private const val MIN_ODOMETER_DIGITS = 4
        private const val MAX_ODOMETER_DIGITS = 8
        private const val LITERS_PER_M3 = 1000.0
        private val DIAL_CALIBRATION_VALUES = listOf(0.1, 0.01, 0.001, 0.0001)
        private const val CALIBRATION_EPSILON = 0.00005
    }
}
