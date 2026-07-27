package com.subtitlecompanion.app

import java.util.regex.Pattern

/**
 * Parses .srt, .ass/.ssa, and .lrc files into the same Cue list the rest of
 * the app already works with. Dispatches on file extension; falls back to
 * the SRT parser (the most forgiving of the three) if the extension is
 * missing or unrecognized.
 */
object SubtitleParser {

    fun parse(fileName: String, text: String): List<Cue> {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "ass", "ssa" -> parseAss(text)
            "lrc" -> parseLrc(text)
            else -> SrtParser.parse(text)
        }
    }

    // --- .ass / .ssa (Advanced SubStation Alpha) ---

    private val assTimePattern = Pattern.compile("(\\d+):(\\d{2}):(\\d{2})[.,](\\d{2,3})")

    private fun parseAssTime(s: String): Long {
        val m = assTimePattern.matcher(s.trim())
        if (!m.find()) return 0L
        val h = m.group(1)!!.toLong()
        val min = m.group(2)!!.toLong()
        val sec = m.group(3)!!.toLong()
        var frac = m.group(4)!!
        // ASS uses centiseconds (2 digits); SRT-style dot notation sometimes
        // sneaks in with 3 -- handle both.
        val ms = if (frac.length == 2) frac.toLong() * 10 else frac.toLong()
        return h * 3600000L + min * 60000L + sec * 1000L + ms
    }

    private fun parseAss(text: String): List<Cue> {
        val lines = text.replace("\r", "").split("\n")
        var startIdx = -1
        var endIdx = -1
        var textIdx = -1
        var inEvents = false
        val out = mutableListOf<Cue>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.equals("[Events]", ignoreCase = true)) {
                inEvents = true
                continue
            }
            if (trimmed.startsWith("[") && !trimmed.equals("[Events]", ignoreCase = true)) {
                inEvents = false
            }
            if (!inEvents) continue

            if (trimmed.startsWith("Format:", ignoreCase = true)) {
                val fields = trimmed.removePrefix("Format:").split(",").map { it.trim() }
                startIdx = fields.indexOfFirst { it.equals("Start", ignoreCase = true) }
                endIdx = fields.indexOfFirst { it.equals("End", ignoreCase = true) }
                textIdx = fields.indexOfFirst { it.equals("Text", ignoreCase = true) }
                continue
            }

            if (trimmed.startsWith("Dialogue:", ignoreCase = true)) {
                if (startIdx < 0 || endIdx < 0 || textIdx < 0) continue
                // Text is always the last field and may itself contain commas
                // (unescaped), so split only up to it and keep the remainder.
                val body = trimmed.removePrefix("Dialogue:").trim()
                val parts = body.split(",", limit = textIdx + 1)
                if (parts.size <= textIdx) continue
                val start = parseAssTime(parts[startIdx])
                val end = parseAssTime(parts[endIdx])
                val rawText = parts[textIdx]
                    .replace(Regex("\\{[^}]*\\}"), "") // strip ASS override tags like {\an8}
                    .replace("\\N", " ").replace("\\n", " ").replace("\\h", " ")
                    .trim()
                if (rawText.isNotEmpty() && end > start) out.add(Cue(start, end, rawText))
            }
        }
        return out.sortedBy { it.startMs }
    }

    // --- .lrc (synced lyrics) ---

    private val lrcTagPattern = Pattern.compile("\\[(\\d{1,2}):(\\d{2})[.:](\\d{1,3})\\]")

    private fun parseLrc(text: String): List<Cue> {
        val raw = mutableListOf<Pair<Long, String>>()
        for (line in text.replace("\r", "").split("\n")) {
            val matcher = lrcTagPattern.matcher(line)
            val tags = mutableListOf<Long>()
            while (matcher.find()) {
                val min = matcher.group(1)!!.toLong()
                val sec = matcher.group(2)!!.toLong()
                var frac = matcher.group(3)!!
                val ms = when (frac.length) {
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.toLong()
                }
                tags.add(min * 60000L + sec * 1000L + ms)
            }
            if (tags.isEmpty()) continue // metadata line like [ar:...], [ti:...], or plain text
            val lyricText = lrcTagPattern.matcher(line).replaceAll("").trim()
            if (lyricText.isEmpty()) continue
            for (t in tags) raw.add(t to lyricText)
        }
        val sorted = raw.sortedBy { it.first }
        val out = mutableListOf<Cue>()
        for (i in sorted.indices) {
            val (start, txt) = sorted[i]
            val next = sorted.getOrNull(i + 1)?.first
            val end = when {
                next != null && next > start -> next
                else -> start + 4000L
            }
            out.add(Cue(start, end, txt))
        }
        return out
    }
}
