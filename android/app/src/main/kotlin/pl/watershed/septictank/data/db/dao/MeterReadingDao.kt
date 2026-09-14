package pl.watershed.septictank.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.watershed.septictank.data.db.entities.MeterReadingEntity

@Dao
interface MeterReadingDao {
    @Insert
    suspend fun insert(reading: MeterReadingEntity): Long

    @Update
    suspend fun update(reading: MeterReadingEntity)

    @Query("SELECT * FROM meter_readings ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun getLatest(): MeterReadingEntity?

    @Query("SELECT * FROM meter_readings WHERE id = :id")
    suspend fun getById(id: Long): MeterReadingEntity?

    @Query("SELECT * FROM meter_readings ORDER BY timestampMillis ASC LIMIT 1")
    suspend fun getFirst(): MeterReadingEntity?

    @Query("SELECT * FROM meter_readings ORDER BY timestampMillis DESC")
    fun observeHistory(): Flow<List<MeterReadingEntity>>
}
