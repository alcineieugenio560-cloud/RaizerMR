package com.raizer.mr.vr

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/** Ícones próprios desenhados com Canvas (sem imagens externas). */
object Icons {
    enum class Kind { HOME, APPS, SETTINGS, RECENTS, CLOCK, CALC, WIFI, WIFI_OFF, PLAY, SEARCH, PIN, GLOBE, HAND, BACK, FORWARD, RELOAD }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    fun draw(c: Canvas, k: Kind, cx: Float, cy: Float, s: Float, color: Int) {
        stroke.color = color
        fill.color = color
        stroke.strokeWidth = s * 0.08f
        when (k) {
            Kind.HOME -> {
                path.reset()
                path.moveTo(cx - s * 0.45f, cy - s * 0.02f)
                path.lineTo(cx, cy - s * 0.45f)
                path.lineTo(cx + s * 0.45f, cy - s * 0.02f)
                c.drawPath(path, stroke)
                c.drawRect(cx - s * 0.32f, cy - s * 0.05f, cx + s * 0.32f, cy + s * 0.42f, stroke)
            }
            Kind.APPS -> {
                for (i in -1..1) for (j in -1..1) {
                    c.drawCircle(cx + i * s * 0.3f, cy + j * s * 0.3f, s * 0.07f, fill)
                }
            }
            Kind.SETTINGS -> {
                c.drawCircle(cx, cy, s * 0.17f, stroke)
                c.drawCircle(cx, cy, s * 0.32f, stroke)
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4.0
                    val ca = cos(a).toFloat()
                    val sa = sin(a).toFloat()
                    c.drawLine(cx + ca * s * 0.32f, cy + sa * s * 0.32f, cx + ca * s * 0.46f, cy + sa * s * 0.46f, stroke)
                }
            }
            Kind.RECENTS -> {
                rect.set(cx - s * 0.46f, cy - s * 0.18f, cx + s * 0.1f, cy + s * 0.38f)
                c.drawRoundRect(rect, s * 0.08f, s * 0.08f, stroke)
                rect.set(cx - s * 0.1f, cy - s * 0.38f, cx + s * 0.46f, cy + s * 0.18f)
                c.drawRoundRect(rect, s * 0.08f, s * 0.08f, stroke)
            }
            Kind.CLOCK -> {
                c.drawCircle(cx, cy, s * 0.42f, stroke)
                c.drawLine(cx, cy, cx, cy - s * 0.26f, stroke)
                c.drawLine(cx, cy, cx + s * 0.18f, cy + s * 0.1f, stroke)
            }
            Kind.CALC -> {
                rect.set(cx - s * 0.34f, cy - s * 0.44f, cx + s * 0.34f, cy + s * 0.44f)
                c.drawRoundRect(rect, s * 0.08f, s * 0.08f, stroke)
                c.drawRect(cx - s * 0.22f, cy - s * 0.32f, cx + s * 0.22f, cy - s * 0.16f, stroke)
                for (r in 0..1) for (col in -1..1) {
                    c.drawCircle(cx + col * s * 0.17f, cy + s * 0.02f + r * s * 0.2f, s * 0.04f, fill)
                }
            }
            Kind.PLAY -> {
                rect.set(cx - s * 0.46f, cy - s * 0.32f, cx + s * 0.46f, cy + s * 0.32f)
                c.drawRoundRect(rect, s * 0.14f, s * 0.14f, stroke)
                path.reset()
                path.moveTo(cx - s * 0.1f, cy - s * 0.15f)
                path.lineTo(cx - s * 0.1f, cy + s * 0.15f)
                path.lineTo(cx + s * 0.17f, cy)
                path.close()
                c.drawPath(path, fill)
            }
            Kind.SEARCH -> {
                c.drawCircle(cx - s * 0.07f, cy - s * 0.07f, s * 0.28f, stroke)
                c.drawLine(cx + s * 0.14f, cy + s * 0.14f, cx + s * 0.42f, cy + s * 0.42f, stroke)
            }
            Kind.PIN -> {
                c.drawCircle(cx, cy - s * 0.14f, s * 0.24f, stroke)
                path.reset()
                path.moveTo(cx - s * 0.2f, cy)
                path.lineTo(cx, cy + s * 0.44f)
                path.lineTo(cx + s * 0.2f, cy)
                c.drawPath(path, stroke)
                c.drawCircle(cx, cy - s * 0.14f, s * 0.07f, fill)
            }
            Kind.GLOBE -> {
                c.drawCircle(cx, cy, s * 0.42f, stroke)
                rect.set(cx - s * 0.18f, cy - s * 0.42f, cx + s * 0.18f, cy + s * 0.42f)
                c.drawOval(rect, stroke)
                c.drawLine(cx - s * 0.42f, cy, cx + s * 0.42f, cy, stroke)
            }
            Kind.HAND -> {
                c.drawCircle(cx, cy + s * 0.2f, s * 0.22f, stroke)
                for (i in -2..2) {
                    val topY = cy - s * 0.42f + Math.abs(i) * s * 0.07f
                    c.drawLine(cx + i * s * 0.1f, cy + s * 0.02f, cx + i * s * 0.16f, topY, stroke)
                    c.drawCircle(cx + i * s * 0.16f, topY, s * 0.035f, fill)
                }
            }
            Kind.BACK -> {
                path.reset()
                path.moveTo(cx + s * 0.14f, cy - s * 0.3f)
                path.lineTo(cx - s * 0.16f, cy)
                path.lineTo(cx + s * 0.14f, cy + s * 0.3f)
                c.drawPath(path, stroke)
            }
            Kind.FORWARD -> {
                path.reset()
                path.moveTo(cx - s * 0.14f, cy - s * 0.3f)
                path.lineTo(cx + s * 0.16f, cy)
                path.lineTo(cx - s * 0.14f, cy + s * 0.3f)
                c.drawPath(path, stroke)
            }
            Kind.RELOAD -> {
                rect.set(cx - s * 0.3f, cy - s * 0.3f, cx + s * 0.3f, cy + s * 0.3f)
                c.drawArc(rect, -50f, 280f, false, stroke)
                val px = cx + s * 0.193f
                val py = cy - s * 0.23f
                path.reset()
                path.moveTo(px, py - s * 0.14f)
                path.lineTo(px + s * 0.16f, py + s * 0.02f)
                path.lineTo(px - s * 0.08f, py + s * 0.08f)
                path.close()
                c.drawPath(path, fill)
            }
            Kind.WIFI, Kind.WIFI_OFF -> {
                val oy = cy + s * 0.3f
                for (i in 1..3) {
                    val r = s * 0.2f * i
                    rect.set(cx - r, oy - r, cx + r, oy + r)
                    c.drawArc(rect, 225f, 90f, false, stroke)
                }
                c.drawCircle(cx, oy, s * 0.05f, fill)
                if (k == Kind.WIFI_OFF) c.drawLine(cx - s * 0.4f, cy - s * 0.3f, cx + s * 0.4f, cy + s * 0.4f, stroke)
            }
        }
    }
}
