package pl.watershed.septictank.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.watershed.septictank.data.db.entities.PumpingEventEntity

@Dao
interface PumpingEventDao {
    @Insert
    suspend fun insert(event: PumpingEventEntity): Long

    @Query("SELECT * FROM pumping_events ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun getLatest(): PumpingEventEntity?

    @Query("SELECT * FROM pumping_events ORDER BY timestampMillis DESC")
    fun observeHistory(): Flow<List<PumpingEventEntity>>
}
