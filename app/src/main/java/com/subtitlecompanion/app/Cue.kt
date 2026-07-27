package com.subtitlecompanion.app

data class Cue(val startMs: Long, val endMs: Long, val text: String)

interface ClockListener {
    fun onTick(elapsedMs: Long, totalMs: Long, activeIndex: Int)
}
