package com.raizer.mr.vr

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * Gerencia as janelas 3D. Tudo roda na thread GL (eventos de toque entram via queueEvent).
 * A ordem da lista é a ordem de empilhamento: a última é a que está em foco.
 */
class WindowManager3D(private val host: Host, private val texWidth: () -> Int) {
    companion object {
        private const val SPEED = 6f              // 1 / duração da animação (s)
        private const val TAP_ANGLE = 0.05f       // rad: abaixo disso é toque, acima é arraste
        private const val MAX_PITCH = 0.9f        // limite de segurança vertical (~51°)
        private const val MIN_W = 0.7f
        private const val MAX_W = 4.2f
        private val SPAWN_YAW_DEG = floatArrayOf(0f, -34f, 34f, -68f, 68f, -102f, 102f)
    }

    val windows = ArrayList<VrWindow>()
    private var nextId = 1
    private var spawnCount = 0

    // estado do ponteiro
    private var grab: Hit? = null
    private var downYaw = 0f
    private var downPitch = 0f
    private var offYaw = 0f
    private var offPitch = 0f
    val hasGrab: Boolean get() = grab != null

    fun addWindow(content: WindowContent, widthM: Float, yaw: Float, pitch: Float, dist: Float): VrWindow {
        content.host = host
        val w = VrWindow(nextId++, content, texWidth())
        w.widthM = widthM
        w.restoreW = widthM
        w.yaw = yaw
        w.pitch = pitch
        w.dist = dist
        windows.add(w)
        return w
    }

    fun openApp(app: AppId) {
        val existing = windows.firstOrNull { it.content.appId == app && !it.closing }
        if (existing != null) {
            restore(existing)
            focus(existing)
            return
        }
        val c = app.create()
        val yawDeg = SPAWN_YAW_DEG[spawnCount % SPAWN_YAW_DEG.size]
        spawnCount++
        val w = addWindow(c, c.preferredWidthM, Math.toRadians(yawDeg.toDouble()).toFloat(), 0.1f, 2.2f)
        w.anim = 0f
    }

    fun infos(): List<WindowInfo> =
        windows.filter { it.content !is SystemBarApp && it.content.appId != AppId.RECENTS && !it.closing }
            .map { WindowInfo(it.id, it.content.title, it.minimized) }

    fun restoreOrFocus(id: Int) {
        val w = windows.firstOrNull { it.id == id } ?: return
        restore(w)
        focus(w)
    }

    fun onGlContextLost() {
        for (w in windows) {
            w.texId = 0
            w.uploadPending = true
        }
    }

    private fun focus(w: VrWindow) {
        windows.remove(w)
        windows.add(w)
    }

    private fun close(w: VrWindow) { w.closing = true; w.target = 0f }
    private fun minimize(w: VrWindow) { w.minimized = true; w.target = 0f }
    private fun restore(w: VrWindow) {
        if (w.minimized) {
            w.minimized = false
            w.target = 1f
            w.needsRedraw = true
        }
    }

    private fun toggleMax(w: VrWindow) {
        if (!w.maximized) {
            w.restoreW = w.widthM
            w.widthM = min(w.widthM * 1.7f, MAX_W)
            w.maximized = true
        } else {
            w.widthM = w.restoreW
            w.maximized = false
        }
    }

    /** Animações, tick dos conteúdos, redesenho e upload de texturas. */
    fun update(dt: Float, now: Long) {
        val remove = ArrayList<VrWindow>()
        for (w in windows.toList()) {
            if (w.anim < w.target) w.anim = min(w.target, w.anim + dt * SPEED)
            else if (w.anim > w.target) w.anim = max(w.target, w.anim - dt * SPEED)
            if (w.closing && w.anim <= 0f) { remove.add(w); continue }
            if (w.target <= 0f && w.anim <= 0f) continue   // minimizada e invisível
            if (w.content.tick(now)) w.needsRedraw = true
            if (w.content.dirty) { w.content.dirty = false; w.needsRedraw = true }
            if (w.needsRedraw) w.redraw()
            if (w.uploadPending) w.upload()
        }
        for (w in remove) { windows.remove(w); w.release() }
    }

    // ---------- interação ----------

    private fun pick(ray: Ray): Hit? {
        for (i in windows.indices.reversed()) {
            val w = windows[i]
            if (w.closing || w.minimized || w.anim < 0.5f) continue
            val h = w.hit(ray)
            if (h != null) return h
        }
        return null
    }

    private fun yawOf(r: Ray) = atan2(r.dx, -r.dz)
    private fun pitchOf(r: Ray) = asin(r.dy.coerceIn(-1f, 1f))
    private fun wrap(a: Float): Float {
        var x = a
        while (x > PI) x -= (2 * PI).toFloat()
        while (x < -PI) x += (2 * PI).toFloat()
        return x
    }

    fun pointerDown(ray: Ray) {
        val h = pick(ray)
        grab = h
        if (h == null) return
        focus(h.w)
        downYaw = yawOf(ray)
        downPitch = pitchOf(ray)
        if (h.part == Part.TITLE) {
            offYaw = wrap(downYaw - h.w.yaw)
            offPitch = downPitch - h.w.pitch
        }
    }

    fun pointerMove(ray: Ray) {
        val g = grab ?: return
        val w = g.w
        when (g.part) {
            Part.TITLE -> {
                w.yaw = wrap(yawOf(ray) - offYaw)
                w.pitch = (pitchOf(ray) - offPitch).coerceIn(-MAX_PITCH, MAX_PITCH)
            }
            Part.RESIZE -> {
                val m = w.planeMeters(ray) ?: return
                val byX = m[0] * 2f
                val byY = -m[1] * 2f * (w.logW / w.logH)
                w.widthM = ((byX + byY) / 2f).coerceIn(MIN_W, MAX_W)
                w.maximized = false
            }
            else -> {}
        }
    }

    fun pointerUp(ray: Ray) {
        val g = grab ?: return
        grab = null
        val moved = abs(wrap(yawOf(ray) - downYaw)) > TAP_ANGLE || abs(pitchOf(ray) - downPitch) > TAP_ANGLE
        if (moved) return
        when (g.part) {
            Part.CLOSE -> close(g.w)
            Part.MIN -> minimize(g.w)
            Part.MAX -> toggleMax(g.w)
            Part.CONTENT -> {
                g.w.content.onTap(g.x, g.y)
                g.w.needsRedraw = true
            }
            else -> {}
        }
    }
}
