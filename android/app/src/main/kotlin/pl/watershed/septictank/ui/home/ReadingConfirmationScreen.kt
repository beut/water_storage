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
 * Reading confirmation/entry screen (FR-003, FR-011, FR-015): shows the OCR result when a photo
 * was taken (or an empty form for manual entry without a photo -- [hasPhoto] = false), lets the
 * user correct it or type it in, and requires explicit checkbox confirmation for anomalous
 * readings (data-model.md -> MeterReading.anomalyAcknowledged) before the "Save" action becomes
 * active. The anomaly is computed live from the currently entered value (not the OCR suggestion),
 * so it also works correctly after a manual correction.
 */
@Composable
fun ReadingConfirmationScreen(
    suggestedLiters: Long?,
    latestValueLiters: Long?,
    hasPhoto: Boolean,
    ocrRawText: String,
    onConfirm: (valueLiters: Long, source: ReadingSource, isAnomalous: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember {
        mutableStateOf(suggestedLiters?.let { formatLitersAsM3(it) } ?: "")
    }
    var anomalyAcknowledged by remember { mutableStateOf(false) }
    var showRawOcrText by remember { mutableStateOf(false) }

    val parsedLiters = text.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 1000.0) }
    val isAnomalous = parsedLiters != null && latestValueLiters != null && parsedLiters < latestValueLiters
    val canConfirm = parsedLiters != null && parsedLiters >= 0 && (!isAnomalous || anomalyAcknowledged)

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(
            when {
                !hasPhoto -> "Wpisz odczyt licznika ręcznie (m³)."
                suggestedLiters == null -> "Nie udało się jednoznacznie odczytać wartości ze zdjęcia. Wpisz odczyt ręcznie (m³)."
                else -> "Rozpoznany odczyt licznika (m³). Popraw, jeśli jest niepoprawny."
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

        if (hasPhoto) {
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
        }

        Row(modifier = Modifier.padding(top = 24.dp)) {
            TextButton(onClick = onCancel) { Text("Anuluj") }
            Button(
                enabled = canConfirm,
                onClick = {
                    val source = if (!hasPhoto || suggestedLiters == null) {
                        ReadingSource.MANUAL_ENTERED
                    } else if (parsedLiters == suggestedLiters) {
                        ReadingSource.AUTO_OCR
                    } else {
                        ReadingSource.MANUAL_CORRECTED
                    }
                    onConfirm(parsedLiters ?: 0, source, isAnomalous)
                },
            ) {
                Text("Zapisz odczyt")
            }
        }
    }
}

private fun formatLitersAsM3(liters: Long): String = String.format("%.3f", liters / 1000.0)
