package com.raizer.mr.vr

import android.graphics.Canvas
import android.graphics.Paint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class ClockApp : WindowContent("Relógio", 520, 460) {
    override val preferredWidthM: Float get() = 1.0f

    private var lastSec = -1L
    private val pRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = Theme.NEON
    }
    private val pTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = Theme.MUTED; strokeCap = Paint.Cap.ROUND
    }
    private val pHand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 7f; color = Theme.WHITE; strokeCap = Paint.Cap.ROUND
    }
    private val pSec = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = Theme.NEON; strokeCap = Paint.Cap.ROUND
    }
    private val pDigital = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.WHITE; textSize = 46f; textAlign = Paint.Align.CENTER
    }
    private val pDate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.MUTED; textSize = 22f; textAlign = Paint.Align.CENTER
    }
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("EEEE, dd 'de' MMMM", Locale.getDefault())

    override fun tick(nowMs: Long): Boolean {
        val s = nowMs / 1000L
        if (s != lastSec) { lastSec = s; return true }
        return false
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, p: Paint) {
        val a = Math.toRadians((deg - 90f).toDouble())
        c.drawLine(cx, cy, cx + cos(a).toFloat() * len, cy + sin(a).toFloat() * len, p)
    }

    override fun draw(c: Canvas, w: Int, h: Int) {
        val cx = w / 2f
        val cy = 190f
        val r = 150f
        c.drawCircle(cx, cy, r, pRing)
        for (i in 0 until 12) {
            val a = Math.toRadians((i * 30 - 90).toDouble())
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            c.drawLine(cx + ca * (r - 18f), cy + sa * (r - 18f), cx + ca * (r - 6f), cy + sa * (r - 6f), pTick)
        }
        val cal = Calendar.getInstance()
        val sec = cal.get(Calendar.SECOND)
        val min = cal.get(Calendar.MINUTE) + sec / 60f
        val hr = (cal.get(Calendar.HOUR) % 12) + min / 60f
        hand(c, cx, cy, hr * 30f, r * 0.5f, pHand)
        hand(c, cx, cy, min * 6f, r * 0.75f, pHand)
        hand(c, cx, cy, sec * 6f, r * 0.85f, pSec)

        val now = Date()
        c.drawText(timeFmt.format(now), cx, 395f, pDigital)
        c.drawText(dateFmt.format(now), cx, 435f, pDate)
    }
}
