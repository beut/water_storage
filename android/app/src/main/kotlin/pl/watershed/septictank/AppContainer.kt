package pl.watershed.septictank

import android.content.Context
import pl.watershed.septictank.data.db.AppDatabase
import pl.watershed.septictank.data.db.MeterReadingRepository
import pl.watershed.septictank.data.db.PumpingEventRepository
import pl.watershed.septictank.data.db.TankConfigurationRepository
import pl.watershed.septictank.data.ocr.MeterOcrReader
import pl.watershed.septictank.data.photo.PhotoStorage
import pl.watershed.septictank.domain.usage.UsageCalculator
import pl.watershed.septictank.reminders.AppNotifications

/**
 * Manual dependency container (research.md -> "Application architecture"): the v1 scope (single
 * tank/meter, no backend) doesn't justify the overhead of a DI framework (e.g. Hilt).
 */
class AppContainer(context: Context) {
    private val database = AppDatabase.getInstance(context)

    val photoStorage = PhotoStorage(context)
    val ocrReader = MeterOcrReader()
    val notifications = AppNotifications(context)

    val meterReadingRepository = MeterReadingRepository(database.meterReadingDao())
    val pumpingEventRepository = PumpingEventRepository(database.pumpingEventDao(), database.meterReadingDao())
    val tankConfigurationRepository = TankConfigurationRepository(database.tankConfigurationDao())

    val usageCalculator = UsageCalculator(meterReadingRepository, pumpingEventRepository, tankConfigurationRepository)
}
