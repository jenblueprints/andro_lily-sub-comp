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
 * once it sees it actually advancing in step with wall time. Until then (or if it
 * never does), external updates are treated as a simple start/pause trigger.
 */
object PlaybackClock {
    var cues: List<Cue> = emptyList()
        private set
    var totalMs: Long = 0
        private set

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
        tick()
    }

    fun onExternalUpdate(isPlayingExternal: Boolean, positionMs: Long?) {
        val nowNanos = System.nanoTime()
        if (positionMs != null && positionMs >= 0) {
            if (lastExternalPosMs >= 0) {
                val posDeltaMs = positionMs - lastExternalPosMs
                val wallDeltaMs = (nowNanos - lastExternalWallNanos) / 1_000_000L
                if (wallDeltaMs in 200..8000 && posDeltaMs in (wallDeltaMs / 4)..(wallDeltaMs * 4)) {
                    trustedSampleStreak++
                } else if (posDeltaMs != 0L) {
                    trustedSampleStreak = 0
                }
            }
            trustExternalPosition = trustedSampleStreak >= 2
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
            if (effective >= cues[i].startMs && effective < cues[i].endMs) { activeIdx = i; break }
            if (effective < cues[i].startMs) break
        }
        val snapshot = listeners.toList()
        snapshot.forEach { it.onTick(elapsed, totalMs, activeIdx) }
    }
}
