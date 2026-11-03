package com.raizer.mr.vr

import android.webkit.WebView

/** Serviços da Activity (thread de UI) usados pelas janelas. */
interface UiBridge {
    fun runOnUi(r: Runnable)
    /** Cria um WebView com tamanho fixo em pixels, anexado à hierarquia (necessário para renderizar). */
    fun attachWebView(w: Int, h: Int): WebView
    fun detachWebView(v: WebView)
    /** Mostra um diálogo de texto; o resultado chega na thread de UI. */
    fun promptText(title: String, initial: String, onResult: (String) -> Unit)
    fun hasLocationPermission(): Boolean
    fun requestLocationPermission()
}
