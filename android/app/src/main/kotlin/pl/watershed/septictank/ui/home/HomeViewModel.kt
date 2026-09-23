package pl.watershed.septictank.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.watershed.septictank.data.db.MeterReadingRepository
import pl.watershed.septictank.data.db.PumpingEventRepository
import pl.watershed.septictank.data.db.TankConfigurationRepository
import pl.watershed.septictank.data.db.entities.MeterReadingEntity
import pl.watershed.septictank.data.db.entities.ReadingSource
import pl.watershed.septictank.data.ocr.MeterOcrReader
import pl.watershed.septictank.data.photo.PhotoStorage
import pl.watershed.septictank.data.sms.SmsSendResult
import pl.watershed.septictank.data.sms.SmsSender
import pl.watershed.septictank.domain.order.PumpingOrderMessage
import pl.watershed.septictank.domain.order.availableOrderDays
import pl.watershed.septictank.domain.usage.UsageCalculator
import pl.watershed.septictank.domain.usage.UsageState

/**
 * Reading-registration flow step (US1, FR-001..FR-003, FR-011, FR-015).
 *
 * [photoFile] is `null` when the user chose to enter the reading manually without a photo
 * (FR-015) -- otherwise it comes from [ReadingFlowStep.Capturing].
 */
sealed interface ReadingFlowStep {
    data object Idle : ReadingFlowStep
    data object Capturing : ReadingFlowStep
    data class Confirming(val photoFile: File?, val suggestedLiters: Long?, val ocrRawText: String) :
        ReadingFlowStep
}

/**
 * "Zamów wywóz" dialog state (spec 003, data-model.md -> state machine). A send only starts from
 * [Choosing], and [Sending] is set before it starts, so one confirmation sends at most one SMS.
 */
sealed interface PumpingOrderState {
    data object Hidden : PumpingOrderState
    data object MissingPhone : PumpingOrderState
    data class Choosing(
        val days: List<LocalDate>,
        val selected: LocalDate,
        val phoneNumber: String,
        val message: String,
    ) : PumpingOrderState
    data class PermissionDenied(val choosing: Choosing) : PumpingOrderState
    data class Sending(val day: LocalDate, val phoneNumber: String) : PumpingOrderState
    data class Sent(val day: LocalDate, val phoneNumber: String) : PumpingOrderState
    data class Failed(val choosing: Choosing, val reason: String) : PumpingOrderState
}

data class HomeUiState(
    val usageState: UsageState? = null,
    val hasAnyReading: Boolean = false,
    val latestReadingLiters: Long? = null,
    val readingFlowStep: ReadingFlowStep = ReadingFlowStep.Idle,
    val missingCapacityWarning: Boolean = false,
    val pumpingOrderState: PumpingOrderState = PumpingOrderState.Hidden,
)

