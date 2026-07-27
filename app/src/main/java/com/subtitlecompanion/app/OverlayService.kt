package com.subtitlecompanion.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Draws the actual floating caption using a real system overlay window
 * (TYPE_APPLICATION_OVERLAY) -- the same permission category apps like
 * Messenger use for chat heads. Runs as a foreground service so Android
 * doesn't kill it while you're in another app.
 */
class OverlayService : Service(), ClockListener {

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var currentTv: TextView? = null
    private var nextTv: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(applicationContext)
        startForegroundNotification()
        buildOverlay()
        PlaybackClock.addListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        PlaybackClock.removeListener(this)
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) { /* already gone */ }
        }
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val channelId = "subtitle_overlay_channel"
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(channelId, "Floating subtitles", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)

        val stopIntent = Intent(this, OverlayService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Floating subtitles running")
            .setContentText("Tap Stop to end")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .addAction(0, "Stop", stopPending)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun buildOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val settings = SettingsStore.load()

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(28, 18, 28, 18)

        val current = TextView(this)
        current.gravity = Gravity.CENTER
        val next = TextView(this)
        next.gravity = Gravity.CENTER

        CaptionStyle.applyBackground(container, settings)
        CaptionStyle.applyText(current, settings, isNextLine = false)
        CaptionStyle.applyText(next, settings, isNextLine = true)
        next.visibility = if (settings.showNext) View.VISIBLE else View.GONE

        container.addView(current)
        container.addView(next)

        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = when (settings.position) {
            "top" -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            "bottom" -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            else -> Gravity.CENTER
        }
        params.y = 60

        makeDraggable(container, params)

        windowManager?.addView(container, params)
        overlayView = container
        currentTv = current
        nextTv = next
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY - (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    override fun onTick(elapsedMs: Long, totalMs: Long, activeIndex: Int) {
        val cues = PlaybackClock.cues
        if (activeIndex >= 0 && activeIndex < cues.size) {
            currentTv?.text = cues[activeIndex].text
            val nxt = cues.getOrNull(activeIndex + 1)
            nextTv?.text = nxt?.text ?: ""
        } else {
            currentTv?.text = ""
            val nxt = cues.firstOrNull { it.startMs > elapsedMs }
            nextTv?.text = nxt?.text ?: ""
        }
    }

    companion object {
        const val ACTION_STOP = "com.subtitlecompanion.app.ACTION_STOP_OVERLAY"
    }
}
