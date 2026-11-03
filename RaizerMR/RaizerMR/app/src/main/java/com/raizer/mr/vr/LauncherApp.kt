package com.raizer.mr.vr

import android.graphics.Canvas
import android.graphics.Paint

class LauncherApp : WindowContent("Aplicativos", 660, 560) {
    private val apps = AppId.values().filter { it.showInLauncher }
    private val cols = 3
    private val cellH = 180f
    private val pBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.GLASS_LIGHT }
    private val pRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Theme.NEON_DIM
    }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.WHITE; textSize = 22f; textAlign = Paint.Align.CENTER
    }

    private fun kind(a: AppId): Icons.Kind = when (a) {
        AppId.YOUTUBE -> Icons.Kind.PLAY
        AppId.GOOGLE -> Icons.Kind.SEARCH
        AppId.MAPS -> Icons.Kind.PIN
        AppId.BROWSER -> Icons.Kind.GLOBE
        AppId.HANDS -> Icons.Kind.HAND
        AppId.CLOCK -> Icons.Kind.CLOCK
        AppId.CALCULATOR -> Icons.Kind.CALC
        AppId.RECENTS -> Icons.Kind.RECENTS
        AppId.LAUNCHER -> Icons.Kind.APPS
        AppId.SETTINGS -> Icons.Kind.SETTINGS
    }

    override fun draw(c: Canvas, w: Int, h: Int) {
        val cellW = w / cols.toFloat()
        for ((i, a) in apps.withIndex()) {
            val cx = cellW * (i % cols) + cellW / 2f
            val cy = (i / cols) * cellH + 80f
            c.drawCircle(cx, cy, 52f, pBg)
            c.drawCircle(cx, cy, 52f, pRing)
            Icons.draw(c, kind(a), cx, cy, 58f, Theme.WHITE)
            c.drawText(a.label, cx, cy + 86f, pText)
        }
    }

    override fun onTap(x: Float, y: Float) {
        val cellW = designW / cols.toFloat()
        val col = (x / cellW).toInt()
        val row = (y / cellH).toInt()
        val idx = row * cols + col
        if (col in 0 until cols && row >= 0 && idx in apps.indices) host.openApp(apps[idx])
    }
}
