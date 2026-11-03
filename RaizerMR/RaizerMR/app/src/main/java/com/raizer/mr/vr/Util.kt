package com.raizer.mr.vr

import android.graphics.Paint

object Txt {
    /** Corta o texto com reticências para caber em maxW pixels. */
    fun fit(p: Paint, s: String, maxW: Float): String {
        if (p.measureText(s) <= maxW) return s
        var t = s
        while (t.length > 4 && p.measureText("$t...") > maxW) t = t.dropLast(2)
        return "$t..."
    }
}
