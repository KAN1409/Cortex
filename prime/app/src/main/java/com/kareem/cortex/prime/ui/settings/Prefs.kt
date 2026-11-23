package com.kareem.cortex.prime.ui.settings

import android.content.Context

/** Small persisted settings. displayName == null means "never asked"; "" means the user skipped. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("cortex_prime", Context.MODE_PRIVATE)

    var displayName: String?
        get() = if (sp.contains(KEY_NAME)) sp.getString(KEY_NAME, "") else null
        set(value) { sp.edit().apply { if (value == null) remove(KEY_NAME) else putString(KEY_NAME, value) }.apply() }

    var reduceMotion: Boolean
        get() = sp.getBoolean(KEY_MOTION, false)
        set(value) { sp.edit().putBoolean(KEY_MOTION, value).apply() }

    private companion object { const val KEY_NAME = "display_name"; const val KEY_MOTION = "reduce_motion" }
}
