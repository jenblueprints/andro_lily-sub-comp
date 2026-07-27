package com.subtitlecompanion.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Lets you point the app at a whole folder of subtitle files (one per
 * episode/title) instead of a single file. When Fanjiao switches to a
 * different track, FanjiaoListenerService reports the new title here, and
 * the best-matching file in the folder is loaded automatically.
 *
 * Matching is name-based (not content-based) since that's all a folder of
 * translated subtitle files can offer: normalize both the reported title
 * and each file's name (strip punctuation/spaces, lowercase) and take the
 * best overlap. Falls back to doing nothing (keeping whatever's already
 * showing) if nothing matches well enough, rather than guessing wrong.
 */
object SubtitleLibrary {
    private lateinit var appContext: Context
    private val listeners = mutableListOf<() -> Unit>()

    var folderUri: Uri? = null
        private set
    var entries: List<DocumentFile> = emptyList()
        private set
    var lastMatchedName: String? = null
        private set
    var lastAttemptedTitle: String? = null
        private set

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val saved = SettingsStore.load().subtitleFolderUri
        if (saved.isNotEmpty()) {
            try {
                folderUri = Uri.parse(saved)
                refreshEntries()
            } catch (e: Exception) { /* stale/revoked permission -- ignore */ }
        }
    }

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun notifyChanged() { listeners.toList().forEach { it() } }

    fun setFolder(uri: Uri) {
        folderUri = uri
        lastMatchedName = null
        SettingsStore.update { it.subtitleFolderUri = uri.toString(); it.libraryMode = "folder" }
        refreshEntries()
        notifyChanged()
    }

    fun clearFolder() {
        folderUri = null
        entries = emptyList()
        lastMatchedName = null
        SettingsStore.update { it.subtitleFolderUri = ""; it.libraryMode = "single" }
        notifyChanged()
    }

    fun isFolderMode(): Boolean = folderUri != null

    private fun refreshEntries() {
        val uri = folderUri ?: run { entries = emptyList(); return }
        val tree = DocumentFile.fromTreeUri(appContext, uri)
        entries = tree?.listFiles()?.filter { f ->
            val n = f.name?.lowercase() ?: return@filter false
            f.isFile && (n.endsWith(".srt") || n.endsWith(".ass") || n.endsWith(".ssa") || n.endsWith(".lrc"))
        } ?: emptyList()
    }

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9\\u4e00-\\u9fff]"), "")

    fun findBestMatch(title: String): DocumentFile? {
        if (entries.isEmpty() || title.isBlank()) return null
        val target = normalize(title)
        if (target.isEmpty()) return null

        entries.firstOrNull {
            normalize(it.name?.substringBeforeLast('.') ?: "") == target
        }?.let { return it }

        var best: DocumentFile? = null
        var bestScore = 0
        for (f in entries) {
            val n = normalize(f.name?.substringBeforeLast('.') ?: "")
            if (n.isEmpty()) continue
            val score = when {
                n.contains(target) -> target.length
                target.contains(n) -> n.length
                else -> 0
            }
            if (score > bestScore) { bestScore = score; best = f }
        }
        // Require both a reasonably long overlap AND that the overlap covers
        // a real majority of the reported title -- a few coincidentally
        // shared characters shouldn't be enough to swap in a wrong episode.
        // Below this bar we'd rather show nothing than guess wrong.
        val ratio = if (target.isNotEmpty()) bestScore.toFloat() / target.length else 0f
        return if (bestScore >= 3 && ratio >= 0.5f) best else null
    }

    /** Called whenever Fanjiao reports a (possibly new) track title. Returns
     *  true if a matching file was found and loaded (or was already loaded). */
    fun loadForTitle(title: String): Boolean {
        if (!isFolderMode()) return false
        lastAttemptedTitle = title
        val match = findBestMatch(title)
        if (match == null) {
            // Nothing in the folder matches this track. Don't guess by
            // leaving whatever was showing for a different episode up --
            // clear it and wait. The moment a future title change matches
            // a file we do have, loadForTitle runs again and picks it up;
            // nothing here needs to be "reset" for that to keep working.
            if (lastMatchedName != null) {
                PlaybackClock.clear()
                lastMatchedName = null
            }
            notifyChanged()
            return false
        }
        if (match.name == lastMatchedName) return true // already showing this one
        return try {
            val text = appContext.contentResolver.openInputStream(match.uri)?.use {
                it.bufferedReader(Charsets.UTF_8).readText()
            } ?: return false
            val cues = SubtitleParser.parse(match.name ?: "subtitle.srt", text)
            if (cues.isEmpty()) return false
            PlaybackClock.loadCues(cues)
            lastMatchedName = match.name
            notifyChanged()
            true
        } catch (e: Exception) {
            false
        }
    }
}
