package com.raizer.mr.vr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.opengl.GLES20
import android.opengl.GLUtils
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

enum class Part { CLOSE, MIN, MAX, TITLE, RESIZE, CONTENT }

class Ray(val ox: Float, val oy: Float, val oz: Float, val dx: Float, val dy: Float, val dz: Float)

/** Resultado de um raio contra uma janela. Para CONTENT, x/y já descontam a barra de título. */
class Hit(val w: VrWindow, val part: Part, val x: Float, val y: Float)

/**
 * Janela 3D. Posição em coordenadas ESFÉRICAS DO MUNDO (yaw, pitch, dist) — nunca
 * dependem da câmera, por isso ficam fixas quando a cabeça se move.
 */
class VrWindow(val id: Int, val content: WindowContent, texWidth: Int) {
    companion object {
        const val TITLE_H = 56f
    }

    val chrome = content.chrome
    val logW = content.designW.toFloat()
    val logH = content.designH + if (chrome) TITLE_H else 0f
    val texW = texWidth
    val texH = max(1, (texWidth * logH / logW).roundToInt())

    private val bmp: Bitmap = Bitmap.createBitmap(texW, texH, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bmp)

    var texId = 0
    var uploadPending = true
    var needsRedraw = true

    var widthM = 1.3f
    var restoreW = 1.3f
    var yaw = 0f
    var pitch = 0f
    var dist = 2.2f

    var anim = 0f
    var target = 1f
    var minimized = false
    var maximized = false
    var closing = false

    val heightM: Float get() = widthM * logH / logW

    val pos = FloatArray(3)
    val right = FloatArray(3)
    val up = FloatArray(3)
    val nrm = FloatArray(3)

