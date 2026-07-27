package com.subtitlecompanion.app

import android.content.Context
import android.content.SharedPreferences

data class CaptionSettings(
    var shape: String = "bar",
    var position: String = "middle",
    var bg: String = "solid",
    var opacity: Int = 70,
    var fontSize: Int = 26,
    var color: String = "#F2EDE2",
    var showNext: Boolean = false,
    // Left blank on purpose: nobody should trust a guessed package name here.
    // Use the in-app "Detect now" button while Fanjiao is playing to find the
    // real value, then save it.
    var fanjiaoPackage: String = ""
)

object SettingsStore {
    private const val PREFS = "subtitle_companion_prefs"
    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        if (!::prefs.isInitialized) {
            prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun load(): CaptionSettings = CaptionSettings(
        shape = prefs.getString("shape", "bar") ?: "bar",
        position = prefs.getString("position", "middle") ?: "middle",
        bg = prefs.getString("bg", "solid") ?: "solid",
        opacity = prefs.getInt("opacity", 70),
        fontSize = prefs.getInt("fontSize", 26),
        color = prefs.getString("color", "#F2EDE2") ?: "#F2EDE2",
        showNext = prefs.getBoolean("showNext", false),
        fanjiaoPackage = prefs.getString("fanjiaoPackage", "") ?: ""
    )

    fun save(s: CaptionSettings) {
        prefs.edit()
            .putString("shape", s.shape)
            .putString("position", s.position)
            .putString("bg", s.bg)
            .putInt("opacity", s.opacity)
            .putInt("fontSize", s.fontSize)
            .putString("color", s.color)
            .putBoolean("showNext", s.showNext)
            .putString("fanjiaoPackage", s.fanjiaoPackage)
            .apply()
    }
}
