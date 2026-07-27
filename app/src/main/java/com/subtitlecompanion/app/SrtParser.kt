package com.subtitlecompanion.app

import java.util.regex.Pattern

object SrtParser {
    private val timePattern = Pattern.compile("(\\d+):(\\d{2}):(\\d{2})[.,](\\d{3})")
    private val timeLinePattern = Pattern.compile(
        "(\\d+:\\d{2}:\\d{2}[.,]\\d{3})\\s*-->\\s*(\\d+:\\d{2}:\\d{2}[.,]\\d{3})"
    )

    private fun parseTime(s: String): Long {
        val m = timePattern.matcher(s.trim())
        if (!m.find()) return 0L
        val h = m.group(1)!!.toLong()
        val min = m.group(2)!!.toLong()
        val sec = m.group(3)!!.toLong()
        val ms = m.group(4)!!.toLong()
        return h * 3600000L + min * 60000L + sec * 1000L + ms
    }

    fun parse(text: String): List<Cue> {
        val blocks = text.replace("\r", "").split(Regex("\n\n+"))
        val out = mutableListOf<Cue>()
        for (block in blocks) {
            val lines = block.split("\n").filter { it.trim().isNotEmpty() }
            if (lines.size < 2) continue
            var idx = 0
            if (lines[0].trim().matches(Regex("\\d+"))) idx = 1
            if (idx >= lines.size) continue
            val timeLine = lines[idx]
            val tm = timeLinePattern.matcher(timeLine)
            if (!tm.find()) continue
            val start = parseTime(tm.group(1)!!)
            val end = parseTime(tm.group(2)!!)
            val bodyText = lines.drop(idx + 1).joinToString(" ")
                .replace(Regex("<[^>]+>"), "").trim()
            if (bodyText.isNotEmpty()) out.add(Cue(start, end, bodyText))
        }
        return out.sortedBy { it.startMs }
    }
}
