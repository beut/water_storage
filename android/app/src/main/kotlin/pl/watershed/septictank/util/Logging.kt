package pl.watershed.septictank.util

import android.util.Log

/** Cienka warstwa nad android.util.Log, żeby ujednolicić tag i mieć jedno miejsce do rozszerzenia. */
object Logging {
    private const val TAG = "SepticTankMonitor"

    fun d(message: String) = Log.d(TAG, message)

    fun w(message: String, throwable: Throwable? = null) = Log.w(TAG, message, throwable)

    fun e(message: String, throwable: Throwable? = null) = Log.e(TAG, message, throwable)
}
