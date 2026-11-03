package com.raizer.mr.vr

import android.content.Context
import android.graphics.Canvas

data class WindowInfo(val id: Int, val title: String, val minimized: Boolean)

/** Serviços que o ambiente VR oferece aos conteúdos das janelas. */
interface Host {
    val context: Context
    val headTrackingAvailable: Boolean
    var stereo: Boolean
    var quality: Int
    fun openApp(app: AppId)
    fun recenter()
    fun windowInfos(): List<WindowInfo>
    fun restoreOrFocus(id: Int)
}

enum class AppId(val label: String) {
    LAUNCHER("Aplicativos"),
    RECENTS("Recentes"),
    SETTINGS("Configurações"),
    CLOCK("Relógio"),
    CALCULATOR("Calculadora");

    /** Apps que aparecem na grade do lançador. */
    val showInLauncher: Boolean get() = this == SETTINGS || this == CLOCK || this == CALCULATOR

    fun create(): WindowContent {
        val c: WindowContent = when (this) {
            LAUNCHER -> LauncherApp()
            RECENTS -> RecentsApp()
            SETTINGS -> SettingsApp()
            CLOCK -> ClockApp()
            CALCULATOR -> CalculatorApp()
        }
        c.appId = this
        return c
    }
}

/**
 * Conteúdo de uma janela 3D. Desenha em coordenadas lógicas (designW x designH)
 * num Canvas; a janela cuida da moldura (barra de título) e da textura.
 */
abstract class WindowContent(val title: String, val designW: Int, val designH: Int) {
    open val chrome: Boolean = true
    open val preferredWidthM: Float get() = 1.3f

    lateinit var host: Host
    var appId: AppId? = null
    var dirty: Boolean = true

    abstract fun draw(c: Canvas, w: Int, h: Int)
    open fun onTap(x: Float, y: Float) {}
    /** Retorna true se o conteúdo precisa ser redesenhado. */
    open fun tick(nowMs: Long): Boolean = false

    fun invalidate() { dirty = true }
}
