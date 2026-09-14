package pl.watershed.septictank.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.watershed.septictank.data.db.TankConfigurationRepository
import pl.watershed.septictank.data.db.entities.TankConfigurationEntity
import pl.watershed.septictank.reminders.MeterPhotoReminderWorker

data class SettingsUiState(
    val capacityM3Text: String = "",
    val warningThresholdPercent: Int = TankConfigurationEntity.DEFAULT_WARNING_THRESHOLD_PERCENT,
    val reminderEnabled: Boolean = false,
    val reminderIntervalDays: Int = 7,
)

/** FR-008, FR-013: konfiguracja pojemności zbiornika, progu ostrzegawczego i przypomnień. */
class SettingsViewModel(
    private val tankConfigurationRepository: TankConfigurationRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val configuration = tankConfigurationRepository.get()
            _uiState.value = SettingsUiState(
                capacityM3Text = configuration.capacityLiters?.let { "%.3f".format(it / 1000.0) } ?: "",
                warningThresholdPercent = configuration.warningThresholdPercent,
                reminderEnabled = configuration.reminderEnabled,
                reminderIntervalDays = configuration.reminderIntervalDays ?: 7,
            )
        }
    }

    /** @return `true`, gdy zapis się powiódł (wartość poprawna, capacityLiters > 0). */
    fun saveCapacity(capacityM3Text: String): Boolean {
        val capacityM3 = capacityM3Text.replace(',', '.').toDoubleOrNull() ?: return false
        val capacityLiters = Math.round(capacityM3 * 1000.0)
        if (capacityLiters <= 0) return false
        viewModelScope.launch { tankConfigurationRepository.updateCapacity(capacityLiters) }
        _uiState.value = _uiState.value.copy(capacityM3Text = capacityM3Text)
        return true
    }

    fun saveWarningThreshold(percent: Int) {
        if (percent !in 1..99) return
        viewModelScope.launch { tankConfigurationRepository.updateWarningThreshold(percent) }
        _uiState.value = _uiState.value.copy(warningThresholdPercent = percent)
    }

    fun setReminder(enabled: Boolean, intervalDays: Int) {
        viewModelScope.launch {
            tankConfigurationRepository.updateReminder(enabled, if (enabled) intervalDays else null)
        }
        _uiState.value = _uiState.value.copy(reminderEnabled = enabled, reminderIntervalDays = intervalDays)
        if (enabled) {
            MeterPhotoReminderWorker.schedule(appContext, intervalDays)
        } else {
            MeterPhotoReminderWorker.cancel(appContext)
        }
    }
}
