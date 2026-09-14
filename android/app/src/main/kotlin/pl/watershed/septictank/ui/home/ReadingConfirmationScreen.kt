package pl.watershed.septictank.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.watershed.septictank.data.db.entities.ReadingSource

/**
 * Ekran potwierdzenia odczytu (FR-003, FR-011): pokazuje wynik OCR (jeśli dostępny), pozwala go
 * poprawić lub wpisać ręcznie, i wymaga jawnego potwierdzenia checkboxem dla odczytów anomalnych
 * (data-model.md -> MeterReading.anomalyAcknowledged) zanim akcja "Zapisz" stanie się aktywna.
 */
@Composable
fun ReadingConfirmationScreen(
    suggestedLiters: Long?,
    isAnomalous: Boolean,
    ocrRawText: String,
    onConfirm: (valueLiters: Long, source: ReadingSource) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember {
        mutableStateOf(suggestedLiters?.let { formatLitersAsM3(it) } ?: "")
    }
    var anomalyAcknowledged by remember { mutableStateOf(false) }
    var showRawOcrText by remember { mutableStateOf(false) }

    val parsedLiters = text.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 1000.0) }
    val canConfirm = parsedLiters != null && parsedLiters >= 0 && (!isAnomalous || anomalyAcknowledged)

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(
            if (suggestedLiters == null) {
                "Nie udało się jednoznacznie odczytać wartości ze zdjęcia. Wpisz odczyt ręcznie (m³)."
            } else {
                "Rozpoznany odczyt licznika (m³). Popraw, jeśli jest niepoprawny."
            },
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Odczyt licznika (m³)") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        if (isAnomalous) {
            Row(modifier = Modifier.padding(top = 12.dp)) {
                Checkbox(checked = anomalyAcknowledged, onCheckedChange = { anomalyAcknowledged = it })
                Text(
                    "Ten odczyt jest niższy niż poprzedni -- potwierdzam, że jest prawidłowy " +
                        "(np. wymiana licznika).",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        TextButton(onClick = { showRawOcrText = !showRawOcrText }, modifier = Modifier.padding(top = 12.dp)) {
            Text(if (showRawOcrText) "Ukryj tekst rozpoznany przez OCR" else "Pokaż tekst rozpoznany przez OCR")
        }
        if (showRawOcrText) {
            Text(
                text = ocrRawText.ifBlank { "(OCR nie rozpoznał żadnego tekstu na zdjęciu)" },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }

        Row(modifier = Modifier.padding(top = 24.dp)) {
            TextButton(onClick = onCancel) { Text("Anuluj") }
            Button(
                enabled = canConfirm,
                onClick = {
                    val source = if (suggestedLiters == null) {
                        ReadingSource.MANUAL_ENTERED
                    } else if (parsedLiters == suggestedLiters) {
                        ReadingSource.AUTO_OCR
                    } else {
                        ReadingSource.MANUAL_CORRECTED
                    }
                    onConfirm(parsedLiters ?: 0, source)
                },
            ) {
                Text("Zapisz odczyt")
            }
        }
    }
}

private fun formatLitersAsM3(liters: Long): String = String.format("%.3f", liters / 1000.0)
