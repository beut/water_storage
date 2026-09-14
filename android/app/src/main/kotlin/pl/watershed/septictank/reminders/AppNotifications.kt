package pl.watershed.septictank.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import pl.watershed.septictank.R
import pl.watershed.septictank.domain.warning.WarningLevel

/** Wysyła przypomnienia o zdjęciu licznika (FR-013) i ostrzeżenia o zapełnieniu zbiornika (FR-009, FR-010). */
class AppNotifications(private val context: Context) {

    init {
        val channel = NotificationChannel(CHANNEL_ID, "Monitor szamba", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun showMeterPhotoReminder() {
        notify(
            id = REMINDER_NOTIFICATION_ID,
            title = "Zrób zdjęcie licznika wody",
            text = "Przypomnienie o cyklicznym odczycie licznika wody.",
        )
    }

    /** FR-010: różne komunikaty dla APPROACHING i EXCEEDED; nic nie wysyła dla NONE. */
    fun showWarning(level: WarningLevel) {
        val text = when (level) {
            WarningLevel.NONE -> return
            WarningLevel.APPROACHING -> "Zbiornik zbliża się do zapełnienia -- rozważ zaplanowanie wywozu."
            WarningLevel.EXCEEDED -> "Zbiornik prawdopodobnie przekroczył pojemność! Zaplanuj wywóz ścieków."
        }
        notify(id = WARNING_NOTIFICATION_ID, title = "Monitor szamba", text = text)
    }

    private fun notify(id: Int, title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        private const val CHANNEL_ID = "septic_tank_monitor"
        private const val REMINDER_NOTIFICATION_ID = 1
        private const val WARNING_NOTIFICATION_ID = 2
    }
}
