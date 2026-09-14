package pl.watershed.septictank.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pl.watershed.septictank.data.db.entities.TankConfigurationEntity

@Dao
interface TankConfigurationDao {
    @Upsert
    suspend fun upsert(configuration: TankConfigurationEntity)

    @Query("SELECT * FROM tank_configuration WHERE id = ${TankConfigurationEntity.SINGLETON_ID} LIMIT 1")
    suspend fun get(): TankConfigurationEntity?

    @Query("SELECT * FROM tank_configuration WHERE id = ${TankConfigurationEntity.SINGLETON_ID} LIMIT 1")
    fun observe(): Flow<TankConfigurationEntity?>
}
