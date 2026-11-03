package com.raizer.mr.vr

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.raizer.mr.Prefs

/** Configurações dentro do VR. Tudo aqui tem efeito real. */
class SettingsApp : WindowContent("Configurações", 640, 560) {
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.WHITE; textSize = 26f }
    private val pSub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.MUTED; textSize = 19f }
    private val pChip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.GLASS_LIGHT }
    private val pChipOn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.NEON }
    private val pChipText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.WHITE; textSize = 20f; textAlign = Paint.Align.CENTER
    }
    private val pKnob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.WHITE }
    private val rect = RectF()

    private val chipW = 186f
    private val chipGap = 12f

    override fun draw(c: Canvas, w: Int, h: Int) {
        // Estéreo
        c.drawText("Modo estéreo (óculos VR)", 28f, 56f, pText)
        c.drawText("No estéreo o cursor fica no centro; toque na tela para clicar.", 28f, 90f, pSub)
        rect.set(520f, 30f, 610f, 74f)
        c.drawRoundRect(rect, 22f, 22f, if (host.stereo) pChipOn else pChip)
        c.drawCircle(if (host.stereo) 588f else 542f, 52f, 17f, pKnob)

        // Qualidade
        c.drawText("Qualidade gráfica", 28f, 164f, pText)
        for (i in 0..2) {
            val l = 28f + i * (chipW + chipGap)
            rect.set(l, 184f, l + chipW, 240f)
            c.drawRoundRect(rect, 16f, 16f, if (host.quality == i) pChipOn else pChip)
            c.drawText(Prefs.QUALITY_LABELS[i], l + chipW / 2f, 218f, pChipText)
        }
        c.drawText("O perfil define resolução de renderização, estrelas e limite de FPS.", 28f, 270f, pSub)

        // Rastreamento
        c.drawText("Rastreamento de cabeça", 28f, 330f, pText)
        c.drawText(
            if (host.headTrackingAvailable) "Giroscópio ativo." else "Sem giroscópio: arraste no espaço vazio para olhar ao redor.",
            28f, 362f, pSub
        )

        // Recentralizar
        rect.set(28f, 400f, w - 28f, 460f)
        c.drawRoundRect(rect, 16f, 16f, pChip)
        c.drawText("Recentralizar interface", w / 2f, 438f, pChipText.apply { textSize = 24f })
        pChipText.textSize = 20f
    }

    override fun onTap(x: Float, y: Float) {
        when {
            y in 20f..110f -> host.stereo = !host.stereo
            y in 184f..240f -> {
                val i = ((x - 28f) / (chipW + chipGap)).toInt()
                if (x >= 28f && i in 0..2) host.quality = i
            }
            y in 400f..460f -> host.recenter()
        }
        invalidate()
    }
}
