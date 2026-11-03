package com.raizer.mr.vr

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.math.BigDecimal
import java.math.RoundingMode

/** Calculadora funcional (quatro operações, %, ±, apagar). */
class CalculatorApp : WindowContent("Calculadora", 480, 640) {
    override val preferredWidthM: Float get() = 0.95f

    private val labels = arrayOf(
        "C", "±", "%", "÷",
        "7", "8", "9", "×",
        "4", "5", "6", "−",
        "1", "2", "3", "+",
        "0", ".", "←", "="
    )

    private var cur = "0"
    private var acc: Double? = null
    private var op: Char? = null
    private var fresh = true

    private val pDisp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.WHITE; textAlign = Paint.Align.RIGHT }
    private val pSub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.MUTED; textSize = 24f }
    private val pNum = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.GLASS_LIGHT }
    private val pOp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.NEON_DIM }
    private val pEq = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.NEON }
    private val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.WHITE; textSize = 36f; textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    private fun fmt(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "Erro"
        return BigDecimal(d).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    private fun apply(a: Double, o: Char, b: Double): Double = when (o) {
        '+' -> a + b
        '−' -> a - b
        '×' -> a * b
        '÷' -> if (b == 0.0) Double.NaN else a / b
        else -> b
    }

    private fun press(l: String) {
        if (cur == "Erro") { cur = "0"; acc = null; op = null; fresh = true }
        val ch = l[0]
        when {
            ch.isDigit() -> {
                if (fresh) { cur = l; fresh = false } else cur = if (cur == "0") l else cur + l
            }
            l == "." -> {
                if (fresh) { cur = "0."; fresh = false } else if (!cur.contains('.')) cur += "."
            }
            l == "C" -> { cur = "0"; acc = null; op = null; fresh = true }
            l == "±" -> cur = when {
                cur.startsWith("-") -> cur.drop(1)
                cur != "0" -> "-$cur"
                else -> cur
            }
            l == "%" -> { cur = fmt(cur.toDouble() / 100.0); fresh = true }
            l == "←" -> if (!fresh) {
                cur = cur.dropLast(1)
                if (cur.isEmpty() || cur == "-") { cur = "0"; fresh = true }
            }
            l == "=" -> {
                val a = acc
                val o = op
                if (a != null && o != null) cur = fmt(apply(a, o, cur.toDouble()))
                acc = null; op = null; fresh = true
            }
            else -> { // + − × ÷
                val a = acc
                val o = op
                if (a != null && o != null && !fresh) cur = fmt(apply(a, o, cur.toDouble()))
                if (cur == "Erro") { acc = null; op = null } else { acc = cur.toDouble(); op = ch }
                fresh = true
            }
        }
    }

    override fun draw(c: Canvas, w: Int, h: Int) {
        var size = 72f
        pDisp.textSize = size
        val tw = pDisp.measureText(cur)
        if (tw > w - 48f) { size = size * (w - 48f) / tw; pDisp.textSize = size }
        c.drawText(cur, w - 24f, 112f, pDisp)
        val a = acc
        val o = op
        if (a != null && o != null) c.drawText("${fmt(a)} $o", 24f, 40f, pSub)

        val cw = (w - 24f) / 4f
        val ch = (h - 160f) / 5f
        for ((i, l) in labels.withIndex()) {
            val col = i % 4
            val row = i / 4
            val left = 12f + col * cw + 6f
            val top = 150f + row * ch + 6f
            rect.set(left, top, left + cw - 12f, top + ch - 12f)
            val p = when {
                l == "=" -> pEq
                l in arrayOf("÷", "×", "−", "+") -> pOp
                else -> pNum
            }
            c.drawRoundRect(rect, 18f, 18f, p)
            c.drawText(l, rect.centerX(), rect.centerY() + 12f, pLabel)
        }
    }

    override fun onTap(x: Float, y: Float) {
        val cw = (designW - 24f) / 4f
        val ch = (designH - 160f) / 5f
        if (x < 12f || y < 150f) return
        val col = ((x - 12f) / cw).toInt()
        val row = ((y - 150f) / ch).toInt()
        if (col in 0..3 && row in 0..4) press(labels[row * 4 + col])
        invalidate()
    }
}
