package pl.watershed.septictank.data.db

import kotlinx.coroutines.flow.Flow
import pl.watershed.septictank.data.db.dao.MeterReadingDao
import pl.watershed.septictank.data.db.entities.MeterReadingEntity

/** Meter reading repository (data-model.md -> MeterReading, FR-004). */
class MeterReadingRepository(private val dao: MeterReadingDao) {

    /** @param reading.valueLiters MUST be >= 0 (data-model.md -> MeterReading.valueM3 validation rule). */
    suspend fun save(reading: MeterReadingEntity): Long {
        require(reading.valueLiters >= 0) { "valueLiters MUST być >= 0" }
        return dao.insert(reading)
    }

    suspend fun acknowledgeAnomaly(reading: MeterReadingEntity) {
        dao.update(reading.copy(anomalyAcknowledged = true))
    }

    suspend fun latest(): MeterReadingEntity? = dao.getLatest()

    suspend fun first(): MeterReadingEntity? = dao.getFirst()

    suspend fun byId(id: Long): MeterReadingEntity? = dao.getById(id)

    fun observeHistory(): Flow<List<MeterReadingEntity>> = dao.observeHistory()
}
