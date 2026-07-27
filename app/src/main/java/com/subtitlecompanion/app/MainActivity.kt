package com.subtitlecompanion.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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

    private var userIsScrubbing = false

    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { loadSrt(it) }
    }

    private val notifPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        SettingsStore.init(this)

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

        val transcriptRecycler = findViewById<RecyclerView>(R.id.transcriptList)
        transcriptAdapter = TranscriptAdapter { cue -> PlaybackClock.jumpTo(cue.startMs) }
        transcriptRecycler.layoutManager = LinearLayoutManager(this)
        transcriptRecycler.adapter = transcriptAdapter

        findViewById<MaterialButton>(R.id.loadButton).setOnClickListener {
            openDoc.launch(arrayOf("*/*"))
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
        findViewById<MaterialButton>(R.id.floatingButton).setOnClickListener {
            toggleOverlay()
        }
        findViewById<MaterialButton>(R.id.styleButton).setOnClickListener {
            SettingsSheet().show(supportFragmentManager, "settings")
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
    }

    override fun onDestroy() {
        PlaybackClock.removeListener(this)
        super.onDestroy()
    }

    private fun loadSrt(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val text = stream.bufferedReader(Charsets.UTF_8).readText()
                val cues = SrtParser.parse(text)
                if (cues.isEmpty()) {
                    statusText.text = "Couldn't parse that file"
                    return
                }
                PlaybackClock.loadCues(cues)
                transcriptAdapter.submit(cues)
                statusText.text = "${cues.size} lines loaded"
                timeTotal.text = fmt(PlaybackClock.totalMs)
            }
        } catch (e: Exception) {
            statusText.text = "Error reading file: ${e.message}"
        }
    }

    private fun toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant the floating-window permission first", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun fmt(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format("%02d:%02d", m, s)
    }

    override fun onTick(elapsedMs: Long, totalMs: Long, activeIndex: Int) {
        timeCurrent.text = fmt(elapsedMs)
        if (!userIsScrubbing && totalMs > 0) {
            seekBar.progress = ((elapsedMs.toDouble() / totalMs) * 1000).toInt().coerceIn(0, 1000)
        }
        playButton.text = if (PlaybackClock.playing) "Pause" else "Play"
        val cues = PlaybackClock.cues
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
