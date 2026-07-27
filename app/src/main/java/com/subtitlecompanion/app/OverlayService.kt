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
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Draws the actual floating caption using a real system overlay window
 * (TYPE_APPLICATION_OVERLAY) -- the same permission category apps like
 * Messenger use for chat heads. Runs as a foreground service so Android
 * doesn't kill it while you're in another app.
 *
 * The window is always anchored with Gravity.TOP or Gravity.START. Some
 * gravity combinations (CENTER, BOTTOM) have offset directions that are
 * inverted from plain intuition on WindowManager overlay windows -- that
 * inconsistency is exactly what caused dragging to feel backwards before.
 * Anchoring top-left keeps x/y math simple and consistent everywhere:
 * increasing x always moves right, increasing y always moves down.
 */
class OverlayService : Service(), ClockListener {

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var currentTv: TextView? = null
    private var nextTv: TextView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var currentSettings: CaptionSettings = CaptionSettings()

    // The panel's width is fixed at creation time (a share of screen width)
    // instead of WRAP_CONTENT. That's the actual fix for "text centers in a
    // different place depending on the line" -- with WRAP_CONTENT, the panel
    // itself grew/shrank to fit each line, so its visual center drifted left
    // or right as line length changed even though x never moved. A fixed
    // width means the panel's box never changes size, so gravity=CENTER
    // inside it lands in the same place for every line.
    private var fixedWidthPx: Int = 0

    // Vertical position is tracked as a center point (not a top edge), and
    // re-applied any time the panel's height changes (longer lines wrapping
    // to more rows, next-line preview toggling, etc.) so the panel grows/
    // shrinks evenly around a stable center instead of drifting downward.
    // Updated whenever the user manually drags, so a manual placement is
    // respected as the new center going forward rather than snapping back.
    private var anchorCenterY: Int = 0

    private val styleListener = { refreshStyle() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(applicationContext)
        isRunning = true
        startForegroundNotification()
        buildOverlay()
        PlaybackClock.addListener(this)
        OverlayStyleBus.addListener(styleListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        PlaybackClock.removeListener(this)
        OverlayStyleBus.removeListener(styleListener)
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
            .setContentTitle("Sub on Top - floating subtitles running")
            .setContentText("Tap Stop to end, or use the Stop button in the app")
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
        currentSettings = settings

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(28, 18, 28, 18)

        val current = TextView(this)
        current.gravity = Gravity.CENTER
        val next = TextView(this)
        next.gravity = Gravity.CENTER

        CaptionStyle.applyBackground(container, settings)
        CaptionStyle.applyText(current, settings, isNextLine = false, scalePct = settings.overlayScalePct)
        CaptionStyle.applyText(next, settings, isNextLine = true, scalePct = settings.overlayScalePct)
        next.visibility = if (settings.showNext) View.VISIBLE else View.GONE

        container.addView(current)
        container.addView(next)

        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        fixedWidthPx = (screenW * 0.86).toInt()

        val params = WindowManager.LayoutParams(
            fixedWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = ((screenW - fixedWidthPx) / 2).coerceAtLeast(0)
        anchorCenterY = centerYForPosition(settings.position, screenH)
        params.y = (anchorCenterY - 60) // rough placement pre-measure; corrected below once height is known

        makeDraggableAndPinchable(container, params)

        windowManager?.addView(container, params)
        overlayView = container
        overlayParams = params
        currentTv = current
        nextTv = next

        // Re-applies the vertical center every time this view's height
        // changes (new line wraps to a different number of rows, next-line
        // preview toggled, font size pinched, etc.) so the panel grows and
        // shrinks evenly around anchorCenterY instead of drifting. Kept
        // registered permanently rather than removed after first use.
        container.viewTreeObserver.addOnGlobalLayoutListener {
            val h = container.height
            if (h > 0) {
                val targetY = anchorCenterY - h / 2
                if (targetY != params.y) {
                    params.y = targetY
                    try { windowManager?.updateViewLayout(container, params) } catch (e: Exception) { }
                }
            }
        }
    }

    private fun centerYForPosition(position: String, screenH: Int): Int = when (position) {
        "top" -> (screenH * 0.16).toInt()
        "bottom" -> (screenH * 0.84).toInt()
        else -> (screenH * 0.5).toInt()
    }

    /** Re-reads settings and re-applies them to the already-built overlay
     *  views in place, so style-sheet changes show up immediately instead
     *  of only on the next "Start floating subtitles" tap. */
    private fun refreshStyle() {
        val view = overlayView ?: return
        val params = overlayParams ?: return
        val cur = currentTv ?: return
        val nxt = nextTv ?: return

        val oldPosition = currentSettings.position
        val settings = SettingsStore.load()
        currentSettings = settings

        CaptionStyle.applyBackground(view, settings)
        CaptionStyle.applyText(cur, settings, isNextLine = false, scalePct = settings.overlayScalePct)
        CaptionStyle.applyText(nxt, settings, isNextLine = true, scalePct = settings.overlayScalePct)
        nxt.visibility = if (settings.showNext) View.VISIBLE else View.GONE

        if (settings.position != oldPosition) {
            anchorCenterY = centerYForPosition(settings.position, resources.displayMetrics.heightPixels)
            params.y = anchorCenterY - view.height / 2
        }
        try { windowManager?.updateViewLayout(view, params) } catch (e: Exception) { }
    }

    private fun makeDraggableAndPinchable(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newScale = (currentSettings.overlayScalePct * detector.scaleFactor).coerceIn(50f, 250f)
                currentSettings.overlayScalePct = newScale
                currentTv?.let { CaptionStyle.applyText(it, currentSettings, isNextLine = false, scalePct = newScale) }
                nextTv?.let { CaptionStyle.applyText(it, currentSettings, isNextLine = true, scalePct = newScale) }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                SettingsStore.update { it.overlayScalePct = currentSettings.overlayScalePct }
            }
        })

        view.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (event.pointerCount >= 2) {
                // Two fingers down: this is a pinch, not a drag -- ignore
                // single-pointer drag math for this event.
                return@setOnTouchListener true
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Top/start-anchored window: this is the natural,
                    // non-inverted mapping (right/down deltas move the
                    // window right/down).
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    // Keep the recenter target following the drag, using
                    // this view's current height -- so once you let go, a
                    // later line-length/height change grows evenly around
                    // where you actually put it, instead of snapping back
                    // toward the original top/middle/bottom position.
                    anchorCenterY = params.y + view.height / 2
                    try { windowManager?.updateViewLayout(view, params) } catch (e: Exception) { }
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
        @Volatile var isRunning: Boolean = false
            private set
    }
}
