package pl.watershed.septictank.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.watershed.septictank.SepticTankApplication
import pl.watershed.septictank.ui.common.IconBadge
import pl.watershed.septictank.ui.common.MaxContentWidth
import pl.watershed.septictank.ui.theme.AppIcons

/**
 * Initial setup on first launch (US4, Acceptance Scenario 1): the user MUST provide the tank
 * capacity before the app starts computing warnings (FR-008).
 */
@Composable
fun OnboardingScreen(onCompleted: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as SepticTankApplication).container
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(container.tankConfigurationRepository, context.applicationContext) }
        },
    )

    var capacityText by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    val submit: () -> Unit = {
        if (viewModel.saveCapacity(capacityText)) {
            onCompleted()
        } else {
            error = true
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
            ) {
                IconBadge(AppIcons.WaterDrop, size = 88.dp)
                Spacer(Modifier.height(24.dp))
                Text(
                    "Witaj w Monitorze szamba",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Na podstawie odczytów licznika wody aplikacja oszacuje, jak bardzo zapełniony jest " +
                        "zbiornik i kiedy zamówić wywóz. Zacznij od podania jego pojemności.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                OutlinedTextField(
                    value = capacityText,
                    onValueChange = {
                        capacityText = it
                        error = false
                    },
                    label = { Text("Pojemność zbiornika") },
                    placeholder = { Text("np. 10") },
                    suffix = { Text("m³") },
                    singleLine = true,
                    isError = error,
                    supportingText = {
                        Text(if (error) "Podaj liczbę większą od zera, np. 10" else "Znajdziesz ją w dokumentacji zbiornika. Możesz ją później zmienić.")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    enabled = capacityText.isNotBlank(),
                    onClick = submit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("Zapisz i rozpocznij") }
            }
        }
    }
}
