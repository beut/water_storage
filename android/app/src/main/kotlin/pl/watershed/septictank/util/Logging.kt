package pl.watershed.septictank.util

import android.util.Log

/** Thin wrapper over android.util.Log to unify the tag and have a single place to extend. */
object Logging {
    private const val TAG = "SepticTankMonitor"

    fun d(message: String) = Log.d(TAG, message)

    fun w(message: String, throwable: Throwable? = null) = Log.w(TAG, message, throwable)

    fun e(message: String, throwable: Throwable? = null) = Log.e(TAG, message, throwable)
}
