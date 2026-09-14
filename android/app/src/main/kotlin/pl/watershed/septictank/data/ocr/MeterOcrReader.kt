package pl.watershed.septictank.data.ocr

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlinx.coroutines.tasks.await

/**
 * Rozpoznaje wartość odczytu licznika ze zdjęcia w pełni lokalnie na urządzeniu, przy użyciu
 * modelu ML Kit Text Recognition spakowanego z aplikacją (research.md -> "Rozpoznawanie odczytu
 * licznika (OCR)", FR-002, FR-014). Nie wykonuje żadnych wywołań sieciowych.
 */
class MeterOcrReader {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * @return rozpoznana wartość w litrach (odpowiednik m3 z dokładnością do 0,001 m3, FR-002),
     * albo `null`, gdy nie udało się jednoznacznie wyodrębnić liczby (wymaga ręcznego wprowadzenia,
     * FR-003).
     */
    suspend fun recognizeLiters(photoFile: File): Long? {
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath) ?: return null
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()
        return parseLiters(result.text)
    }

    /** Wyodrębnia pierwszą liczbę (m3 z do 3 miejscami po przecinku) z rozpoznanego tekstu. */
    private fun parseLiters(recognizedText: String): Long? {
        val match = NUMBER_REGEX.find(recognizedText) ?: return null
        val normalized = match.value.replace(',', '.')
        val valueM3 = normalized.toDoubleOrNull() ?: return null
        return Math.round(valueM3 * LITERS_PER_M3)
    }

    companion object {
        private val NUMBER_REGEX = Regex("""\d{1,5}[.,]?\d{0,3}""")
        private const val LITERS_PER_M3 = 1000.0
    }
}
