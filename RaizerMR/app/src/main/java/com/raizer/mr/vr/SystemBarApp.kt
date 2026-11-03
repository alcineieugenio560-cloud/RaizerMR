package com.raizer.mr.vr

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Barra flutuante do sistema, fixa no mundo 3D: Home, Apps, Config, Recentes, Wi-Fi, bateria, hora. */
class SystemBarApp : WindowContent("Barra", 720, 96) {
    override val chrome: Boolean = false
    override val preferredWidthM: Float get() = 1.5f

    private val pBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.GLASS }
    private val pBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Theme.NEON_DIM
    }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.WHITE; textSize = 26f }
    private val pBatt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = Theme.WHITE
    }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.NEON }
    private val rect = RectF()
    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    private var battery = -1
    private var charging = false
    private var wifi = false
    private var timeStr = ""
    private var lastPoll = 0L

    private fun refresh(): Boolean {
        val ctx = host.context
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val sc = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val b = if (lvl >= 0 && sc > 0) lvl * 100 / sc else -1
        val ch = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val w = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val t = fmt.format(Date())
        val changed = b != battery || ch != charging || w != wifi || t != timeStr
        battery = b; charging = ch; wifi = w; timeStr = t
        return changed
    }

    override fun tick(nowMs: Long): Boolean {
        if (nowMs - lastPoll < 2000L) return false
        lastPoll = nowMs
        return refresh()
    }

    override fun draw(c: Canvas, w: Int, h: Int) {
        if (timeStr.isEmpty()) refresh()
        rect.set(1f, 1f, w - 1f, h - 1f)
        c.drawRoundRect(rect, 48f, 48f, pBg)
        c.drawRoundRect(rect, 48f, 48f, pBorder)

        val col = Theme.WHITE
        Icons.draw(c, Icons.Kind.HOME, 50f, 48f, 44f, col)
        Icons.draw(c, Icons.Kind.APPS, 130f, 48f, 44f, col)
        Icons.draw(c, Icons.Kind.SETTINGS, 210f, 48f, 44f, col)
        Icons.draw(c, Icons.Kind.RECENTS, 290f, 48f, 44f, col)

        Icons.draw(c, if (wifi) Icons.Kind.WIFI else Icons.Kind.WIFI_OFF, 400f, 48f, 40f, if (wifi) Theme.NEON else Theme.MUTED)

        // bateria
        rect.set(440f, 36f, 480f, 60f)
        c.drawRoundRect(rect, 5f, 5f, pBatt)
        c.drawRect(480f, 43f, 484f, 53f, pFill)
        if (battery >= 0) {
            rect.set(443f, 39f, 443f + 34f * battery / 100f, 57f)
            c.drawRect(rect, pFill)
        }
        pText.textAlign = Paint.Align.LEFT
        c.drawText(if (battery >= 0) "$battery%" else "--", 494f, 57f, pText)

        pText.textAlign = Paint.Align.RIGHT
        c.drawText(timeStr, w - 26f, 58f, pText)
        pText.textAlign = Paint.Align.LEFT
    }

    override fun onTap(x: Float, y: Float) {
        if (x < 10f || x > 330f) return
        when (((x - 10f) / 80f).toInt()) {
            0 -> host.recenter()                    // Home: recentraliza a interface
            1 -> host.openApp(AppId.LAUNCHER)
            2 -> host.openApp(AppId.SETTINGS)
            3 -> host.openApp(AppId.RECENTS)
        }
    }
}
