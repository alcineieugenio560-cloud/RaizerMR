package com.raizer.mr.vr

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** Lista as janelas abertas/minimizadas; tocar restaura e traz para frente. */
class RecentsApp : WindowContent("Recentes", 560, 420) {
    private val pRow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.GLASS_LIGHT }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.WHITE; textSize = 26f }
    private val pSub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.MUTED; textSize = 20f }
    private val rect = RectF()
    private var signature = ""

    private fun sig(l: List<WindowInfo>) = l.joinToString(",") { "${it.id}:${it.minimized}" }

    override fun tick(nowMs: Long): Boolean {
        val s = sig(host.windowInfos())
        if (s != signature) { signature = s; return true }
        return false
    }

    override fun draw(c: Canvas, w: Int, h: Int) {
        val list = host.windowInfos()
        if (list.isEmpty()) {
            pText.textAlign = Paint.Align.CENTER
            c.drawText("Nenhuma janela aberta", w / 2f, h / 2f, pText)
            pText.textAlign = Paint.Align.LEFT
            return
        }
        for ((i, info) in list.withIndex()) {
            val top = 12f + i * 76f
            if (top + 68f > h) break
            rect.set(16f, top, w - 16f, top + 68f)
            c.drawRoundRect(rect, 16f, 16f, pRow)
            c.drawText(info.title, 36f, top + 32f, pText)
            c.drawText(if (info.minimized) "Minimizada - toque para restaurar" else "Aberta - toque para focar", 36f, top + 57f, pSub)
        }
    }

    override fun onTap(x: Float, y: Float) {
        val idx = ((y - 12f) / 76f).toInt()
        val list = host.windowInfos()
        if (y >= 12f && idx in list.indices) host.restoreOrFocus(list[idx].id)
    }
}
