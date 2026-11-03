package com.raizer.mr.vr

import android.app.Activity
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.raizer.mr.Prefs
import kotlin.math.max
import kotlin.math.min

class VrSurfaceView(
    activity: Activity,
    head: HeadTracker,
    startApp: AppId?,
    ui: UiBridge,
    hands: HandsState
) : GLSurfaceView(activity) {
    val renderer = VrRenderer(this, head, startApp, ui, hands)

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        applyRenderScale(Prefs.quality(activity))
    }

    /** Resolução de renderização reduzida nos perfis econômicos (economiza GPU e bateria). */
    fun applyRenderScale(q: Int) {
        val dm = resources.displayMetrics
        val s = VrRenderer.RENDER_SCALE[q.coerceIn(0, 2)]
        val w = max(dm.widthPixels, dm.heightPixels)
        val h = min(dm.widthPixels, dm.heightPixels)
        holder.setFixedSize((w * s).toInt(), (h * s).toInt())
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val sx = if (width > 0) e.x / width else 0f
        val sy = if (height > 0) e.y / height else 0f
        // o renderer trabalha em pixels da superfície (que pode ser menor que a view)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> queueEvent { renderer.onTouch(0, sx * surfaceW(), sy * surfaceH()) }
            MotionEvent.ACTION_MOVE -> queueEvent { renderer.onTouch(1, sx * surfaceW(), sy * surfaceH()) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                queueEvent { renderer.onTouch(2, sx * surfaceW(), sy * surfaceH()) }
        }
        return true
    }

    private fun surfaceW(): Float = holder.surfaceFrame.width().toFloat()
    private fun surfaceH(): Float = holder.surfaceFrame.height().toFloat()
}