class HomeViewModel(
    private val meterReadingRepository: MeterReadingRepository,
    private val pumpingEventRepository: PumpingEventRepository,
    private val usageCalculator: UsageCalculator,
    private val photoStorage: PhotoStorage,
    private val ocrReader: MeterOcrReader,
    private val tankConfigurationRepository: TankConfigurationRepository,
    private val smsSender: SmsSender,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshUsage()
    }

    private fun refreshUsage() {
        viewModelScope.launch {
            val usage = usageCalculator.calculate()
            val latest = meterReadingRepository.latest()
            _uiState.value = _uiState.value.copy(
                usageState = usage,
                hasAnyReading = latest != null,
                latestReadingLiters = latest?.valueLiters,
                // FR-009: warnings MUST be suppressed without a configured capacity (Edge Case).
                missingCapacityWarning = latest != null && usage?.capacityLiters == null,
            )
        }
    }

    fun onOpenCamera() {
        _uiState.value = _uiState.value.copy(readingFlowStep = ReadingFlowStep.Capturing)
    }

    /** FR-015: allows entering a reading directly, without taking a photo and waiting for OCR. */
    fun onManualEntry() {
        _uiState.value = _uiState.value.copy(
            readingFlowStep = ReadingFlowStep.Confirming(photoFile = null, suggestedLiters = null, ocrRawText = ""),
        )
    }

    fun onCancelReadingFlow() {
        val step = _uiState.value.readingFlowStep
        if (step is ReadingFlowStep.Confirming) {
            step.photoFile?.let { photoStorage.delete(it.absolutePath) }
        }
        _uiState.value = _uiState.value.copy(readingFlowStep = ReadingFlowStep.Idle)
    }

    fun onPhotoCaptured(photoFile: File) {
        viewModelScope.launch {
            // FR-002/FR-014: recognition is fully on-device, no network calls.
            val ocrResult = ocrReader.recognize(photoFile)
            _uiState.value = _uiState.value.copy(
                readingFlowStep = ReadingFlowStep.Confirming(photoFile, ocrResult.suggestedLiters, ocrResult.rawText),
            )
        }
    }

    /**
     * [isAnomalous] is computed in ReadingConfirmationScreen based on the value actually entered
     * (which may have been corrected from the OCR suggestion), not the OCR suggestion -- FR-011.
     */
    fun confirmReading(valueLiters: Long, source: ReadingSource, isAnomalous: Boolean) {
        val step = _uiState.value.readingFlowStep
        if (step !is ReadingFlowStep.Confirming) return
        viewModelScope.launch {
            meterReadingRepository.save(
                MeterReadingEntity(
                    timestampMillis = System.currentTimeMillis(),
                    valueLiters = valueLiters,
                    photoPath = step.photoFile?.absolutePath,
                    source = source,
                    isAnomalous = isAnomalous,
                    anomalyAcknowledged = isAnomalous,
                ),
            )
            _uiState.value = _uiState.value.copy(readingFlowStep = ReadingFlowStep.Idle)
            refreshUsage()
        }
    }

    /** FR-006: blocked when no reading exists yet (no baseline point). */
    fun registerPumping() {
        viewModelScope.launch {
            pumpingEventRepository.registerPumping(System.currentTimeMillis())
            refreshUsage()
        }
    }

    private fun setOrderState(orderState: PumpingOrderState) {
        _uiState.value = _uiState.value.copy(pumpingOrderState = orderState)
    }

    /** Spec 003 FR-001/FR-008: opens the order dialog, or explains that no number is configured. */
    fun onOrderPumpingClick() {
        viewModelScope.launch {
            val phoneNumber = tankConfigurationRepository.get().pumpingCompanyPhone
            if (phoneNumber == null) {
                setOrderState(PumpingOrderState.MissingPhone)
                return@launch
            }
            val days = availableOrderDays(LocalDate.now())
            val selected = days.first()
            setOrderState(
                PumpingOrderState.Choosing(days, selected, phoneNumber, PumpingOrderMessage.forDay(selected.dayOfWeek)),
            )
        }
    }

    fun onOrderDaySelected(day: LocalDate) {
        val choosing = _uiState.value.pumpingOrderState as? PumpingOrderState.Choosing ?: return
        setOrderState(choosing.copy(selected = day, message = PumpingOrderMessage.forDay(day.dayOfWeek)))
    }

    /** Spec 003 FR-005: sends only from [PumpingOrderState.Choosing]; repeated taps are ignored. */
    fun onOrderConfirm() {
        val choosing = _uiState.value.pumpingOrderState as? PumpingOrderState.Choosing ?: return
        setOrderState(PumpingOrderState.Sending(choosing.selected, choosing.phoneNumber))
        // FR-009: ordering never registers a pumping event nor touches usage.
        viewModelScope.launch {
            setOrderState(
                when (val result = smsSender.send(choosing.phoneNumber, choosing.message)) {
                    SmsSendResult.Sent -> PumpingOrderState.Sent(choosing.selected, choosing.phoneNumber)
                    is SmsSendResult.Failed -> PumpingOrderState.Failed(choosing, result.reason)
                },
            )
        }
    }

    fun onOrderPermissionDenied() {
        val choosing = _uiState.value.pumpingOrderState as? PumpingOrderState.Choosing ?: return
        setOrderState(PumpingOrderState.PermissionDenied(choosing))
    }

    /** Back to the same day choice -- every send needs its own confirmation (research.md §6). */
    fun onOrderRetry() {
        when (val orderState = _uiState.value.pumpingOrderState) {
            is PumpingOrderState.Failed -> setOrderState(orderState.choosing)
            is PumpingOrderState.PermissionDenied -> setOrderState(orderState.choosing)
            else -> Unit
        }
    }

    fun onOrderDismiss() {
        if (_uiState.value.pumpingOrderState is PumpingOrderState.Sending) return
        setOrderState(PumpingOrderState.Hidden)
    }
}
