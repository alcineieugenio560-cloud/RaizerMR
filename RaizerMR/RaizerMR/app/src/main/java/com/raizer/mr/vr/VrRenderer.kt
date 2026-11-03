package com.raizer.mr.vr

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import com.raizer.mr.Prefs
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/**
 * Renderizador do ambiente VR.
 *
 * Mundo GL: x = leste, y = cima, z = -norte. As janelas vivem em coordenadas do mundo;
 * a matriz de visão vem SÓ da orientação da cabeça, então os menus não seguem a câmera.
 */
class VrRenderer(
    private val view: VrSurfaceView,
    private val head: HeadTracker,
    private val startApp: AppId?,
    override val ui: UiBridge,
    override val hands: HandsState
) : GLSurfaceView.Renderer, Host {

    companion object {
        private val STARS = intArrayOf(250, 700, 1500)
        private val FPS_CAP_MS = longArrayOf(33L, 16L, 0L)
        val TEX_W = intArrayOf(768, 1024, 1280)
        val RENDER_SCALE = floatArrayOf(0.6f, 0.85f, 1.0f)
        private const val FOV_MONO = 75f
        private const val FOV_STEREO = 90f
        private const val IPD = 0.064f
        private const val HAND_DIST = 1.2f
        // sensor ENU (x leste, y norte, z cima) <- mundo GL; linhas da matriz
        private val CINV = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)

        private const val TEX_VS = """
            attribute vec4 aPos; attribute vec2 aUV; uniform mat4 uMVP; varying vec2 vUV;
            void main(){ vUV = aUV; gl_Position = uMVP * aPos; }"""
        private const val TEX_FS = """
            precision mediump float; uniform sampler2D uTex; uniform float uAlpha; varying vec2 vUV;
            void main(){ gl_FragColor = texture2D(uTex, vUV) * uAlpha; }"""
        private const val OES_VS = """
            attribute vec4 aPos; attribute vec2 aUV; uniform mat4 uMVP; uniform mat4 uST; varying vec2 vUV;
            void main(){ vUV = (uST * vec4(aUV, 0.0, 1.0)).xy; gl_Position = uMVP * aPos; }"""
        private const val OES_FS = """#extension GL_OES_EGL_image_external : require
            precision mediump float; uniform samplerExternalOES uTex; uniform float uAlpha; varying vec2 vUV;
            void main(){ gl_FragColor = vec4(texture2D(uTex, vUV).rgb, 1.0) * uAlpha; }"""
        private const val PT_VS = """
            attribute vec4 aData; uniform mat4 uMVP; uniform float uPointSize;
            varying float vB; varying float vD;
            void main(){
                vec4 p = vec4(aData.xyz, 1.0);
                vB = aData.w; vD = length(p.xz);
                gl_Position = uMVP * p; gl_PointSize = uPointSize;
            }"""
        private const val PT_FS = """
            precision mediump float; uniform vec4 uColor; uniform float uFade; uniform float uRound;
            varying float vB; varying float vD;
            void main(){
                float a = uColor.a * vB;
                if (uFade > 0.5) a *= clamp(1.0 - vD / 12.0, 0.0, 1.0);
                if (uRound > 0.5) {
                    float r = length(gl_PointCoord - vec2(0.5));
                    if (r > 0.5) discard;
                    a *= smoothstep(0.5, 0.15, r);
                }
                gl_FragColor = vec4(uColor.rgb * a, a);
            }"""
    }

    // ---------- Host ----------
    override val context: Context get() = view.context
    override val headTrackingAvailable: Boolean get() = head.available

    private var stereoOn = Prefs.stereo(view.context)
    override var stereo: Boolean
        get() = stereoOn
        set(v) { stereoOn = v; Prefs.setStereo(view.context, v) }

    private var q = Prefs.quality(view.context)
    override var quality: Int
        get() = q
        set(v) {
            q = v.coerceIn(0, 2)
            Prefs.setQuality(view.context, q)
            starsDirty = true
            view.post { view.applyRenderScale(q) }
        }

    override fun openApp(app: AppId) = wm.openApp(app)
    override fun recenter() { recenterReq = true; lookYaw = 0f; lookPitch = 0f }
    override fun windowInfos(): List<WindowInfo> = wm.infos()
    override fun restoreOrFocus(id: Int) = wm.restoreOrFocus(id)

    // ---------- estado ----------
    private val wm = WindowManager3D(this) { TEX_W[q] }
    private val engine = GestureEngine(
        hands, wm,
        { x, y -> makeRay(x, y) },
        { recenter() },
        { on, off -> Prefs.setPinch(view.context, on, off) }
    )
    private var started = false
    private var sw = 1
    private var sh = 1
    private var lastNs = 0L
    private val density = view.resources.displayMetrics.density

    private lateinit var texProg: GlProgram
    private lateinit var oesProg: GlProgram
    private lateinit var ptProg: GlProgram
    private var tMVP = 0; private var tPos = 0; private var tUV = 0; private var tTex = 0; private var tAlpha = 0
    private var oMVP = 0; private var oPos = 0; private var oUV = 0; private var oTex = 0
    private var oAlpha = 0; private var oST = 0
    private var pMVP = 0; private var pData = 0; private var pSize = 0
    private var pColor = 0; private var pFade = 0; private var pRound = 0

    private lateinit var quadBuf: FloatBuffer
    private lateinit var oesQuadBuf: FloatBuffer
    private lateinit var gridBuf: FloatBuffer
    private var gridCount = 0
    private var starBuf: FloatBuffer? = null
    private var starCount = 0
    private var starsDirty = true
    private val curBuf: FloatBuffer = floatBuf(floatArrayOf(0f, 0f, 0f, 1f))

    // esqueleto das mãos (até 2), reaproveitando buffers
    private val boneCount = HandMath.CONNECTIONS.size * 2
    private val lineArr = FloatArray(boneCount * 4)
    private val ptArr = FloatArray(21 * 4)
    private val lineBufs = Array(2) { floatBuf(FloatArray(boneCount * 4)) }
    private val ptBufs = Array(2) { floatBuf(FloatArray(21 * 4)) }
    private val handLeft = BooleanArray(2)
    private var handDrawn = 0

    // câmera
    private val rot = FloatArray(9)
    private val m3 = FloatArray(9)
    private val base = FloatArray(16)
    private val view4 = FloatArray(16)
    private val eyeView = FloatArray(16)
    private val tmp = FloatArray(16)
    private val proj = FloatArray(16)
    private val vp = FloatArray(16)
    private val mvp = FloatArray(16)
    private val model = FloatArray(16)
    private val vr = FloatArray(16)
    private val ident = FloatArray(16)
    @Volatile private var recenterReq = true
    private var yawOffDeg = 0f
    private var lookYaw = 0f
    private var lookPitch = 0f

    // ponteiro (toque)
    private var touching = false
    private var curX = 0f
    private var curY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var lookDragging = false

    // ---------- GL ----------
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glClearColor(0.015f, 0.02f, 0.035f, 1f)

        texProg = GlProgram(TEX_VS, TEX_FS)
        tMVP = texProg.u("uMVP"); tPos = texProg.a("aPos"); tUV = texProg.a("aUV")
        tTex = texProg.u("uTex"); tAlpha = texProg.u("uAlpha")
        oesProg = GlProgram(OES_VS, OES_FS)
        oMVP = oesProg.u("uMVP"); oPos = oesProg.a("aPos"); oUV = oesProg.a("aUV")
        oTex = oesProg.u("uTex"); oAlpha = oesProg.u("uAlpha"); oST = oesProg.u("uST")
        ptProg = GlProgram(PT_VS, PT_FS)
        pMVP = ptProg.u("uMVP"); pData = ptProg.a("aData"); pSize = ptProg.u("uPointSize")
        pColor = ptProg.u("uColor"); pFade = ptProg.u("uFade"); pRound = ptProg.u("uRound")

        quadBuf = floatBuf(floatArrayOf(
            -0.5f, -0.5f, 0f, 1f,
            0.5f, -0.5f, 1f, 1f,
            -0.5f, 0.5f, 0f, 0f,
            0.5f, 0.5f, 1f, 0f
        ))
        // textura externa: v = 0 embaixo (a matriz da SurfaceTexture cuida da inversão)
        oesQuadBuf = floatBuf(floatArrayOf(
            -0.5f, -0.5f, 0f, 0f,
            0.5f, -0.5f, 1f, 0f,
            -0.5f, 0.5f, 0f, 1f,
            0.5f, 0.5f, 1f, 1f
        ))
        buildGrid()
        Matrix.setIdentityM(ident, 0)
        starsDirty = true
        wm.onGlContextLost()

        if (!started) {
            started = true
            wm.addWindow(SystemBarApp(), 1.5f, 0f, -0.55f, 1.7f)
            wm.openApp(startApp ?: AppId.LAUNCHER)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        sw = width
        sh = height
        GLES20.glViewport(0, 0, width, height)
    }

    private fun buildGrid() {
        val y = -1.6f
        val n = 12
        val list = ArrayList<Float>()
        for (i in -n..n) {
            list.add(i.toFloat()); list.add(y); list.add(-n.toFloat()); list.add(1f)
            list.add(i.toFloat()); list.add(y); list.add(n.toFloat()); list.add(1f)
            list.add(-n.toFloat()); list.add(y); list.add(i.toFloat()); list.add(1f)
            list.add(n.toFloat()); list.add(y); list.add(i.toFloat()); list.add(1f)
        }
        gridCount = list.size / 4
        gridBuf = floatBuf(list.toFloatArray())
    }

    private fun rebuildStars() {
        val n = STARS[q]
        val rnd = Random(42)
        val a = FloatArray(n * 4)
        for (i in 0 until n) {
            val z = rnd.nextFloat() * 2f - 1f
            val ang = rnd.nextFloat() * 6.2831853f
            val r = sqrt(1f - z * z)
            a[i * 4] = r * cos(ang) * 60f
            a[i * 4 + 1] = z * 60f
            a[i * 4 + 2] = r * sin(ang) * 60f
            val b = rnd.nextFloat()
            a[i * 4 + 3] = 0.3f + 0.7f * b * b
        }
        starBuf = floatBuf(a)
        starCount = n
    }

    private fun updateView() {
        if (head.available && head.copyTo(rot)) {
            // M = R^T * CINV  (visão = inverso da orientação da câmera)
            for (i in 0..2) for (j in 0..2) {
                var s = 0f
                for (k in 0..2) s += rot[k * 3 + i] * CINV[k * 3 + j]
                m3[i * 3 + j] = s
            }
            for (i in 0 until 16) base[i] = 0f
            for (r in 0..2) for (c in 0..2) base[c * 4 + r] = m3[r * 3 + c]
            base[15] = 1f
            if (recenterReq) {
                val yaw = atan2(-m3[6], m3[8])           // direção do olhar no plano horizontal
                yawOffDeg = -Math.toDegrees(yaw.toDouble()).toFloat()
                recenterReq = false
            }
            System.arraycopy(base, 0, view4, 0, 16)
            Matrix.rotateM(view4, 0, yawOffDeg, 0f, 1f, 0f)
        } else {
            Matrix.setIdentityM(view4, 0)
            Matrix.rotateM(view4, 0, -lookPitch, 1f, 0f, 0f)
            Matrix.rotateM(view4, 0, lookYaw, 0f, 1f, 0f)
        }
    }

    /** Converte os 21 pontos de cada mão em linhas/pontos no espaço do mundo, à frente da câmera. */
    private fun buildHands(fresh: Boolean) {
        handDrawn = 0
        val f = hands.frame
        if (!hands.enabled || !fresh || f == null) return
        for (hi in 0 until minOf(2, f.hands.size)) {
            val h = f.hands[hi]
            val wx = FloatArray(21)
            val wy = FloatArray(21)
            val wz = FloatArray(21)
            for (i in 0 until 21) {
                val nx = ((h.lm[i * 3] * 2f - 1f) * hands.gain).coerceIn(-1f, 1f)
                val ny = ((1f - h.lm[i * 3 + 1] * 2f) * hands.gain).coerceIn(-1f, 1f)
                val r = makeRay(nx, ny)
                val d = (HAND_DIST + h.lm[i * 3 + 2] * 2f).coerceIn(0.6f, 2f)
                wx[i] = r.dx * d; wy[i] = r.dy * d; wz[i] = r.dz * d
                ptArr[i * 4] = wx[i]; ptArr[i * 4 + 1] = wy[i]; ptArr[i * 4 + 2] = wz[i]; ptArr[i * 4 + 3] = 1f
            }
            for ((ci, cn) in HandMath.CONNECTIONS.withIndex()) {
                val a = cn[0]
                val b = cn[1]
                val o = ci * 8
                lineArr[o] = wx[a]; lineArr[o + 1] = wy[a]; lineArr[o + 2] = wz[a]; lineArr[o + 3] = 1f
                lineArr[o + 4] = wx[b]; lineArr[o + 5] = wy[b]; lineArr[o + 6] = wz[b]; lineArr[o + 7] = 1f
            }
            lineBufs[hi].clear(); lineBufs[hi].put(lineArr); lineBufs[hi].position(0)
            ptBufs[hi].clear(); ptBufs[hi].put(ptArr); ptBufs[hi].position(0)
            handLeft[hi] = h.label == "Esquerda"
            handDrawn = hi + 1
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        val t0 = System.nanoTime()
        val dt = if (lastNs == 0L) 0.016f else ((t0 - lastNs) / 1e9f).coerceAtMost(0.1f)
        lastNs = t0
        if (starsDirty) { rebuildStars(); starsDirty = false }

        updateView()
        val nowMs = SystemClock.uptimeMillis()
        val fr = hands.frame
        val fresh = fr != null && nowMs - fr.timeMs < 400L
        engine.update(nowMs, fresh, touching)
        buildHands(fresh)
        wm.update(dt, nowMs)

        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glViewport(0, 0, sw, sh)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val handCur = engine.cursorVisible && !touching
        if (stereoOn) {
            val half = sw / 2
            for (eye in 0..1) {
                val x0 = eye * half
                GLES20.glViewport(x0, 0, half, sh)
                GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
                GLES20.glScissor(x0, 0, half, sh)
                Matrix.perspectiveM(proj, 0, FOV_STEREO, half.toFloat() / sh, 0.05f, 100f)
                val dx = if (eye == 0) IPD / 2f else -IPD / 2f
                Matrix.setIdentityM(tmp, 0)
                Matrix.translateM(tmp, 0, dx, 0f, 0f)
                Matrix.multiplyMM(eyeView, 0, tmp, 0, view4, 0)
                drawScene(eyeView, proj)
                if (handCur) drawCursor(engine.cursorX, engine.cursorY, engine.cursorPinch)
                else drawCursor(0f, 0f, false)
            }
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        } else {
            Matrix.perspectiveM(proj, 0, FOV_MONO, sw.toFloat() / sh, 0.05f, 100f)
            drawScene(view4, proj)
            if (touching) drawCursor(curX, curY, false)
            else if (handCur) drawCursor(engine.cursorX, engine.cursorY, engine.cursorPinch)
        }

        val cap = FPS_CAP_MS[q]
        if (cap > 0) {
            val el = (System.nanoTime() - t0) / 1_000_000L
            if (el < cap) {
                try { Thread.sleep(cap - el) } catch (e: InterruptedException) { }
            }
        }
    }

    private fun drawScene(v: FloatArray, p: FloatArray) {
        Matrix.multiplyMM(vp, 0, p, 0, v, 0)

        // estrelas: no infinito (sem translação do olho)
        System.arraycopy(v, 0, vr, 0, 16)
        vr[12] = 0f; vr[13] = 0f; vr[14] = 0f
        Matrix.multiplyMM(mvp, 0, p, 0, vr, 0)
        val sb = starBuf
        if (sb != null) {
            drawPoints(sb, starCount, GLES20.GL_POINTS, mvp, 2.2f * density, 0.75f, 0.88f, 1f, 0.9f, false, true)
        }

        // piso em grade (some com a distância)
        if (q > 0) drawPoints(gridBuf, gridCount, GLES20.GL_LINES, vp, 1f, 0f, 0.72f, 1f, 0.35f, true, false)

        // janelas
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        for (w in wm.windows) {
            if (w.texId == 0 || w.anim <= 0.01f) continue
            w.updateBasis()
            val e = w.anim * w.anim * (3f - 2f * w.anim)
            val s = 0.85f + 0.15f * e
            val ww = w.widthM * s
            val hh = w.heightM * s

            // quadro da janela (moldura + conteúdo desenhado em Canvas)
            setModel(w, ww, hh, 0f, 1f)
            Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
            GLES20.glUseProgram(texProg.id)
            GLES20.glUniform1i(tTex, 0)
            GLES20.glUniformMatrix4fv(tMVP, 1, false, mvp, 0)
            GLES20.glUniform1f(tAlpha, e)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, w.texId)
            quadBuf.position(0)
            GLES20.glEnableVertexAttribArray(tPos)
            GLES20.glVertexAttribPointer(tPos, 2, GLES20.GL_FLOAT, false, 16, quadBuf)
            quadBuf.position(2)
            GLES20.glEnableVertexAttribArray(tUV)
            GLES20.glVertexAttribPointer(tUV, 2, GLES20.GL_FLOAT, false, 16, quadBuf)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            // conteúdo vivo (WebView) por cima, na região abaixo da barra
            val ext = w.content.externalTexture
            if (ext != null && ext.ready) {
                val top = (if (w.chrome) VrWindow.TITLE_H else 0f) + w.content.externalTop
                val regionH = w.logH - top
                val hf = regionH / w.logH
                val yc = 0.5f - (top + regionH / 2f) / w.logH
                setModel(w, ww, hh, yc * hh, hf)
                Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
                GLES20.glUseProgram(oesProg.id)
                GLES20.glUniform1i(oTex, 0)
                GLES20.glUniformMatrix4fv(oMVP, 1, false, mvp, 0)
                GLES20.glUniformMatrix4fv(oST, 1, false, ext.stMatrix, 0)
                GLES20.glUniform1f(oAlpha, e)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ext.texId)
                oesQuadBuf.position(0)
                GLES20.glEnableVertexAttribArray(oPos)
                GLES20.glVertexAttribPointer(oPos, 2, GLES20.GL_FLOAT, false, 16, oesQuadBuf)
                oesQuadBuf.position(2)
                GLES20.glEnableVertexAttribArray(oUV)
                GLES20.glVertexAttribPointer(oUV, 2, GLES20.GL_FLOAT, false, 16, oesQuadBuf)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            }
        }

        // esqueleto das mãos por cima de tudo
        for (i in 0 until handDrawn) {
            val r = if (handLeft[i]) 0f else 1f
            val g = if (handLeft[i]) 0.72f else 1f
            GLES20.glLineWidth(3f * density)
            drawPoints(lineBufs[i], boneCount, GLES20.GL_LINES, vp, 1f, r, g, 1f, 0.95f, false, false)
            GLES20.glLineWidth(1f)
            drawPoints(ptBufs[i], 21, GLES20.GL_POINTS, vp, 9f * density, r, g, 1f, 1f, false, true)
        }
    }

    /** Matriz de modelo da janela; yOff desloca ao longo do "cima" da janela, hFrac reduz a altura. */
    private fun setModel(w: VrWindow, ww: Float, hh: Float, yOff: Float, hFrac: Float) {
        val hs = hh * hFrac
        model[0] = w.right[0] * ww; model[1] = w.right[1] * ww; model[2] = w.right[2] * ww; model[3] = 0f
        model[4] = w.up[0] * hs; model[5] = w.up[1] * hs; model[6] = w.up[2] * hs; model[7] = 0f
        model[8] = w.nrm[0]; model[9] = w.nrm[1]; model[10] = w.nrm[2]; model[11] = 0f
        model[12] = w.pos[0] + w.up[0] * yOff
        model[13] = w.pos[1] + w.up[1] * yOff
        model[14] = w.pos[2] + w.up[2] * yOff
        model[15] = 1f
    }

    private fun drawPoints(
        buf: FloatBuffer, count: Int, mode: Int, m: FloatArray, size: Float,
        r: Float, g: Float, b: Float, a: Float, fade: Boolean, round: Boolean
    ) {
        GLES20.glUseProgram(ptProg.id)
        GLES20.glUniformMatrix4fv(pMVP, 1, false, m, 0)
        GLES20.glUniform1f(pSize, size)
        GLES20.glUniform4f(pColor, r, g, b, a)
        GLES20.glUniform1f(pFade, if (fade) 1f else 0f)
        GLES20.glUniform1f(pRound, if (round) 1f else 0f)
        buf.position(0)
        GLES20.glEnableVertexAttribArray(pData)
        GLES20.glVertexAttribPointer(pData, 4, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glDrawArrays(mode, 0, count)
    }

    private fun drawCursor(nx: Float, ny: Float, pinch: Boolean) {
        curBuf.put(0, nx)
        curBuf.put(1, ny)
        drawPoints(curBuf, 1, GLES20.GL_POINTS, ident, 30f * density, 0f, 0.72f, 1f, 0.45f, false, true)
        if (pinch) drawPoints(curBuf, 1, GLES20.GL_POINTS, ident, 14f * density, 0f, 0.72f, 1f, 1f, false, true)
        else drawPoints(curBuf, 1, GLES20.GL_POINTS, ident, 11f * density, 1f, 1f, 1f, 1f, false, true)
    }

    // ---------- entrada (chamado na thread GL) ----------

    /** Raio do olho (origem) pelo ponto NDC (nx, ny) da tela/viewport atual. */
    private fun makeRay(nx: Float, ny: Float): Ray {
        val tanHalf = tan(Math.toRadians((if (stereoOn) FOV_STEREO else FOV_MONO) / 2.0)).toFloat()
        val aspect = if (stereoOn) (sw / 2f) / sh else sw.toFloat() / sh
        val cx = nx * tanHalf * aspect
        val cy = ny * tanHalf
        val cz = -1f
        // vetor da câmera -> mundo: transposta da rotação da visão
        val v = view4
        var dx = v[0] * cx + v[1] * cy + v[2] * cz
        var dy = v[4] * cx + v[5] * cy + v[6] * cz
        var dz = v[8] * cx + v[9] * cy + v[10] * cz
        val l = sqrt(dx * dx + dy * dy + dz * dz)
        dx /= l; dy /= l; dz /= l
        return Ray(0f, 0f, 0f, dx, dy, dz)
    }

    /** action: 0 = down, 1 = move, 2 = up/cancel */
    fun onTouch(action: Int, x: Float, y: Float) {
        val nx = if (stereoOn) 0f else x / sw * 2f - 1f
        val ny = if (stereoOn) 0f else 1f - y / sh * 2f
        curX = nx
        curY = ny
        val ray = makeRay(nx, ny)
        when (action) {
            0 -> {
                touching = true
                wm.pointerDown(ray)
                lastX = x; lastY = y
                lookDragging = !head.available && !wm.hasGrab
            }
            1 -> {
                if (lookDragging) {
                    lookYaw -= (x - lastX) * 0.15f
                    lookPitch = (lookPitch + (y - lastY) * 0.15f).coerceIn(-80f, 80f)
                } else {
                    wm.pointerMove(ray)
                }
                lastX = x; lastY = y
            }
            else -> {
                touching = false
                if (!lookDragging) wm.pointerUp(ray)
                lookDragging = false
            }
        }
    }

    private fun floatBuf(a: FloatArray): FloatBuffer {
        val b = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        b.put(a)
        b.position(0)
        return b
    }
}
