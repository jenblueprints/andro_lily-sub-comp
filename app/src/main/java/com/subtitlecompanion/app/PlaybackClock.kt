package com.subtitlecompanion.app

import android.os.Handler
import android.os.Looper

/**
 * Mirrors the timing logic from the original web tool: a wall-clock-driven cue
 * timeline, with a manual offset for fine sync nudges, plus an "external sync"
 * path fed by FanjiaoListenerService.
 *
 * Because we can't know in advance whether Fanjiao's media session reports a
 * real, advancing playback position or only play/pause state, onExternalUpdate()
 * watches a few samples and only trusts the position for direct continuous sync
 * once it sees it actually advancing in step with wall time. Once that trust is
 * earned it's kept for the rest of the session -- including big jumps, since a
 * big jump is exactly what a seek/skip looks like and should be mirrored
 * immediately, not filtered out as noise. Until trust is earned (or if it never
 * is, because Fanjiao doesn't report a real position), external updates are
 * treated as a simple start/pause trigger.
 */
object PlaybackClock {
    var cues: List<Cue> = emptyList()
        private set
    var totalMs: Long = 0
        private set

    // Real dialogue is frequently timed with tiny gaps (well under a second)
    // between the end of one line and the start of the next. Polling every
    // 200ms can land right inside one of those gaps and show a blank panel
    // for a tick before the next line appears -- a visible flicker. Bridging
    // gaps smaller than this keeps the previous line on screen through them.
    private const val GAP_BRIDGE_MS = 450L

    private var elapsedAtStart: Long = 0
    private var wallStartNanos: Long = 0
    var speed: Float = 1f
        private set
    var offsetMs: Long = 0
        private set
    var playing: Boolean = false
        private set

    private var lastExternalPosMs: Long = -1
    private var lastExternalWallNanos: Long = 0
    private var trustExternalPosition: Boolean = false
    private var trustedSampleStreak: Int = 0

    private val listeners = mutableListOf<ClockListener>()
    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null

    fun addListener(l: ClockListener) { listeners.add(l) }
    fun removeListener(l: ClockListener) { listeners.remove(l) }

    fun loadCues(newCues: List<Cue>) {
        cues = newCues.sortedBy { it.startMs }
        totalMs = cues.lastOrNull()?.endMs ?: 0
        elapsedAtStart = 0
        wallStartNanos = System.nanoTime()
        playing = false
        // Restore the last sync nudge you dialed in, so you don't have to
        // re-apply "-0.5s" (or whatever you landed on) at the start of
        // every single session.
        offsetMs = SettingsStore.load().savedOffsetMs
        stopTicking()
        tick()
    }

    fun currentElapsed(): Long {
        if (!playing) return elapsedAtStart
        val dtMs = (System.nanoTime() - wallStartNanos) / 1_000_000L
        return elapsedAtStart + (dtMs * speed).toLong()
    }

    fun play() {
        if (playing || cues.isEmpty()) return
        playing = true
        wallStartNanos = System.nanoTime()
        startTicking()
    }

    fun pause() {
        if (!playing) return
        elapsedAtStart = currentElapsed()
        playing = false
        stopTicking()
        tick()
    }

    fun jumpTo(ms: Long) {
        elapsedAtStart = ms.coerceAtLeast(0)
        wallStartNanos = System.nanoTime()
        tick()
    }

    fun setSpeed(newSpeed: Float) {
        elapsedAtStart = currentElapsed()
        wallStartNanos = System.nanoTime()
        speed = newSpeed
    }

    fun nudge(deltaSec: Float) {
        offsetMs += (deltaSec * 1000).toLong()
        SettingsStore.update { it.savedOffsetMs = offsetMs }
        tick()
    }

    fun onExternalUpdate(isPlayingExternal: Boolean, positionMs: Long?) {
        val nowNanos = System.nanoTime()

        if (positionMs != null && positionMs >= 0) {
            if (!trustExternalPosition) {
                // Still deciding whether Fanjiao's reported position is real
                // (changes on its own between samples) or a dead/static
                // placeholder some apps report. Once we've seen it actually
                // move a couple of times, trust it for the rest of the
                // session -- including later jumps, which are real seeks.
                if (lastExternalPosMs >= 0) {
                    val moved = kotlin.math.abs(positionMs - lastExternalPosMs)
                    if (moved > 300) trustedSampleStreak++
                }
                if (trustedSampleStreak >= 2) trustExternalPosition = true
            }
            lastExternalPosMs = positionMs
            lastExternalWallNanos = nowNanos
        }

        if (trustExternalPosition && positionMs != null && positionMs >= 0) {
            elapsedAtStart = positionMs
            wallStartNanos = nowNanos
            if (isPlayingExternal && !playing) { playing = true; startTicking() }
            if (!isPlayingExternal && playing) { playing = false; stopTicking() }
            tick()
        } else {
            if (isPlayingExternal && !playing) play()
            if (!isPlayingExternal && playing) pause()
        }
    }

    fun isTrustingExternalPosition() = trustExternalPosition

    private fun startTicking() {
        stopTicking()
        tickRunnable = object : Runnable {
            override fun run() {
                tick()
                if (currentElapsed() >= totalMs + 2000 && totalMs > 0) { pause(); return }
                handler.postDelayed(this, 200)
            }
        }
        handler.post(tickRunnable!!)
    }

    private fun stopTicking() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
    }

    private fun tick() {
        val elapsed = currentElapsed()
        val effective = elapsed - offsetMs
        var activeIdx = -1
        for (i in cues.indices) {
            val cue = cues[i]
            if (effective >= cue.startMs && effective < cue.endMs) {
                activeIdx = i
                break
            }
            if (effective < cue.startMs) {
                // We're in a gap before cue i. If it's a short gap right
                // after the previous cue, keep showing the previous cue
                // instead of blanking out for one tick.
                val prev = cues.getOrNull(i - 1)
                if (prev != null && effective - prev.endMs in 0 until GAP_BRIDGE_MS) {
                    activeIdx = i - 1
                }
                break
            }
        }
        // Past the last cue: bridge a short trailing gap the same way.
        if (activeIdx == -1 && cues.isNotEmpty()) {
            val last = cues.last()
            if (effective >= last.endMs && effective - last.endMs < GAP_BRIDGE_MS) {
                activeIdx = cues.lastIndex
            }
        }
        val snapshot = listeners.toList()
        snapshot.forEach { it.onTick(elapsed, totalMs, activeIdx) }
    }
}
