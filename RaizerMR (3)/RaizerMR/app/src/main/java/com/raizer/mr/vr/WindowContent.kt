package com.raizer.mr.vr

import android.content.Context
import android.graphics.Canvas

data class WindowInfo(val id: Int, val title: String, val minimized: Boolean)

/** Serviços que o ambiente VR oferece aos conteúdos das janelas. */
interface Host {
    val context: Context
    val ui: UiBridge
    val hands: HandsState
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
    YOUTUBE("YouTube"),
    GOOGLE("Google"),
    MAPS("Google Maps"),
    BROWSER("Navegador"),
    HANDS("Mãos"),
    SETTINGS("Configurações"),
    CLOCK("Relógio"),
    CALCULATOR("Calculadora");

    /** Apps que aparecem na grade do lançador. */
    val showInLauncher: Boolean get() = this != LAUNCHER && this != RECENTS

    fun create(): WindowContent {
        val c: WindowContent = when (this) {
            LAUNCHER -> LauncherApp()
            RECENTS -> RecentsApp()
            YOUTUBE -> WebApp("YouTube", "https://www.youtube.com")
            GOOGLE -> WebApp("Google", "https://www.google.com")
            MAPS -> WebApp("Google Maps", "https://www.google.com/maps")
            BROWSER -> WebApp("Navegador", "https://duckduckgo.com")
            HANDS -> HandsApp()
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
    @Volatile var visible: Boolean = true

    /** Conteúdo vivo (WebView): região retangular abaixo de externalTop, desenhada por textura externa. */
    open val externalTexture: ExternalTexture? get() = null
    open val externalTop: Float get() = 0f

    /** Se true, recebe down/move/up em vez de apenas um toque. action: 0 down, 1 move, 2 up, 3 cancel. */
    open val wantsPointer: Boolean get() = false
    open fun onPointer(action: Int, x: Float, y: Float) {}

    open fun onAttach() {}
    open fun onDetach() {}

    abstract fun draw(c: Canvas, w: Int, h: Int)
    open fun onTap(x: Float, y: Float) {}
    /** Retorna true se o conteúdo precisa ser redesenhado. */
    open fun tick(nowMs: Long): Boolean = false

    fun invalidate() { dirty = true }
}
