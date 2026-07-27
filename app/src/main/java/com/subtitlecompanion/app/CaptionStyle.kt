package com.subtitlecompanion.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView

object CaptionStyle {
    fun applyBackground(view: View, s: CaptionSettings) {
        when (s.bg) {
            "outline" -> view.background = null
            else -> {
                val gd = GradientDrawable()
                gd.cornerRadius = 28f
                val baseColor = Color.parseColor("#0B0E13")
                val alphaPct = if (s.bg == "soft") (s.opacity * 0.8).toInt() else s.opacity
                val alpha = (alphaPct * 255 / 100).coerceIn(0, 255)
                gd.setColor(
                    Color.argb(
                        alpha,
                        Color.red(baseColor),
                        Color.green(baseColor),
                        Color.blue(baseColor)
                    )
                )
                view.background = gd
            }
        }
    }

    fun applyText(tv: TextView, s: CaptionSettings, isNextLine: Boolean = false) {
        tv.textSize = if (isNextLine) (s.fontSize * 0.6f) else s.fontSize.toFloat()
        val safeColor = try {
            Color.parseColor(s.color)
        } catch (e: IllegalArgumentException) {
            Color.parseColor("#F2EDE2")
        }
        tv.setTextColor(safeColor)
        tv.alpha = if (isNextLine) 0.55f else 1f
        if (s.bg == "outline") {
            tv.setShadowLayer(5f, 0f, 0f, Color.BLACK)
        } else {
            tv.setShadowLayer(3f, 0f, 1f, Color.parseColor("#8A000000"))
        }
    }
}
