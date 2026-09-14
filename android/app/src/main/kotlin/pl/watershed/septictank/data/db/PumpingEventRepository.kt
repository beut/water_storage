package pl.watershed.septictank.data.db

import kotlinx.coroutines.flow.Flow
import pl.watershed.septictank.data.db.dao.MeterReadingDao
import pl.watershed.septictank.data.db.dao.PumpingEventDao
import pl.watershed.septictank.data.db.entities.PumpingEventEntity

/**
 * Repozytorium zdarzeń wywozu ścieków (data-model.md -> PumpingEvent, FR-006, FR-007).
 *
 * [registerPumping] egzekwuje regułę walidacji z data-model.md: `baselineReadingId` MUST
 * wskazywać na najnowszy dostępny odczyt w momencie wywozu; jeśli żaden odczyt nie istnieje,
 * akcja jest zablokowana (zwraca null) -- patrz Edge Case "pierwsze uruchomienie" w spec.md.
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
