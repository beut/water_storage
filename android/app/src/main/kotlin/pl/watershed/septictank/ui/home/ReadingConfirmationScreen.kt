package pl.watershed.septictank.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.watershed.septictank.data.db.entities.ReadingSource
import pl.watershed.septictank.ui.common.AppCard
import pl.watershed.septictank.ui.common.AppScaffold
import pl.watershed.septictank.ui.common.CenteredContent
import pl.watershed.septictank.ui.common.NoticeBanner
import pl.watershed.septictank.ui.common.ScreenHorizontalPadding
import pl.watershed.septictank.ui.theme.AppTheme

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
    var text by rememberSaveable {
        mutableStateOf(suggestedLiters?.let { formatLitersAsM3(it) } ?: "")
    }
    var anomalyAcknowledged by rememberSaveable { mutableStateOf(false) }
    var showRawOcrText by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // System back cancels the reading flow instead of leaving the app.
    BackHandler(onBack = onCancel)

    val parsedLiters = text.trim().replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 1000.0) }
    val isInvalid = text.isNotBlank() && (parsedLiters == null || parsedLiters < 0)
    val isAnomalous = parsedLiters != null && latestValueLiters != null && parsedLiters < latestValueLiters
    val canConfirm = parsedLiters != null && parsedLiters >= 0 && (!isAnomalous || anomalyAcknowledged)

    val confirm: () -> Unit = {
        if (canConfirm) {
            val source = if (!hasPhoto || suggestedLiters == null) {
                ReadingSource.MANUAL_ENTERED
            } else if (parsedLiters == suggestedLiters) {
                ReadingSource.AUTO_OCR
            } else {
                ReadingSource.MANUAL_CORRECTED
            }
            onConfirm(parsedLiters ?: 0, source, isAnomalous)
        }
    }

    AppScaffold(
        title = if (hasPhoto) "Potwierdź odczyt" else "Nowy odczyt",
        onBack = onCancel,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = ScreenHorizontalPadding, vertical = 12.dp),
                ) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text("Anuluj")
                    }
                    Button(enabled = canConfirm, onClick = confirm, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text("Zapisz odczyt")
                    }
                }
            }
        },
    ) { innerPadding ->
        CenteredContent(modifier = Modifier.padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenHorizontalPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    !hasPhoto -> NoticeBanner(
                        icon = Icons.Filled.Info,
                        text = "Wpisz stan licznika wody w m³ — tak, jak widać go na liczniku.",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    suggestedLiters == null -> NoticeBanner(
                        icon = Icons.Filled.Warning,
                        text = "Nie udało się jednoznacznie odczytać wartości ze zdjęcia. Wpisz odczyt ręcznie.",
                        containerColor = AppTheme.statusColors.warningContainer,
                        contentColor = AppTheme.statusColors.onWarningContainer,
                    )
                    else -> NoticeBanner(
                        icon = Icons.Filled.Info,
                        text = "Rozpoznaliśmy odczyt ze zdjęcia. Sprawdź go i popraw, jeśli jest niepoprawny.",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                AppCard {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Stan licznika") },
                        placeholder = { Text("np. 123,456") },
                        suffix = { Text("m³") },
                        singleLine = true,
                        isError = isInvalid,
                        supportingText = {
                            Text(
                                when {
                                    isInvalid -> "Wpisz liczbę, np. 123,456"
                                    latestValueLiters != null -> "Poprzedni odczyt: ${formatLitersAsM3(latestValueLiters)} m³"
                                    else -> "To będzie pierwszy odczyt"
                                },
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            confirm()
                        }),
                        textStyle = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (isAnomalous) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = anomalyAcknowledged,
                                        role = Role.Checkbox,
                                        onValueChange = { anomalyAcknowledged = it },
                                    )
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            ) {
                                Checkbox(checked = anomalyAcknowledged, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                                Text(
                                    "Ten odczyt jest niższy niż poprzedni. Potwierdzam, że jest prawidłowy " +
                                        "(np. po wymianie licznika).",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                }

                if (hasPhoto) {
                    AppCard(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        TextButton(onClick = { showRawOcrText = !showRawOcrText }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Tekst rozpoznany przez OCR",
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                if (showRawOcrText) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (showRawOcrText) "Zwiń" else "Rozwiń",
                            )
                        }
                        if (showRawOcrText) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                text = ocrRawText.ifBlank { "(OCR nie rozpoznał żadnego tekstu na zdjęciu)" },
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatLitersAsM3(liters: Long): String = String.format("%.3f", liters / 1000.0)
