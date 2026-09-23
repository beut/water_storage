package pl.watershed.septictank.domain.history

import pl.watershed.septictank.data.db.entities.ReadingSource

/**
 * Point type on the History chart (data-model.md -> HistoryPoint, FR-002): distinguishes a meter
 * reading from a pumping event, so the two can be rendered with different marker shapes.
 */
enum class HistoryPointType {
    READING,
    PUMPING,
}

/**
 * A single point on the History trend chart (data-model.md -> HistoryPoint) -- computed by
 * [pl.watershed.septictank.domain.history.HistoryTrendCalculator], never persisted.
 *
 * [fillLiters] is the tank fill level at [timestampMillis], relative to the baseline reading
 * active at that moment (see HistoryTrendCalculator for the exact rule) -- not the raw water
 * meter value, which increases monotonically forever.
 */
data class HistoryPoint(
    val timestampMillis: Long,
    val type: HistoryPointType,
    val fillLiters: Long,
    val fillPercentOfCapacity: Double?,
    val sourceReadingId: Long?,
    val pumpingEventId: Long?,
    val readingSource: ReadingSource?,
    val isAnomalous: Boolean,
)
