package com.subtitlecompanion.app

import android.content.Context
import android.content.SharedPreferences

// Real-world testing keeps landing on -0.5s as the correction Fanjiao's
// reported media-session position needs versus the actual audio, so that's
// the built-in starting point rather than 0 -- avoids needing to manually
// nudge it every single time before it's usable.
private const val DEFAULT_OFFSET_MS = -500L

data class CaptionSettings(
    var shape: String = "bar",
    var position: String = "middle",
    var bg: String = "solid",
    var opacity: Int = 70,
    var fontSize: Int = 26,
    var color: String = "#F2EDE2",
    var panelColor: String = "#0B0E13",
    var textBold: Boolean = false,
    var showNext: Boolean = false,
    // Pinch-to-zoom multiplier on the floating window, saved so it doesn't
    // reset to 100% every time you restart floating subtitles.
    var overlayScalePct: Float = 100f,
    // Saved so the manual sync nudge you dial in doesn't need to be redone
    // every single session -- it's applied automatically on the next load.
    // Defaults to -500ms (see DEFAULT_OFFSET_MS below) since that's the
    // correction that's consistently needed out of the box; nudge from
    // there with +/- if a particular episode still feels off.
    var savedOffsetMs: Long = DEFAULT_OFFSET_MS,
    // "single" (one file, remembered across launches) or "folder" (a whole
    // directory, auto-matched by title as Fanjiao switches episodes).
    var libraryMode: String = "single",
    // Persisted content:// URI strings so the last-used file/folder reloads
    // automatically on the next app launch, instead of picking it every time.
    var lastSingleFileUri: String = "",
    var subtitleFolderUri: String = "",
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
        panelColor = prefs.getString("panelColor", "#0B0E13") ?: "#0B0E13",
        textBold = prefs.getBoolean("textBold", false),
        showNext = prefs.getBoolean("showNext", false),
        overlayScalePct = prefs.getFloat("overlayScalePct", 100f),
        savedOffsetMs = prefs.getLong("savedOffsetMs", DEFAULT_OFFSET_MS),
        libraryMode = prefs.getString("libraryMode", "single") ?: "single",
        lastSingleFileUri = prefs.getString("lastSingleFileUri", "") ?: "",
        subtitleFolderUri = prefs.getString("subtitleFolderUri", "") ?: "",
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
            .putString("panelColor", s.panelColor)
            .putBoolean("textBold", s.textBold)
            .putBoolean("showNext", s.showNext)
            .putFloat("overlayScalePct", s.overlayScalePct)
            .putLong("savedOffsetMs", s.savedOffsetMs)
            .putString("libraryMode", s.libraryMode)
            .putString("lastSingleFileUri", s.lastSingleFileUri)
            .putString("subtitleFolderUri", s.subtitleFolderUri)
            .putString("fanjiaoPackage", s.fanjiaoPackage)
            .apply()
    }

    /** Cheap helper so callers that only change one field don't have to
     *  hand-roll a read-modify-write every time. */
    fun update(mutate: (CaptionSettings) -> Unit) {
        val s = load()
        mutate(s)
        save(s)
    }
}
