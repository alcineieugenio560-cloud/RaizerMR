package com.raizer.mr

import android.content.Context

/** Preferências persistentes, compartilhadas entre a Home e o ambiente VR. */
object Prefs {
    val QUALITY_LABELS = arrayOf("Econômico", "Equilibrado", "Alto desempenho")

    private fun sp(c: Context) = c.getSharedPreferences("raizermr", Context.MODE_PRIVATE)

    fun stereo(c: Context): Boolean = sp(c).getBoolean("stereo", false)
    fun setStereo(c: Context, v: Boolean) = sp(c).edit().putBoolean("stereo", v).apply()

    fun quality(c: Context): Int = sp(c).getInt("quality", 1).coerceIn(0, 2)
    fun setQuality(c: Context, v: Int) = sp(c).edit().putInt("quality", v.coerceIn(0, 2)).apply()
}
