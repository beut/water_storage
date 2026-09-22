package pl.watershed.septictank.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.watershed.septictank.data.db.MeterReadingRepository
import pl.watershed.septictank.data.db.PumpingEventRepository
import pl.watershed.septictank.data.db.entities.MeterReadingEntity
import pl.watershed.septictank.data.db.entities.ReadingSource
import pl.watershed.septictank.data.ocr.MeterOcrReader
import pl.watershed.septictank.data.photo.PhotoStorage
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

data class HomeUiState(
    val usageState: UsageState? = null,
    val hasAnyReading: Boolean = false,
    val latestReadingLiters: Long? = null,
    val readingFlowStep: ReadingFlowStep = ReadingFlowStep.Idle,
    val missingCapacityWarning: Boolean = false,
)

class HomeViewModel(
    private val meterReadingRepository: MeterReadingRepository,
    private val pumpingEventRepository: PumpingEventRepository,
    private val usageCalculator: UsageCalculator,
    private val photoStorage: PhotoStorage,
    private val ocrReader: MeterOcrReader,
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
}
