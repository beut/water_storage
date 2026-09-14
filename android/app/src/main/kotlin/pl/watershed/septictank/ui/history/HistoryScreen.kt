package pl.watershed.septictank.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import pl.watershed.septictank.SepticTankApplication

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as SepticTankApplication).container
    val viewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HistoryViewModel(container.meterReadingRepository, container.pumpingEventRepository)
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            TextButton(onClick = onBack) { Text("Wróć") }

            Text("Historia wywozów ścieków", modifier = Modifier.padding(top = 12.dp))
            LazyColumn {
                items(state.pumpingEvents) { event ->
                    Text("${dateFormat.format(Date(event.timestampMillis))} — odczyt bazowy #${event.baselineReadingId}")
                }
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Text("Historia odczytów licznika")
            LazyColumn {
                items(state.readings) { reading ->
                    Text(
                        "${dateFormat.format(Date(reading.timestampMillis))} — " +
                            "%.3f m³ (${reading.source})".format(reading.valueLiters / 1000.0),
                    )
                }
            }
        }
    }
}
