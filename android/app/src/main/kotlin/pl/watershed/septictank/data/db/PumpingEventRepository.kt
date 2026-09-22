package pl.watershed.septictank.data.db

import kotlinx.coroutines.flow.Flow
import pl.watershed.septictank.data.db.dao.MeterReadingDao
import pl.watershed.septictank.data.db.dao.PumpingEventDao
import pl.watershed.septictank.data.db.entities.PumpingEventEntity

/**
 * Pumping event repository (data-model.md -> PumpingEvent, FR-006, FR-007).
 *
 * [registerPumping] enforces the validation rule from data-model.md: `baselineReadingId` MUST
 * point to the latest available reading at the time of pumping; if no reading exists yet, the
 * action is blocked (returns null) -- see the "first launch" Edge Case in spec.md.
 */
class PumpingEventRepository(
    private val dao: PumpingEventDao,
    private val meterReadingDao: MeterReadingDao,
) {
    suspend fun registerPumping(timestampMillis: Long): PumpingEventEntity? {
        val latestReading = meterReadingDao.getLatest() ?: return null
        val event = PumpingEventEntity(timestampMillis = timestampMillis, baselineReadingId = latestReading.id)
        val id = dao.insert(event)
        return event.copy(id = id)
    }

    suspend fun latest(): PumpingEventEntity? = dao.getLatest()

    fun observeHistory(): Flow<List<PumpingEventEntity>> = dao.observeHistory()
}
