package com.raizer.mr.vr

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Janela com navegador real (WebView). O WebView fica na hierarquia de views, é desenhado numa
 * Surface (Canvas de hardware, com vídeo) e vira uma textura no espaço 3D.
 * Barra superior própria: voltar, avançar, recarregar, endereço/pesquisa e início.
 */
class WebApp(title: String, private val home: String) : WindowContent(title, W, BAR.toInt() + H) {
    companion object {
        const val W = 1024
        const val H = 576
        const val BAR = 64f
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    override val preferredWidthM: Float get() = 1.9f
    override val wantsPointer: Boolean get() = true

    private val ext = ExternalTexture(W, H)
    override val externalTexture: ExternalTexture? get() = ext
    override val externalTop: Float get() = BAR

    @Volatile private var webView: WebView? = null
    @Volatile private var alive = false
    @Volatile private var canBack = false
    @Volatile private var canFwd = false
    @Volatile private var progress = 100
    @Volatile private var curUrl = home
    private var lastReady = false
    private var downInBar = false
    private var downTime = 0L
    private val ui = Handler(Looper.getMainLooper())

    private val pBar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#22FFFFFF") }
    private val pPill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#33000000") }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.WHITE; textSize = 22f }
    private val pProg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.NEON }
    private val pHint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.MUTED; textSize = 26f; textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    private val drawLoop = object : Runnable {
        override fun run() {
            if (!alive) return
            drawFrame()
            ui.postDelayed(this, frameMs())
        }
    }

    private fun frameMs(): Long = when (host.quality) {
        0 -> 50L
        1 -> 33L
        else -> 16L
    }

    override fun onAttach() {
        alive = true
        host.ui.runOnUi(Runnable { createWebView() })
        if (title == "Google Maps" && !host.ui.hasLocationPermission()) {
            host.ui.runOnUi(Runnable { host.ui.requestLocationPermission() })
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        if (!alive) return
        val wv = host.ui.attachWebView(W, H)
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.setSupportZoom(false)
        s.userAgentString = DESKTOP_UA
        s.setGeolocationEnabled(true)
        wv.setBackgroundColor(Color.parseColor("#101218"))
        wv.webViewClient = object : WebViewClient() {
            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                if (view != null) {
                    canBack = view.canGoBack()
                    canFwd = view.canGoForward()
                }
                if (url != null) curUrl = url
                invalidate()
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress = newProgress
                invalidate()
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?, callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, host.ui.hasLocationPermission(), false)
            }
        }
        wv.loadUrl(home)
        webView = wv
        ui.post(drawLoop)
    }

    /** Thread de UI: copia o WebView para a Surface da textura externa. */
    private fun drawFrame() {
        if (!visible) return
        val wv = webView ?: return
        val s = ext.surface() ?: return
        if (!s.isValid) return
        try {
            val c = s.lockHardwareCanvas()
            try {
                wv.draw(c)
            } finally {
                s.unlockCanvasAndPost(c)
            }
        } catch (e: Exception) {
        }
    }

    override fun onDetach() {
        alive = false
        ui.removeCallbacks(drawLoop)
        host.ui.runOnUi(Runnable {
            webView?.let { host.ui.detachWebView(it) }
            webView = null
        })
        ext.release()
    }

    override fun tick(nowMs: Long): Boolean {
        val r = ext.hasFrame
        if (r != lastReady) { lastReady = r; return true }
        return false
    }

    override fun draw(c: Canvas, w: Int, h: Int) {
        rect.set(0f, 0f, w.toFloat(), BAR)
        c.drawRect(rect, pBar)
        Icons.draw(c, Icons.Kind.BACK, 40f, 32f, 30f, if (canBack) Theme.WHITE else Theme.MUTED)
        Icons.draw(c, Icons.Kind.FORWARD, 104f, 32f, 30f, if (canFwd) Theme.WHITE else Theme.MUTED)
        Icons.draw(c, Icons.Kind.RELOAD, 168f, 32f, 30f, Theme.WHITE)
        rect.set(216f, 11f, w - 84f, 53f)
        c.drawRoundRect(rect, 21f, 21f, pPill)
        c.drawText(Txt.fit(pText, curUrl, w - 84f - 216f - 36f), 236f, 39f, pText)
        Icons.draw(c, Icons.Kind.HOME, w - 44f, 32f, 30f, Theme.WHITE)
        val p = progress
        if (p in 1..99) c.drawRect(0f, BAR - 4f, w * p / 100f, BAR, pProg)
        if (!ext.hasFrame) c.drawText("Carregando...", w / 2f, BAR + H / 2f, pHint)
    }

    // ---------- entrada ----------

    override fun onPointer(action: Int, x: Float, y: Float) {
        when (action) {
            0 -> {
                downInBar = y < BAR
                if (!downInBar) sendTouch(MotionEvent.ACTION_DOWN, x, y)
            }
            1 -> if (!downInBar) sendTouch(MotionEvent.ACTION_MOVE, x, y)
            2 -> if (downInBar) {
                if (y < BAR) toolbarTap(x)
            } else {
                sendTouch(MotionEvent.ACTION_UP, x, y)
            }
            else -> if (!downInBar) sendTouch(MotionEvent.ACTION_CANCEL, x, y)
        }
    }

    private fun sendTouch(action: Int, x: Float, y: Float) {
        val ax = x.coerceIn(0f, W - 1f)
        val ay = (y - BAR).coerceIn(0f, H - 1f)
        host.ui.runOnUi(Runnable {
            val wv = webView ?: return@Runnable
            val now = SystemClock.uptimeMillis()
            if (action == MotionEvent.ACTION_DOWN) {
                downTime = now
                wv.requestFocus()
            }
            val ev = MotionEvent.obtain(downTime, now, action, ax, ay, 0)
            wv.dispatchTouchEvent(ev)
            ev.recycle()
        })
    }

    private fun withWeb(block: (WebView) -> Unit) {
        host.ui.runOnUi(Runnable { webView?.let(block) })
    }

    private fun toolbarTap(x: Float) {
        when {
            x < 76f -> withWeb { if (it.canGoBack()) it.goBack() }
            x < 140f -> withWeb { if (it.canGoForward()) it.goForward() }
            x < 204f -> withWeb { it.reload() }
            x > designW - 84f -> withWeb { it.loadUrl(home) }
            else -> host.ui.promptText("Endereço ou pesquisa", curUrl) { text -> navigate(text) }
        }
    }

    private fun navigate(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains(' ') || !t.contains('.') -> "https://www.google.com/search?q=" + Uri.encode(t)
            else -> "https://$t"
        }
        webView?.loadUrl(url)
    }
}
