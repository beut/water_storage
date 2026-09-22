package pl.watershed.septictank.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pl.watershed.septictank.data.db.MeterReadingRepository
import pl.watershed.septictank.data.db.PumpingEventRepository
import pl.watershed.septictank.data.db.entities.MeterReadingEntity
import pl.watershed.septictank.data.db.entities.PumpingEventEntity

/** FR-012: overview of meter reading history and pumping event history. */
data class HistoryUiState(
    val readings: List<MeterReadingEntity> = emptyList(),
    val pumpingEvents: List<PumpingEventEntity> = emptyList(),
)

class HistoryViewModel(
    meterReadingRepository: MeterReadingRepository,
    pumpingEventRepository: PumpingEventRepository,
) : ViewModel() {
    val uiState: StateFlow<HistoryUiState> = combine(
        meterReadingRepository.observeHistory(),
        pumpingEventRepository.observeHistory(),
    ) { readings, pumpingEvents ->
        HistoryUiState(readings = readings, pumpingEvents = pumpingEvents)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
