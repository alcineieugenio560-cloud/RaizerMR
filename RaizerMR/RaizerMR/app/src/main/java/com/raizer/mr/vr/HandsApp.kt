package com.raizer.mr.vr

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.raizer.mr.Prefs

/** Painel de rastreamento de mãos: liga/desliga, câmera, sensibilidade, prévia do esqueleto e calibração. */
class HandsApp : WindowContent("Mãos", 640, 660) {
    private val gains = floatArrayOf(0.8f, 1.0f, 1.3f)
    private val sensLabels = arrayOf("Baixa", "Média", "Alta")

    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.WHITE; textSize = 24f }
    private val pSub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.MUTED; textSize = 18f }
    private val pChip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.GLASS_LIGHT }
    private val pChipOn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.NEON }
    private val pChipText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.WHITE; textSize = 20f; textAlign = Paint.Align.CENTER
    }
    private val pKnob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.WHITE }
    private val pView = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Integer.parseInt("55000000", 16) }
    private val pBone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND
    }
    private val pJoint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pProg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.NEON }
    private val rect = RectF()
    private var lastTick = 0L
    private var lastStatus = ""

    override fun tick(nowMs: Long): Boolean {
        val st = host.hands
        if (st.status != lastStatus) { lastStatus = st.status; return true }
        if ((st.enabled || st.calibStep != 0) && nowMs - lastTick > 90L) {
            lastTick = nowMs
            return true
        }
        return false
    }

    private fun chip(c: Canvas, l: Float, t: Float, r: Float, b: Float, on: Boolean, label: String) {
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, 16f, 16f, if (on) pChipOn else pChip)
        c.drawText(label, (l + r) / 2f, (t + b) / 2f + 7f, pChipText)
    }

    override fun draw(c: Canvas, w: Int, h: Int) {
        val st = host.hands

        c.drawText("Rastreamento de mãos", 28f, 52f, pText)
        c.drawText(Txt.fit(pSub, st.status, 470f), 28f, 84f, pSub)
        rect.set(520f, 28f, 610f, 72f)
        c.drawRoundRect(rect, 22f, 22f, if (st.enabled) pChipOn else pChip)
        c.drawCircle(if (st.enabled) 588f else 542f, 50f, 17f, pKnob)

        c.drawText("Câmera", 28f, 128f, pSub)
        chip(c, 28f, 136f, 314f, 186f, !st.frontCamera, "Traseira (óculos)")
        chip(c, 326f, 136f, 612f, 186f, st.frontCamera, "Frontal")

        c.drawText("Sensibilidade do cursor", 28f, 214f, pSub)
        for (i in 0..2) {
            val l = 28f + i * 198f
            chip(c, l, 224f, l + 186f, 274f, st.gain == gains[i], sensLabels[i])
        }

        // prévia do esqueleto
        rect.set(28f, 292f, 612f, 492f)
        c.drawRoundRect(rect, 16f, 16f, pView)
        val f = st.frame
        if (st.enabled && f != null && f.hands.isNotEmpty()) {
            val ph = 200f
            val pw = ph * f.aspect
            val ox = 320f - pw / 2f
            val oy = 292f
            for (hand in f.hands) {
                val col = if (hand.label == "Esquerda") Theme.NEON else Theme.WHITE
                pBone.color = col
                pJoint.color = col
                for (cn in HandMath.CONNECTIONS) {
                    c.drawLine(
                        ox + hand.lm[cn[0] * 3] * pw, oy + hand.lm[cn[0] * 3 + 1] * ph,
                        ox + hand.lm[cn[1] * 3] * pw, oy + hand.lm[cn[1] * 3 + 1] * ph, pBone
                    )
                }
                for (i in 0 until 21) {
                    c.drawCircle(ox + hand.lm[i * 3] * pw, oy + hand.lm[i * 3 + 1] * ph, 4f, pJoint)
                }
            }
        } else {
            pChipText.textSize = 22f
            c.drawText(if (st.enabled) "Mostre a mão para a câmera" else "Ative o rastreamento", 320f, 400f, pChipText)
            pChipText.textSize = 20f
        }

        // gestos
        val g = st.gestureText
        for ((i, line) in g.withIndex()) {
            if (i > 1) break
            c.drawText(line, 28f, 520f + i * 28f, pText.apply { textSize = 22f })
        }
        pText.textSize = 24f

        // calibração
        val msg = st.calibMessage
        if (msg.isNotEmpty()) c.drawText(Txt.fit(pSub, msg, 584f), 28f, 582f, pSub)
        if (st.calibStep != 0) {
            rect.set(28f, 590f, 28f + 584f * st.calibProgress, 596f)
            c.drawRect(rect, pProg)
        }

        chip(c, 28f, 604f, 318f, 652f, st.calibStep != 0, "Calibrar pinça")
        chip(c, 330f, 604f, 612f, 652f, st.openRecenter, "Mão aberta recentraliza")
    }

    override fun onTap(x: Float, y: Float) {
        val st = host.hands
        when {
            y in 20f..100f -> st.setEnabled(!st.enabled)
            y in 136f..186f -> {
                val front = x > 320f
                Prefs.setHandFront(host.context, front)
                st.applyCamera(front)
            }
            y in 224f..274f -> {
                val i = ((x - 28f) / 198f).toInt()
                if (x >= 28f && i in 0..2) {
                    st.gain = gains[i]
                    Prefs.setHandSens(host.context, i)
                }
            }
            y in 604f..652f -> {
                if (x < 324f) {
                    if (!st.enabled) st.calibMessage = "Ative o rastreamento para calibrar"
                    else st.startCalibration()
                } else {
                    st.openRecenter = !st.openRecenter
                    Prefs.setHandRecenter(host.context, st.openRecenter)
                }
            }
        }
        invalidate()
    }
}
