package pl.watershed.septictank.data.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import pl.watershed.septictank.util.Logging

sealed interface SmsSendResult {
    data object Sent : SmsSendResult
    data class Failed(val reason: String) : SmsSendResult
}

/**
 * Sends a single SMS and reports whether it actually left the phone (spec 003 FR-005, FR-007).
 * Behind an interface so the sending mechanism can be swapped (e.g. for ACTION_SENDTO, research.md §1).
 */
interface SmsSender {
    suspend fun send(phoneNumber: String, text: String): SmsSendResult
}

/** [SmsManager]-based sender; the result comes from the `sentIntent` broadcast (research.md §2). */
class AndroidSmsSender(context: Context) : SmsSender {

    private val appContext = context.applicationContext

    override suspend fun send(phoneNumber: String, text: String): SmsSendResult {
        val smsManager = smsManager() ?: return SmsSendResult.Failed("Ten telefon nie może wysyłać SMS-ów")
        // Never retried automatically -- a missing confirmation must not lead to a second SMS.
        return withTimeoutOrNull(SENT_TIMEOUT_MILLIS) { sendAndAwaitResult(smsManager, phoneNumber, text) }
            ?: SmsSendResult.Failed("Nie udało się potwierdzić wysłania SMS-a")
    }

    private suspend fun sendAndAwaitResult(smsManager: SmsManager, phoneNumber: String, text: String): SmsSendResult =
        suspendCancellableCoroutine { continuation ->
            val action = "${appContext.packageName}.SMS_SENT.${UUID.randomUUID()}"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    unregister(this)
                    if (continuation.isActive) continuation.resume(mapResultCode(resultCode))
                }
            }
            ContextCompat.registerReceiver(appContext, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            continuation.invokeOnCancellation { unregister(receiver) }

            val sentIntent = PendingIntent.getBroadcast(
                appContext,
                0,
                Intent(action).setPackage(appContext.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
            )
            try {
                smsManager.sendTextMessage(phoneNumber, null, text, sentIntent, null)
                Logging.d("Pumping order SMS handed to SmsManager")
            } catch (e: SecurityException) {
                Logging.w("SEND_SMS permission missing", e)
                unregister(receiver)
                continuation.resume(SmsSendResult.Failed("Brak uprawnienia do wysyłania SMS-ów"))
            } catch (e: RuntimeException) {
                Logging.e("Sending pumping order SMS failed", e)
                unregister(receiver)
                continuation.resume(SmsSendResult.Failed("Nie udało się wysłać SMS-a"))
            }
        }

    private fun unregister(receiver: BroadcastReceiver) {
        try {
            appContext.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered (result arrived and the coroutine was cancelled at the same time).
        }
    }

    private fun mapResultCode(resultCode: Int): SmsSendResult {
        if (resultCode == Activity.RESULT_OK) return SmsSendResult.Sent
        Logging.w("Pumping order SMS failed, resultCode=$resultCode")
        return SmsSendResult.Failed(
            when (resultCode) {
                SmsManager.RESULT_ERROR_RADIO_OFF -> "Telefon jest w trybie samolotowym lub radio jest wyłączone"
                SmsManager.RESULT_ERROR_NO_SERVICE -> "Brak zasięgu sieci komórkowej"
                else -> "Nie udało się wysłać SMS-a (kod $resultCode)"
            },
        )
    }

    private fun smsManager(): SmsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

    private companion object {
        const val SENT_TIMEOUT_MILLIS = 60_000L
    }
}
