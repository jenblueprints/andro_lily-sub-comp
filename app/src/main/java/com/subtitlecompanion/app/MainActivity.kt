package com.subtitlecompanion.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

// Hardcoded on purpose per your request -- no in-app field for this, so
// nobody else running the app can edit it. Tell me the real URL and I'll
// fill this constant in and ship it; leave it blank and the Support button
// stays hidden.
private const val SUPPORT_LINK_URL = ""

class MainActivity : AppCompatActivity(), ClockListener {

    private lateinit var statusText: TextView
    private lateinit var currentLineText: TextView
    private lateinit var nextLineText: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var timeCurrent: TextView
    private lateinit var timeTotal: TextView
    private lateinit var playButton: MaterialButton
    private lateinit var nudgeValue: TextView
    private lateinit var packageInput: TextInputEditText
    private lateinit var detectedList: TextView
    private lateinit var transcriptAdapter: TranscriptAdapter
    private lateinit var floatingButton: MaterialButton
    private lateinit var folderStatusText: TextView

    private var userIsScrubbing = false
    private var didShowSupportPromptThisSession = false
    private var pulseAnimator: ObjectAnimator? = null
    private var defaultFloatingButtonBg: Drawable? = null

    private val libraryListener: () -> Unit = { runOnUiThread { refreshLibraryStatus() } }

    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { /* some providers don't support this -- fine, just won't survive reboot */ }
            SubtitleLibrary.clearFolder()
            SettingsStore.update { s -> s.lastSingleFileUri = it.toString(); s.libraryMode = "single" }
            loadSrt(it)
            refreshLibraryStatus()
        }
    }

    private val openTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            } catch (e: Exception) { /* fine -- just won't survive reboot */ }
            SubtitleLibrary.setFolder(it)
            statusText.text = "Folder set - will auto-load a subtitle file as Fanjiao's title changes"
            refreshLibraryStatus()
        }
    }

    private val notifPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        SettingsStore.init(this)
        SubtitleLibrary.init(this)

        statusText = findViewById(R.id.statusText)
        currentLineText = findViewById(R.id.currentLineText)
        nextLineText = findViewById(R.id.nextLineText)
        seekBar = findViewById(R.id.seekBar)
        timeCurrent = findViewById(R.id.timeCurrent)
        timeTotal = findViewById(R.id.timeTotal)
        playButton = findViewById(R.id.playButton)
        nudgeValue = findViewById(R.id.nudgeValue)
        packageInput = findViewById(R.id.packageInput)
        detectedList = findViewById(R.id.detectedList)
        folderStatusText = findViewById(R.id.folderStatusText)

        val transcriptRecycler = findViewById<RecyclerView>(R.id.transcriptList)
        transcriptAdapter = TranscriptAdapter { cue -> PlaybackClock.jumpTo(cue.startMs) }
        transcriptRecycler.layoutManager = LinearLayoutManager(this)
        transcriptRecycler.adapter = transcriptAdapter

        findViewById<MaterialButton>(R.id.loadButton).setOnClickListener {
            openDoc.launch(arrayOf("*/*"))
        }
        findViewById<MaterialButton>(R.id.loadFolderButton).setOnClickListener {
            openTree.launch(null)
        }
        findViewById<MaterialButton>(R.id.clearFolderButton).setOnClickListener {
            SubtitleLibrary.clearFolder()
            statusText.text = "Folder mode off - load a single file above"
            refreshLibraryStatus()
        }

        playButton.setOnClickListener {
            if (PlaybackClock.playing) PlaybackClock.pause() else PlaybackClock.play()
        }

        findViewById<MaterialButton>(R.id.back15).setOnClickListener {
            PlaybackClock.jumpTo(PlaybackClock.currentElapsed() - 15000)
        }
        findViewById<MaterialButton>(R.id.fwd15).setOnClickListener {
            PlaybackClock.jumpTo(PlaybackClock.currentElapsed() + 15000)
        }
        findViewById<MaterialButton>(R.id.nudgeMinus).setOnClickListener {
            PlaybackClock.nudge(-0.5f)
            nudgeValue.text = String.format("%.1fs", PlaybackClock.offsetMs / 1000.0)
        }
        findViewById<MaterialButton>(R.id.nudgePlus).setOnClickListener {
            PlaybackClock.nudge(0.5f)
            nudgeValue.text = String.format("%.1fs", PlaybackClock.offsetMs / 1000.0)
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(sb: SeekBar?) { userIsScrubbing = true }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                userIsScrubbing = false
                val pct = (sb?.progress ?: 0) / 1000.0
                PlaybackClock.jumpTo((pct * PlaybackClock.totalMs).toLong())
            }
        })

        findViewById<MaterialButton>(R.id.notifPermButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<MaterialButton>(R.id.overlayPermButton).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "Already granted", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<MaterialButton>(R.id.detectButton).setOnClickListener {
            val found = FanjiaoListenerService.listActiveSessionPackages(this)
            detectedList.text = if (found.isEmpty())
                "No active media sessions found. Grant notification access above, make sure Fanjiao is actually playing, then try again."
            else
                "Currently active:\n" + found.joinToString("\n")
        }
        findViewById<MaterialButton>(R.id.savePackageButton).setOnClickListener {
            val s = SettingsStore.load()
            s.fanjiaoPackage = packageInput.text?.toString()?.trim() ?: ""
            SettingsStore.save(s)
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }
        floatingButton = findViewById(R.id.floatingButton)
        defaultFloatingButtonBg = floatingButton.background
        floatingButton.setOnClickListener {
            toggleOverlay()
        }
        findViewById<MaterialButton>(R.id.styleButton).setOnClickListener {
            SettingsSheet().show(supportFragmentManager, "settings")
        }
        val supportButton = findViewById<MaterialButton>(R.id.supportButton)
        if (SUPPORT_LINK_URL.isBlank()) {
            supportButton.visibility = View.GONE
        } else {
            supportButton.setOnClickListener { openSupportLink() }
        }

        packageInput.setText(SettingsStore.load().fanjiaoPackage)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        PlaybackClock.addListener(this)
        SubtitleLibrary.addListener(libraryListener)
        restoreLastLoaded()
        refreshLibraryStatus()
    }

    override fun onDestroy() {
        PlaybackClock.removeListener(this)
        SubtitleLibrary.removeListener(libraryListener)
        stopPulse()
        super.onDestroy()
    }

    /** Reopens whatever was loaded last time -- a single file, or a folder
     *  (which will auto-match as soon as Fanjiao reports a title) -- so you
     *  don't have to pick it again every time you open the app. */
    private fun restoreLastLoaded() {
        val s = SettingsStore.load()
        if (s.libraryMode == "folder" && s.subtitleFolderUri.isNotEmpty()) {
            statusText.text = "Folder restored - will auto-load as Fanjiao's title changes"
            return
        }
        if (s.libraryMode == "single" && s.lastSingleFileUri.isNotEmpty()) {
            try {
                loadSrt(Uri.parse(s.lastSingleFileUri))
            } catch (e: Exception) {
                statusText.text = "Couldn't reopen the last file - pick it again"
            }
        }
    }

    private fun refreshLibraryStatus() {
        if (SubtitleLibrary.isFolderMode()) {
            val count = SubtitleLibrary.entries.size
            val matched = SubtitleLibrary.lastMatchedName
            folderStatusText.text = when {
                matched != null -> "Folder mode - $count subtitle files found - now showing: $matched"
                count == 0 -> "Folder mode - no .srt/.ass/.lrc files found in that folder"
                else -> "Folder mode - $count subtitle files found - waiting for Fanjiao to report a title"
            }
        } else {
            folderStatusText.text = "Single-file mode - use \"Load subtitle folder\" to auto-follow Fanjiao's episodes"
        }
    }

    private fun loadSrt(uri: Uri) {
        try {
            val name = displayNameFor(uri)
            contentResolver.openInputStream(uri)?.use { stream ->
                val text = stream.bufferedReader(Charsets.UTF_8).readText()
                val cues = SubtitleParser.parse(name, text)
                if (cues.isEmpty()) {
                    statusText.text = "Couldn't parse that file (.srt, .ass/.ssa, and .lrc are supported)"
                    return
                }
                PlaybackClock.loadCues(cues)
                statusText.text = "${cues.size} lines loaded from $name"
                timeTotal.text = fmt(PlaybackClock.totalMs)
            }
        } catch (e: Exception) {
            statusText.text = "Error reading file: ${e.message}"
        }
    }

    /** SAF content:// URIs don't reliably carry a usable extension in the
     *  URI itself, so ask the content resolver for the real picked file name. */
    private fun displayNameFor(uri: Uri): String {
        var name = uri.lastPathSegment ?: "subtitle.srt"
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx)?.let { name = it }
            }
        } catch (e: Exception) {
            // Fall back to the URI's last path segment already set above.
        } finally {
            cursor?.close()
        }
        return name
    }

    override fun onResume() {
        super.onResume()
        updateFloatingButtonLabel()
        refreshLibraryStatus()
    }

    private fun updateFloatingButtonLabel() {
        if (OverlayService.isRunning) {
            floatingButton.text = "\u23F9  Stop floating subtitles"
            floatingButton.setBackgroundColor(Color.parseColor("#E4574C"))
            startPulse()
        } else {
            floatingButton.text = "Start floating subtitles"
            floatingButton.background = defaultFloatingButtonBg
            stopPulse()
        }
    }

    /** Gentle looping fade so the Stop button is easy to spot at a glance
     *  when you switch back to the app -- same idea as the pulsing record
     *  indicator in subtitle-companion.html. */
    private fun startPulse() {
        if (pulseAnimator != null) return
        val anim = ObjectAnimator.ofFloat(floatingButton, View.ALPHA, 1f, 0.55f, 1f)
        anim.duration = 1100
        anim.repeatCount = ValueAnimator.INFINITE
        anim.interpolator = LinearInterpolator()
        anim.start()
        pulseAnimator = anim
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        floatingButton.alpha = 1f
    }

    private fun toggleOverlay() {
        if (OverlayService.isRunning) {
            startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
            // Give the service a beat to actually tear down before refreshing
            // the label; onDestroy() flips isRunning synchronously on this
            // same thread's next iteration, so a short delay is plenty.
            floatingButton.postDelayed({ updateFloatingButtonLabel() }, 150)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant the floating-window permission first", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        floatingButton.postDelayed({ updateFloatingButtonLabel() }, 150)
        maybeShowSupportPrompt()
    }

    /** A single, low-pressure invite per app session -- not every time
     *  floating subtitles start, and only if a support link is hardcoded in. */
    private fun maybeShowSupportPrompt() {
        if (didShowSupportPromptThisSession || SUPPORT_LINK_URL.isBlank()) return
        didShowSupportPromptThisSession = true
        Toast.makeText(this, "Enjoying it? There's a Support button on the main screen \u2615", Toast.LENGTH_LONG).show()
    }

    private fun openSupportLink() {
        if (SUPPORT_LINK_URL.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_LINK_URL)))
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open that link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fmt(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format("%02d:%02d", m, s)
    }

    private var lastSubmittedCues: List<Cue>? = null

    override fun onTick(elapsedMs: Long, totalMs: Long, activeIndex: Int) {
        timeCurrent.text = fmt(elapsedMs)
        if (!userIsScrubbing && totalMs > 0) {
            seekBar.progress = ((elapsedMs.toDouble() / totalMs) * 1000).toInt().coerceIn(0, 1000)
        }
        playButton.text = if (PlaybackClock.playing) "Pause" else "Play"
        val cues = PlaybackClock.cues
        if (cues !== lastSubmittedCues) {
            // A new list reference means a new file was loaded (e.g. folder
            // mode auto-switching to a different episode) -- refresh the
            // transcript view and total time to match.
            transcriptAdapter.submit(cues)
            lastSubmittedCues = cues
            if (totalMs > 0) timeTotal.text = fmt(totalMs)
        }
        if (activeIndex >= 0) {
            currentLineText.text = cues[activeIndex].text
            nextLineText.text = cues.getOrNull(activeIndex + 1)?.text ?: ""
            transcriptAdapter.setActive(activeIndex)
        } else {
            currentLineText.text = if (PlaybackClock.playing) "\u2026" else "Press play to begin"
            nextLineText.text = cues.firstOrNull { it.startMs > elapsedMs }?.text ?: ""
        }
    }
}
