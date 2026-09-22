package pl.watershed.septictank.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import pl.watershed.septictank.data.db.dao.MeterReadingDao
import pl.watershed.septictank.data.db.dao.PumpingEventDao
import pl.watershed.septictank.data.db.dao.TankConfigurationDao
import pl.watershed.septictank.data.db.entities.MeterReadingEntity
import pl.watershed.septictank.data.db.entities.PumpingEventEntity
import pl.watershed.septictank.data.db.entities.TankConfigurationEntity

@Database(
    entities = [MeterReadingEntity::class, PumpingEventEntity::class, TankConfigurationEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meterReadingDao(): MeterReadingDao
    abstract fun pumpingEventDao(): PumpingEventDao
    abstract fun tankConfigurationDao(): TankConfigurationDao

    companion object {
        private const val DATABASE_NAME = "septic_tank_monitor.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
