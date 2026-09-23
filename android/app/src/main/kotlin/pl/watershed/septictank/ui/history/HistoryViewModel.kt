package pl.watershed.septictank.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pl.watershed.septictank.data.db.MeterReadingRepository
import pl.watershed.septictank.data.db.PumpingEventRepository
import pl.watershed.septictank.data.db.TankConfigurationRepository
import pl.watershed.septictank.data.db.entities.TankConfigurationEntity
import pl.watershed.septictank.domain.history.HistoryPoint
import pl.watershed.septictank.domain.history.HistoryTrendCalculator

/**
 * History tab state (data-model.md -> "Zmiana istniejącej struktury UI"): a single chart of
 * [chartPoints] (readings + pumping events) instead of two separate text lists.
 */
data class HistoryUiState(
    val chartPoints: List<HistoryPoint> = emptyList(),
    val warningThresholdPercent: Int? = null,
    val isCapacityConfigured: Boolean = false,
)

class HistoryViewModel(
    meterReadingRepository: MeterReadingRepository,
    pumpingEventRepository: PumpingEventRepository,
    tankConfigurationRepository: TankConfigurationRepository,
) : ViewModel() {
    val uiState: StateFlow<HistoryUiState> = combine(
        meterReadingRepository.observeHistory(),
        pumpingEventRepository.observeHistory(),
        tankConfigurationRepository.observe(),
    ) { readings, pumpingEvents, configuration ->
        val resolvedConfiguration = configuration ?: TankConfigurationEntity(capacityLiters = null)
        val isCapacityConfigured = (resolvedConfiguration.capacityLiters ?: 0) > 0
        HistoryUiState(
            chartPoints = HistoryTrendCalculator.calculate(readings, pumpingEvents, resolvedConfiguration),
            warningThresholdPercent = if (isCapacityConfigured) resolvedConfiguration.warningThresholdPercent else null,
            isCapacityConfigured = isCapacityConfigured,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
