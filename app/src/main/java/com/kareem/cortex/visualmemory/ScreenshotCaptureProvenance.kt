package com.kareem.cortex.visualmemory

import android.app.Activity
import android.content.Context
import android.os.Build
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.Executor

object ScreenshotCaptureProvenance {
    private const val PREFS = "cortex_visual_provenance"
    private const val KEY_TIME = "last_self_capture_time"
    private const val KEY_SCREEN = "last_self_capture_screen"
    private const val MATCH_WINDOW_MS = 5_000L

    private val callbacks = WeakHashMap<Activity, Any>()

    @JvmStatic
    fun register(activity: Activity) {
        if (Build.VERSION.SDK_INT < 34) return
        synchronized(callbacks) {
            if (callbacks.containsKey(activity)) return
            val executor = Executor { command -> activity.runOnUiThread(command) }
            val callback = Activity.ScreenCaptureCallback {
                record(activity, activity.javaClass.simpleName)
            }
            activity.registerScreenCaptureCallback(executor, callback)
            callbacks[activity] = callback
        }
    }

    private fun record(context: Context, screen: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_TIME, System.currentTimeMillis())
            .putString(KEY_SCREEN, screen)
            .apply()
    }

    @JvmStatic
    fun classify(context: Context, capturedAtMillis: Long?): CaptureMatch {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val marker = prefs.getLong(KEY_TIME, 0L)
        val screen = prefs.getString(KEY_SCREEN, null)
        val captured = capturedAtMillis ?: return CaptureMatch.external()
        val delta = kotlin.math.abs(captured - marker)
        return if (marker > 0L && delta <= MATCH_WINDOW_MS) {
            CaptureMatch(
                origin = "CORTEX_SELF_CAPTURE",
                selfReferenceScore = 1.0f,
                derivationDepth = 1,
                knowledgeEligible = false,
                reason = "Android screenshot callback matched Cortex screen " + (screen ?: "unknown")
            )
        } else {
            CaptureMatch.external()
        }
    }
}

data class CaptureMatch(
    val origin: String,
    val selfReferenceScore: Float,
    val derivationDepth: Int,
    val knowledgeEligible: Boolean,
    val reason: String?
) {
    companion object {
        fun external() = CaptureMatch(
            origin = "UNKNOWN",
            selfReferenceScore = 0f,
            derivationDepth = 0,
            knowledgeEligible = true,
            reason = null
        )
    }
}
