package pl.watershed.septictank.ui.pumping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import pl.watershed.septictank.SepticTankApplication
import pl.watershed.septictank.domain.pumping.PumpingHistoryEntry
import pl.watershed.septictank.domain.pumping.PumpingHistorySummary
import pl.watershed.septictank.ui.common.AppCard
import pl.watershed.septictank.ui.common.AppScaffold
import pl.watershed.septictank.ui.common.CardTitle
import pl.watershed.septictank.ui.common.CenteredContent
import pl.watershed.septictank.ui.common.EmptyState
import pl.watershed.septictank.ui.common.IconBadge
import pl.watershed.septictank.ui.common.InfoRow
import pl.watershed.septictank.ui.common.ScreenHorizontalPadding
import pl.watershed.septictank.ui.common.SectionHeader
import pl.watershed.septictank.ui.theme.AppIcons

/** Past pumpings: when, after how many days, and how much was emptied. */
@Composable
fun PumpingHistoryScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as SepticTankApplication).container
    val viewModel: PumpingHistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PumpingHistoryViewModel(
                    container.meterReadingRepository,
                    container.pumpingEventRepository,
                    container.tankConfigurationRepository,
                )
            }
        },
    )
    val history by viewModel.history.collectAsState()

    AppScaffold(title = "Historia wywozów", onBack = onBack) { innerPadding ->
        val current = history ?: return@AppScaffold
        if (current.entries.isEmpty()) {
            EmptyState(
                icon = AppIcons.LocalShipping,
                title = "Brak wywozów",
                message = "Nie zarejestrowano jeszcze żadnego wywozu. Po opróżnieniu zbiornika wybierz " +
                    "„Zarejestruj wykonany wywóz” na ekranie głównym.",
                modifier = Modifier.padding(innerPadding),
            )
            return@AppScaffold
        }

        CenteredContent(modifier = Modifier.padding(innerPadding)) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = ScreenHorizontalPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { SummaryCard(current.summary) }
                item { SectionHeader("Wszystkie wywozy") }
                items(current.entries, key = { it.pumpingEventId }) { entry -> PumpingEntryCard(entry) }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: PumpingHistorySummary) {
    AppCard {
        CardTitle("Podsumowanie")
        Column {
            PumpingHistoryTexts.summaryRows(summary).forEachIndexed { index, (label, value) ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                InfoRow(label = label, value = value, emphasized = true)
            }
        }
    }
}

@Composable
private fun PumpingEntryCard(entry: PumpingHistoryEntry) {
    val texts = PumpingHistoryTexts.entry(entry, ZoneId.systemDefault())
    AppCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconBadge(AppIcons.LocalShipping)
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(texts.date, style = MaterialTheme.typography.titleMedium)
                Text(texts.weekday, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                texts.volume?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            texts.interval?.let {
                Text(
                    it,
                    style = if (entry.isFirstRecorded) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleSmall,
                    color = if (entry.isFirstRecorded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/** Display strings for the pumping history screen, kept plain so they can be unit-tested. */
internal object PumpingHistoryTexts {
    private val POLISH = Locale("pl", "PL")
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    data class EntryTexts(val date: String, val weekday: String, val interval: String?, val volume: String?)

    fun entry(entry: PumpingHistoryEntry, zone: ZoneId): EntryTexts {
        val date = Instant.ofEpochMilli(entry.timestampMillis).atZone(zone).toLocalDate()
        val interval = when {
            entry.isFirstRecorded -> "pierwszy zarejestrowany"
            else -> entry.daysSincePrevious?.let { "po ${afterDays(it)}" }
        }
        val volume = entry.volumeLiters?.let { liters ->
            "Wywieziono ok. ${m3(liters.toDouble())}${percentSuffix(entry.volumePercentOfCapacity)}"
        }
        return EntryTexts(
            date = date.format(DATE_FORMAT),
            weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, POLISH),
            interval = interval,
            volume = volume,
        )
    }

    fun summaryRows(summary: PumpingHistorySummary): List<Pair<String, String>> = buildList {
        add("Liczba wywozów" to summary.count.toString())
        summary.averageIntervalDays?.let { add("Średnio co" to daysPhrase(Math.round(it))) }
        summary.averageVolumeLiters?.let { add("Średnio wywożone" to m3(it)) }
    }

    /** "1 dzień", "5 dni". */
    private fun daysPhrase(days: Long) = if (days == 1L) "1 dzień" else "$days dni"

    /** "po 1 dniu", "po 36 dniach". */
    private fun afterDays(days: Long) = if (days == 1L) "1 dniu" else "$days dniach"

    // Numbers are formatted on their own: a '%' must never reach format() as part of the pattern.
    private fun m3(liters: Double) = "${"%.3f".format(liters / 1000.0)} m³"

    private fun percentSuffix(percent: Double?) = percent?.let { " (${"%.0f".format(it)}%)" } ?: ""
}
