package pl.watershed.septictank.domain.usage

import pl.watershed.septictank.data.db.entities.MeterReadingEntity

/**
 * Detects anomalous readings: a new value lower than the previous reading (data-model.md ->
 * MeterReading -- validation rule, FR-011). An anomalous reading requires explicit user
 * confirmation (see ReadingConfirmationScreen, T016) before it's treated as normal.
 */
object AnomalyDetector {
    fun isAnomalous(newValueLiters: Long, previousLatest: MeterReadingEntity?): Boolean {
        if (previousLatest == null) return false
        return newValueLiters < previousLatest.valueLiters
    }
}