    private val pBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.GLASS }
    private val pTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#33FFFFFF") }
    private val pBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Theme.NEON_DIM
    }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.WHITE; textSize = 24f }
    private val pBtn = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pGlyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = Theme.WHITE; strokeCap = Paint.Cap.ROUND
    }
    private val pHandle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = Theme.NEON; strokeCap = Paint.Cap.ROUND
    }
    private val clip = Path()
    private val rr = RectF()

    /** Posição/base no mundo a partir de (yaw, pitch, dist). A janela olha para a origem. */
    fun updateBasis() {
        val cp = cos(pitch)
        pos[0] = dist * sin(yaw) * cp
        pos[1] = dist * sin(pitch)
        pos[2] = -dist * cos(yaw) * cp
        nrm[0] = -pos[0] / dist; nrm[1] = -pos[1] / dist; nrm[2] = -pos[2] / dist
        // right = normalize(cross(Y, n)) = (nz, 0, -nx)
        var rx = nrm[2]
        var rz = -nrm[0]
        val l = Math.sqrt((rx * rx + rz * rz).toDouble()).toFloat()
        if (l < 1e-5f) { rx = 1f; rz = 0f } else { rx /= l; rz /= l }
        right[0] = rx; right[1] = 0f; right[2] = rz
        // up = cross(n, right)
        up[0] = nrm[1] * right[2] - nrm[2] * right[1]
        up[1] = nrm[2] * right[0] - nrm[0] * right[2]
        up[2] = nrm[0] * right[1] - nrm[1] * right[0]
    }

    fun hit(r: Ray): Hit? {
        updateBasis()
        val dn = r.dx * nrm[0] + r.dy * nrm[1] + r.dz * nrm[2]
        if (abs(dn) < 1e-4f) return null
        val t = ((pos[0] - r.ox) * nrm[0] + (pos[1] - r.oy) * nrm[1] + (pos[2] - r.oz) * nrm[2]) / dn
        if (t <= 0f) return null
        val px = r.ox + r.dx * t - pos[0]
        val py = r.oy + r.dy * t - pos[1]
        val pz = r.oz + r.dz * t - pos[2]
        val lx = (px * right[0] + py * right[1] + pz * right[2]) / widthM
        val ly = (px * up[0] + py * up[1] + pz * up[2]) / heightM
        if (abs(lx) > 0.5f || abs(ly) > 0.5f) return null
        return partAt((lx + 0.5f) * logW, (0.5f - ly) * logH)
    }

    /** Ponto do raio no plano da janela, em metros relativos ao centro (sem limites). */
    fun planeMeters(r: Ray): FloatArray? {
        updateBasis()
        val dn = r.dx * nrm[0] + r.dy * nrm[1] + r.dz * nrm[2]
        if (abs(dn) < 1e-4f) return null
        val t = ((pos[0] - r.ox) * nrm[0] + (pos[1] - r.oy) * nrm[1] + (pos[2] - r.oz) * nrm[2]) / dn
        if (t <= 0f) return null
        val px = r.ox + r.dx * t - pos[0]
        val py = r.oy + r.dy * t - pos[1]
        val pz = r.oz + r.dz * t - pos[2]
        return floatArrayOf(
            px * right[0] + py * right[1] + pz * right[2],
            px * up[0] + py * up[1] + pz * up[2]
        )
    }

    /** Coordenadas de conteúdo (sem a barra de título) para um raio, mesmo fora dos limites. */
    fun contentCoords(r: Ray): FloatArray? {
        val m = planeMeters(r) ?: return null
        val x = (m[0] / widthM + 0.5f) * logW
        val y = (0.5f - m[1] / heightM) * logH - (if (chrome) TITLE_H else 0f)
        return floatArrayOf(x, y)
    }

    private fun partAt(x: Float, y: Float): Hit {
        if (!chrome) return Hit(this, Part.CONTENT, x, y)
        if (y < TITLE_H) {
            val cy = 28f
            val rad2 = 22f * 22f
            fun near(cx: Float) = (x - cx) * (x - cx) + (y - cy) * (y - cy) <= rad2
            val part = when {
                near(logW - 30f) -> Part.CLOSE
                near(logW - 74f) -> Part.MAX
                near(logW - 118f) -> Part.MIN
                else -> Part.TITLE
            }
            return Hit(this, part, x, y)
        }
        if (x > logW - 56f && y > logH - 56f) return Hit(this, Part.RESIZE, x, y)
        return Hit(this, Part.CONTENT, x, y - TITLE_H)
    }

    fun redraw() {
        bmp.eraseColor(Color.TRANSPARENT)
        canvas.save()
        canvas.scale(texW / logW, texH / logH)
        if (chrome) drawChrome()
        canvas.save()
        if (chrome) canvas.translate(0f, TITLE_H)
        canvas.clipRect(0f, 0f, content.designW.toFloat(), content.designH.toFloat())
        content.draw(canvas, content.designW, content.designH)
        canvas.restore()
        canvas.restore()
        needsRedraw = false
        uploadPending = true
    }

    private fun drawChrome() {
        rr.set(1f, 1f, logW - 1f, logH - 1f)
        canvas.drawRoundRect(rr, 28f, 28f, pBg)
        canvas.save()
        clip.reset()
        clip.addRoundRect(rr, 28f, 28f, Path.Direction.CW)
        canvas.clipPath(clip)
        canvas.drawRect(0f, 0f, logW, TITLE_H, pTitle)
        canvas.restore()
        canvas.drawRoundRect(rr, 28f, 28f, pBorder)
        canvas.drawText(content.title, 28f, 36f, pText)

        // minimizar
        var cx = logW - 118f
        pBtn.color = Theme.GLASS_LIGHT
        canvas.drawCircle(cx, 28f, 16f, pBtn)
        canvas.drawLine(cx - 7f, 28f, cx + 7f, 28f, pGlyph)
        // maximizar
        cx = logW - 74f
        canvas.drawCircle(cx, 28f, 16f, pBtn)
        canvas.drawRect(cx - 6f, 22f, cx + 6f, 34f, pGlyph)
        // fechar
        cx = logW - 30f
        pBtn.color = Color.parseColor("#66FF5F57")
        canvas.drawCircle(cx, 28f, 16f, pBtn)
        canvas.drawLine(cx - 6f, 22f, cx + 6f, 34f, pGlyph)
        canvas.drawLine(cx - 6f, 34f, cx + 6f, 22f, pGlyph)

        // alça de redimensionar (canto inferior direito)
        canvas.drawLine(logW - 34f, logH - 14f, logW - 14f, logH - 34f, pHandle)
        canvas.drawLine(logW - 24f, logH - 14f, logW - 14f, logH - 24f, pHandle)
    }

    /** Deve ser chamado na thread GL. */
    fun upload() {
        if (texId == 0) {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            texId = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        }
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        uploadPending = false
    }

    fun release() {
        if (texId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(texId), 0)
            texId = 0
        }
        bmp.recycle()
    }
}
