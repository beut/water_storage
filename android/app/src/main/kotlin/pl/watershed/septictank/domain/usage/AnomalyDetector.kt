package pl.watershed.septictank.domain.usage

import pl.watershed.septictank.data.db.entities.MeterReadingEntity

/**
 * Wykrywa anomalie odczytu: nowa wartość niższa niż poprzedni odczyt (data-model.md ->
 * MeterReading -- reguła walidacji, FR-011). Anomalny odczyt wymaga jawnego potwierdzenia
 * użytkownika (patrz ReadingConfirmationScreen, T016) zanim zostanie potraktowany jak zwykły.
 */
object AnomalyDetector {
    fun isAnomalous(newValueLiters: Long, previousLatest: MeterReadingEntity?): Boolean {
        if (previousLatest == null) return false
        return newValueLiters < previousLatest.valueLiters
    }
}
