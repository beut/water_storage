package pl.watershed.septictank.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import pl.watershed.septictank.SepticTankApplication

/**
 * Periodic reminder to take a meter photo (FR-013, research.md -> "Reminders and notifications").
 * Works fully offline -- WorkManager doesn't require an internet connection.
 */
class MeterPhotoReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val notifications = (applicationContext as SepticTankApplication).container.notifications
        notifications.showMeterPhotoReminder()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "meter-photo-reminder"

        fun schedule(context: Context, intervalDays: Int) {
            val request = PeriodicWorkRequestBuilder<MeterPhotoReminderWorker>(
                intervalDays.toLong(),
                TimeUnit.DAYS,
            ).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
