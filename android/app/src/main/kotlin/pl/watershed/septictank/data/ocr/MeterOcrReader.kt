package pl.watershed.septictank.data.ocr

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
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
        return extractOdometerLiters(result)
    }

    /**
     * Tarcza licznika zawiera wiele innych liczb poza odczytem (numer seryjny, parametry typu
     * "H-R=160", oznaczenia podziałek "x0,1"/"x0,01" itd.) -- branie pierwszej liczby z całego
     * rozpoznanego tekstu (jak w poprzedniej wersji) łapało przypadkowo te etykiety zamiast
     * odczytu. Zamiast tego: bierzemy tylko linie, które w całości są liczbą (bez liter/znaków --
     * odrzuca to etykiety typu "H-R=160" czy "TCM 142/08"), o długości 4-8 cyfr (odrzuca pojedyncze
     * cyfry z podziałek tarcz i bardzo długi numer seryjny), i spośród nich wybieramy tę o
     * największej wysokości ramki -- licznikowe okienko z odczytem ma fizycznie największe cyfry
     * na tarczy.
     */
    private fun extractOdometerLiters(result: Text): Long? {
        val candidate = result.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val cleaned = line.text.replace(" ", "")
                if (!PURE_NUMBER_REGEX.matches(cleaned)) return@mapNotNull null
                val digitCount = cleaned.count { it.isDigit() }
                if (digitCount !in MIN_ODOMETER_DIGITS..MAX_ODOMETER_DIGITS) return@mapNotNull null
                val height = line.boundingBox?.height() ?: return@mapNotNull null
                Triple(cleaned, height, digitCount)
            }
            .maxByOrNull { (_, height, _) -> height }
            ?: return null

        val valueM3 = candidate.first.replace(',', '.').toDoubleOrNull() ?: return null
        return Math.round(valueM3 * LITERS_PER_M3)
    }

    companion object {
        private val PURE_NUMBER_REGEX = Regex("""\d{1,8}([.,]\d{1,4})?""")
        private const val MIN_ODOMETER_DIGITS = 4
        private const val MAX_ODOMETER_DIGITS = 8
        private const val LITERS_PER_M3 = 1000.0
    }
}
