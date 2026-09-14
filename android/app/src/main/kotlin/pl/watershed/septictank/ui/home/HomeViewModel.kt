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
import pl.watershed.septictank.domain.usage.AnomalyDetector
import pl.watershed.septictank.domain.usage.UsageCalculator
import pl.watershed.septictank.domain.usage.UsageState

/** Krok przepływu rejestrowania odczytu (US1, FR-001..FR-003, FR-011). */
sealed interface ReadingFlowStep {
    data object Idle : ReadingFlowStep
    data object Capturing : ReadingFlowStep
    data class Confirming(
        val photoFile: File,
        val suggestedLiters: Long?,
        val isAnomalous: Boolean,
        val ocrRawText: String,
    ) : ReadingFlowStep
}

data class HomeUiState(
    val usageState: UsageState? = null,
    val hasAnyReading: Boolean = false,
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
                // FR-009: ostrzeżenia MUST być wstrzymane bez skonfigurowanej pojemności (Edge Case).
                missingCapacityWarning = latest != null && usage?.capacityLiters == null,
            )
        }
    }

    fun onOpenCamera() {
        _uiState.value = _uiState.value.copy(readingFlowStep = ReadingFlowStep.Capturing)
    }

    fun onCancelReadingFlow() {
        val step = _uiState.value.readingFlowStep
        if (step is ReadingFlowStep.Confirming) {
            photoStorage.delete(step.photoFile.absolutePath)
        }
        _uiState.value = _uiState.value.copy(readingFlowStep = ReadingFlowStep.Idle)
    }

    fun onPhotoCaptured(photoFile: File) {
        viewModelScope.launch {
            // FR-002/FR-014: rozpoznawanie w pełni lokalne, bez wywołań sieciowych.
            val ocrResult = ocrReader.recognize(photoFile)
            val latest = meterReadingRepository.latest()
            val isAnomalous =
                ocrResult.suggestedLiters != null && AnomalyDetector.isAnomalous(ocrResult.suggestedLiters, latest)
            _uiState.value = _uiState.value.copy(
                readingFlowStep = ReadingFlowStep.Confirming(
                    photoFile,
                    ocrResult.suggestedLiters,
                    isAnomalous,
                    ocrResult.rawText,
                ),
            )
        }
    }

    fun confirmReading(valueLiters: Long, source: ReadingSource) {
        val step = _uiState.value.readingFlowStep
        if (step !is ReadingFlowStep.Confirming) return
        viewModelScope.launch {
            meterReadingRepository.save(
                MeterReadingEntity(
                    timestampMillis = System.currentTimeMillis(),
                    valueLiters = valueLiters,
                    photoPath = step.photoFile.absolutePath,
                    source = source,
                    isAnomalous = step.isAnomalous,
                    anomalyAcknowledged = step.isAnomalous,
                ),
            )
            _uiState.value = _uiState.value.copy(readingFlowStep = ReadingFlowStep.Idle)
            refreshUsage()
        }
    }

    /** FR-006: zablokowane, gdy nie istnieje jeszcze żaden odczyt (brak punktu bazowego). */
    fun registerPumping() {
        viewModelScope.launch {
            pumpingEventRepository.registerPumping(System.currentTimeMillis())
            refreshUsage()
        }
    }
}
