package pl.watershed.septictank.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import pl.watershed.septictank.AppContainer
import pl.watershed.septictank.SepticTankApplication
import pl.watershed.septictank.ui.common.AppCard
import pl.watershed.septictank.ui.common.AppScaffold
import pl.watershed.septictank.ui.common.CardTitle
import pl.watershed.septictank.ui.common.CenteredContent
import pl.watershed.septictank.ui.common.ScreenHorizontalPadding
import pl.watershed.septictank.ui.common.rememberSnackbarHostState
import pl.watershed.septictank.ui.theme.AppIcons

@Composable
private fun rememberSettingsViewModel(container: AppContainer): SettingsViewModel {
    val context = LocalContext.current
    return viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(container.tankConfigurationRepository, context.applicationContext) }
        },
    )
}

/** FR-008, FR-013: tank capacity, warning threshold and reminder configuration. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as SepticTankApplication).container
    val viewModel = rememberSettingsViewModel(container)
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = rememberSnackbarHostState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    fun notify(message: String) {
        focusManager.clearFocus()
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    var capacityText by remember { mutableStateOf(state.capacityM3Text) }
    LaunchedEffect(state.capacityM3Text) { capacityText = state.capacityM3Text }
    var capacityError by remember { mutableStateOf(false) }

    var phoneText by remember { mutableStateOf(state.pumpingCompanyPhoneText) }
    LaunchedEffect(state.pumpingCompanyPhoneText) { phoneText = state.pumpingCompanyPhoneText }
    var phoneError by remember { mutableStateOf<String?>(null) }

    // Sliders keep a local value while dragging and persist once on release, so the database
    // and the reminder scheduler are not hit on every drag step.
    var thresholdValue by remember { mutableFloatStateOf(state.warningThresholdPercent.toFloat()) }
    LaunchedEffect(state.warningThresholdPercent) { thresholdValue = state.warningThresholdPercent.toFloat() }

    var reminderEnabled by remember { mutableStateOf(state.reminderEnabled) }
    var reminderIntervalValue by remember { mutableFloatStateOf(state.reminderIntervalDays.toFloat()) }
    LaunchedEffect(state.reminderEnabled, state.reminderIntervalDays) {
        reminderEnabled = state.reminderEnabled
        reminderIntervalValue = state.reminderIntervalDays.toFloat()
    }

    val saveCapacity: () -> Unit = {
        capacityError = !viewModel.saveCapacity(capacityText)
        if (!capacityError) notify("Zapisano pojemność zbiornika")
    }
    val savePhone: () -> Unit = {
        phoneError = viewModel.savePumpingCompanyPhone(phoneText)
        if (phoneError == null) notify(if (phoneText.isBlank()) "Usunięto numer firmy" else "Zapisano numer firmy")
    }

    AppScaffold(title = "Ustawienia", onBack = onBack, snackbarHostState = snackbarHostState) { innerPadding ->
        CenteredContent(modifier = Modifier.padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenHorizontalPadding)
                    .padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppCard {
                    CardTitle("Zbiornik", icon = AppIcons.WaterDrop)
                    OutlinedTextField(
                        value = capacityText,
                        onValueChange = {
                            capacityText = it
                            capacityError = false
                        },
                        label = { Text("Pojemność zbiornika") },
                        suffix = { Text("m³") },
                        singleLine = true,
                        isError = capacityError,
                        supportingText = {
                            Text(if (capacityError) "Podaj liczbę większą od zera, np. 10" else "Potrzebna do obliczania wypełnienia i ostrzeżeń")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { saveCapacity() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SaveButton(enabled = capacityText != state.capacityM3Text, onClick = saveCapacity)
                }

                AppCard {
                    CardTitle("Ostrzeżenia", icon = Icons.Filled.Warning, trailing = { ValuePill("${thresholdValue.roundToInt()}%") })
                    Text(
                        "Ostrzeżenie pojawi się, gdy wypełnienie zbiornika przekroczy ten próg.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = thresholdValue,
                        valueRange = 1f..99f,
                        steps = 97,
                        onValueChange = { thresholdValue = it },
                        onValueChangeFinished = { viewModel.saveWarningThreshold(thresholdValue.roundToInt()) },
                    )
                }

                AppCard {
                    // Spec 003 FR-002: number used by "Zamów wywóz".
                    CardTitle("Firma asenizacyjna", icon = AppIcons.LocalShipping)
                    OutlinedTextField(
                        value = phoneText,
                        onValueChange = {
                            phoneText = it
                            phoneError = null
                        },
                        label = { Text("Numer telefonu") },
                        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                        isError = phoneError != null,
                        supportingText = { Text(phoneError ?: "Na ten numer wyślemy SMS z zamówieniem wywozu") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { savePhone() }),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SaveButton(enabled = phoneText != state.pumpingCompanyPhoneText, onClick = savePhone)
                }

                AppCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .toggleable(
                                value = reminderEnabled,
                                role = Role.Switch,
                                onValueChange = {
                                    reminderEnabled = it
                                    viewModel.setReminder(it, reminderIntervalValue.roundToInt())
                                },
                            ),
                    ) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Przypomnienia o odczycie", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Powiadomienie, że czas zrobić zdjęcie licznika",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = reminderEnabled, onCheckedChange = null)
                    }
                    if (reminderEnabled) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Częstotliwość", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            ValuePill(everyDays(reminderIntervalValue.roundToInt()))
                        }
                        Slider(
                            value = reminderIntervalValue,
                            valueRange = 1f..30f,
                            steps = 28,
                            onValueChange = { reminderIntervalValue = it },
                            onValueChangeFinished = { viewModel.setReminder(true, reminderIntervalValue.roundToInt()) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        FilledTonalButton(enabled = enabled, onClick = onClick) { Text("Zapisz") }
    }
}

/** Current slider value shown as a compact tonal label. */
@Composable
private fun ValuePill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** "co 1 dzień", "co 7 dni". */
private fun everyDays(days: Int) = if (days == 1) "co 1 dzień" else "co $days dni"
