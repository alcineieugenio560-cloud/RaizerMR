package com.raizer.mr.vr

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.view.Surface

/**
 * Textura externa (OES) alimentada por uma Surface. A thread de UI desenha o WebView
 * nessa Surface com um Canvas de hardware (isso inclui vídeo), e a thread GL amostra a textura.
 */
class ExternalTexture(val width: Int, val height: Int) {
    var texId = 0
        private set
    private var st: SurfaceTexture? = null
    private var surf: Surface? = null

    @Volatile private var frameReady = false
    @Volatile var hasFrame = false
        private set

    val stMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    val ready: Boolean get() = texId != 0 && hasFrame

    /** Thread GL. */
    @Synchronized
    fun ensureCreated() {
        if (st != null) return
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texId = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val t = SurfaceTexture(texId)
        t.setDefaultBufferSize(width, height)
        t.setOnFrameAvailableListener { frameReady = true }
        st = t
        surf = Surface(t)
    }

    /** Thread GL: puxa o último quadro, se houver. */
    fun update() {
        val t = synchronized(this) { st } ?: return
        if (!frameReady) return
        frameReady = false
        try {
            t.updateTexImage()
            t.getTransformMatrix(stMatrix)
            hasFrame = true
        } catch (e: Exception) {
        }
    }

    /** Thread de UI: Surface onde o WebView é desenhado (null enquanto a GL não criou). */
    @Synchronized
    fun surface(): Surface? = surf

    /** Contexto GL perdido: descarta tudo sem chamar GL; será recriado sob demanda. */
    @Synchronized
    fun invalidate() {
        try { surf?.release() } catch (e: Exception) { }
        try { st?.release() } catch (e: Exception) { }
        surf = null
        st = null
        texId = 0
        hasFrame = false
        frameReady = false
    }

    /** Thread GL: libera de vez, inclusive a textura. */
    fun release() {
        val id = texId
        invalidate()
        if (id != 0) GLES20.glDeleteTextures(1, intArrayOf(id), 0)
    }
}
