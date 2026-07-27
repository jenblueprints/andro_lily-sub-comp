package com.subtitlecompanion.app

/**
 * Lets the settings sheet notify a currently-running OverlayService that
 * style settings changed, so the floating window updates immediately
 * instead of only picking up new settings the next time it's started.
 */
object OverlayStyleBus {
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    fun notifyChanged() {
        listeners.toList().forEach { it() }
    }
}
