package pl.watershed.septictank.ui.pumping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pl.watershed.septictank.data.db.MeterReadingRepository
import pl.watershed.septictank.data.db.PumpingEventRepository
import pl.watershed.septictank.data.db.TankConfigurationRepository
import pl.watershed.septictank.domain.pumping.PumpingHistory
import pl.watershed.septictank.domain.pumping.PumpingHistoryCalculator

/** Pumping history screen state: `null` until the first database emission. */
class PumpingHistoryViewModel(
    meterReadingRepository: MeterReadingRepository,
    pumpingEventRepository: PumpingEventRepository,
    tankConfigurationRepository: TankConfigurationRepository,
) : ViewModel() {
    val history: StateFlow<PumpingHistory?> = combine(
        meterReadingRepository.observeHistory(),
        pumpingEventRepository.observeHistory(),
        tankConfigurationRepository.observe(),
    ) { readings, pumpingEvents, configuration ->
        PumpingHistoryCalculator.calculate(readings, pumpingEvents, configuration?.capacityLiters, ZoneId.systemDefault())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
