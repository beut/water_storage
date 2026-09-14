package pl.watershed.septictank.data.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
 *
 * OGRANICZENIE (potwierdzone na realnym urządzeniu): ML Kit Text Recognition czyta wyłącznie
 * drukowany tekst. Nie potrafi odczytać pozycji czerwonych wskazówek na analogowych podziałkach
 * (x0,1 .. x0,0001) -- to wymagałoby osobnej analizy obrazu (kąt wskazówki), poza zakresem OCR
 * tekstu. Na niektórych licznikach (mechaniczne bębenki cyfrowe za brudnym/odblaskowym szkłem)
 * generyczny model może w ogóle nie rozpoznać cyfr głównego okienka odometru -- w takim wypadku
 * `suggestedLiters` będzie `null` i to oczekiwane zachowanie (bezpieczniejsze niż zgadywanie),
 * użytkownik wprowadza odczyt ręcznie na ekranie potwierdzenia (FR-003).
 */
class MeterOcrReader {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * [suggestedLiters]: `null`, gdy nie udało się jednoznacznie wyodrębnić liczby (wymaga
     * ręcznego wprowadzenia, FR-003). [rawText]: pełny tekst rozpoznany przez ML Kit (obie próby
     * orientacji) -- pokazywany w ReadingConfirmationScreen jako diagnostyka, gdy automatyczny
     * odczyt jest błędny, żeby dało się zrozumieć, co OCR faktycznie widzi na tarczy.
     */
    data class OcrResult(val suggestedLiters: Long?, val rawText: String)

    suspend fun recognize(photoFile: File): OcrResult {
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            ?: return OcrResult(null, "")

        // Liczniki bywają montowane/fotografowane "do góry nogami" (np. gdy podejście rury jest od
        // góry) -- generyczny OCR słabo radzi sobie z tekstem obróconym o 180 stopni, więc próbujemy
        // obu orientacji i łączymy kandydatów.
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

    private fun rotate180(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply { postRotate(180f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private data class Candidate(val text: String, val height: Int)

    /**
     * Tarcza licznika zawiera wiele innych liczb poza odczytem (numer seryjny, parametry typu
     * "H-R=160", oznaczenia podziałek "x0,1"/"x0,01" itd.) -- branie pierwszej liczby z całego
     * rozpoznanego tekstu łapało przypadkowo te etykiety zamiast odczytu. Zamiast tego: bierzemy
     * tylko linie, które w całości są liczbą (bez liter/znaków -- odrzuca to etykiety typu
     * "H-R=160" czy "TCM 142/08"), o długości 4-8 cyfr (odrzuca pojedyncze cyfry z podziałek tarcz
     * i bardzo długi numer seryjny).
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
     * Podziałki tarczy licznika są opisane etykietami "x0,1" / "x0,01" / "x0,001" / "x0,0001"
     * ("x"/"*" bywa gubione przez OCR). Zaobserwowane na realnym urządzeniu: te liczby przechodzą
     * przez filtr czysto-liczbowych linii 4-8 cyfr i wyglądają identycznie jak prawdziwy odczyt w
     * postaci ułamkowej -- odrzucamy je jawnie jako znane stałe kalibracyjne, prawie na pewno nie
     * będące odczytem.
     */
    private fun isDialCalibrationConstant(value: Double): Boolean =
        DIAL_CALIBRATION_VALUES.any { kotlin.math.abs(value - it) < CALIBRATION_EPSILON }

    /**
     * Domowe zużycie wody rośnie powoli w skali lat -- odczyt licznika MUST zaczynać się od "0"
     * jeszcze bardzo długo (zanim zbliży się do 1000 m3), więc kandydaci zaczynający się od "0" są
     * bardziej wiarygodni niż przypadkowe liczby o podobnej długości wyłapane z tarczy. Wśród nich
     * (lub, gdy żaden nie zaczyna się od zera, wśród wszystkich) wybieramy tego o największej
     * wysokości ramki -- licznikowe okienko z odczytem ma fizycznie największe cyfry na tarczy.
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
