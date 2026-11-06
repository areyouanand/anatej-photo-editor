package com.anatej.photo.data

import android.content.Context
import com.anatej.photo.ui.theme.DesignMode

/**
 * Small, dependency-free preference store. Everything is local; there is no sync layer.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("anatej.settings", Context.MODE_PRIVATE)

    var designMode: DesignMode
        get() = runCatching {
            DesignMode.valueOf(
                prefs.getString(KEY_DESIGN, DesignMode.Signature.name) ?: DesignMode.Signature.name
            )
        }.getOrDefault(DesignMode.Signature)
        set(value) = prefs.edit().putString(KEY_DESIGN, value.name).apply()

    var scanOnImport: Boolean
        get() = prefs.getBoolean(KEY_SCAN_ON_IMPORT, true)
        set(value) = prefs.edit().putBoolean(KEY_SCAN_ON_IMPORT, value).apply()

    var subjectMasking: Boolean
        get() = prefs.getBoolean(KEY_SUBJECT_MASK, true)
        set(value) = prefs.edit().putBoolean(KEY_SUBJECT_MASK, value).apply()

    var autoRescueStrength: Float
        get() = prefs.getFloat(KEY_RESCUE, 1f).coerceIn(0.3f, 2f)
        set(value) = prefs.edit().putFloat(KEY_RESCUE, value.coerceIn(0.3f, 2f)).apply()

    var exportQuality: Int
        get() = prefs.getInt(KEY_EXPORT_QUALITY, 95).coerceIn(70, 100)
        set(value) = prefs.edit().putInt(KEY_EXPORT_QUALITY, value.coerceIn(70, 100)).apply()

    fun restoreDefaults() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_DESIGN = "design_mode"
        const val KEY_SCAN_ON_IMPORT = "scan_on_import"
        const val KEY_SUBJECT_MASK = "subject_masking"
        const val KEY_EXPORT_QUALITY = "export_quality"
        const val KEY_RESCUE = "auto_rescue_strength"
    }
}
