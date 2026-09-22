package pl.watershed.septictank.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.watershed.septictank.SepticTankApplication

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

    var capacityText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Witaj! Zanim zaczniesz, podaj pojemność swojego zbiornika (szamba) w m³.")
            OutlinedTextField(
                value = capacityText,
                onValueChange = { capacityText = it },
                isError = error,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Button(
                modifier = Modifier.padding(top = 16.dp),
                onClick = {
                    if (viewModel.saveCapacity(capacityText)) {
                        onCompleted()
                    } else {
                        error = true
                    }
                },
            ) { Text("Zapisz i przejdź do aplikacji") }
        }
    }
}
