package com.subtitlecompanion.app

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Watches Android's active media sessions for the one belonging to Fanjiao
 * (or whatever app's package name is saved in Settings), and feeds its
 * play/pause state -- and position, if Fanjiao actually reports one -- into
 * PlaybackClock. Requires the user to grant Notification Access, since that's
 * the permission that also unlocks reading other apps' media sessions.
 */
class FanjiaoListenerService : NotificationListenerService() {

    private var controller: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            state ?: return
            val isPlaying = state.state == PlaybackState.STATE_PLAYING
            val position = state.position
            PlaybackClock.onExternalUpdate(isPlaying, if (position >= 0) position else null)
        }

        override fun onSessionDestroyed() {
            controller = null
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        SettingsStore.init(applicationContext)
        attach()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val target = SettingsStore.load().fanjiaoPackage
        if (target.isNotBlank() && sbn.packageName == target) attach()
    }

    private fun attach() {
        val target = SettingsStore.load().fanjiaoPackage
        if (target.isBlank()) return
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(this, FanjiaoListenerService::class.java)
            val sessions = msm.getActiveSessions(component)
            val match = sessions.firstOrNull { it.packageName == target }
            if (match != null && match.sessionToken != controller?.sessionToken) {
                controller?.unregisterCallback(controllerCallback)
                val newController = MediaController(this, match.sessionToken)
                newController.registerCallback(controllerCallback)
                newController.playbackState?.let { st -> controllerCallback.onPlaybackStateChanged(st) }
                controller = newController
            }
        } catch (e: SecurityException) {
            Log.w("FanjiaoListener", "Notification access not granted yet", e)
        }
    }

    companion object {
        /** Used by the "Detect now" button in MainActivity to find Fanjiao's
         *  real package name -- run this while Fanjiao is actually playing. */
        fun listActiveSessionPackages(ctx: Context): List<String> {
            return try {
                val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val component = ComponentName(ctx, FanjiaoListenerService::class.java)
                msm.getActiveSessions(component).map { it.packageName }.distinct()
            } catch (e: SecurityException) {
                emptyList()
            }
        }
    }
}
