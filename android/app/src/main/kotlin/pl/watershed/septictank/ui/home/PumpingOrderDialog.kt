package pl.watershed.septictank.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import pl.watershed.septictank.domain.order.PumpingOrderMessage
import pl.watershed.septictank.ui.theme.AppIcons

private val POLISH = Locale("pl", "PL")
private val DAY_MONTH_FORMAT = DateTimeFormatter.ofPattern("dd.MM")

/** "Zamów wywóz" dialog (spec 003 FR-004..FR-008, contracts/sms-message.md -> UI contract). */
@Composable
fun PumpingOrderDialog(
    state: PumpingOrderState,
    onDaySelected: (LocalDate) -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (state) {
        PumpingOrderState.Hidden -> Unit
        PumpingOrderState.MissingPhone -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            title = { Text("Zamów wywóz") },
            text = { Text("Nie ustawiono numeru firmy asenizacyjnej. Wpisz go w Ustawieniach.") },
            confirmButton = { TextButton(onClick = onOpenSettings) { Text("Przejdź do ustawień") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
        )
        is PumpingOrderState.Choosing -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(AppIcons.LocalShipping, contentDescription = null) },
            title = { Text("Zamów wywóz") },
            text = { ChoosingContent(state, onDaySelected) },
            confirmButton = { TextButton(onClick = onConfirm) { Text("Wyślij SMS") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
        )
        is PumpingOrderState.PermissionDenied -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            title = { Text("Potrzebne uprawnienie") },
            text = {
                Text(
                    "Aby zamówić wywóz, aplikacja potrzebuje uprawnienia do wysyłania SMS-ów. " +
                        "SMS wysyłany jest tylko po Twoim potwierdzeniu.",
                )
            },
            confirmButton = { TextButton(onClick = onRetry) { Text("Spróbuj ponownie") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
        )
        is PumpingOrderState.Sending -> AlertDialog(
            // Not dismissible while sending -- the result must always be shown (SC-003).
            onDismissRequest = {},
            title = { Text("Zamów wywóz") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text("Wysyłanie…", modifier = Modifier.padding(start = 16.dp))
                }
            },
            confirmButton = {},
        )
        is PumpingOrderState.Sent -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Zamówiono wywóz") },
            text = {
                Text("Zamówiono wywóz ${PumpingOrderMessage.dayPhrase(state.day.dayOfWeek)} (SMS do ${state.phoneNumber})")
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        is PumpingOrderState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Nie wysłano SMS-a") },
            text = { Text(state.reason) },
            confirmButton = { TextButton(onClick = onRetry) { Text("Ponów") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
        )
    }
}

@Composable
private fun ChoosingContent(state: PumpingOrderState.Choosing, onDaySelected: (LocalDate) -> Unit) {
    Column {
        Text("Na kiedy?", style = MaterialTheme.typography.titleSmall)
        Column(modifier = Modifier.selectableGroup().padding(top = 4.dp)) {
            state.days.forEachIndexed { index, day ->
                val label = "${day.dayOfWeek.getDisplayName(TextStyle.FULL, POLISH)} ${day.format(DAY_MONTH_FORMAT)}" +
                    if (index == 0) " (najwcześniej)" else ""
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected = day == state.selected, onClick = { onDaySelected(day) }, role = Role.RadioButton),
                ) {
                    RadioButton(selected = day == state.selected, onClick = null)
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("SMS do: ${state.phoneNumber}", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    "„${state.message}”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
