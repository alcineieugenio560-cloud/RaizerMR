package com.raizer.mr.vr

import android.opengl.GLES20

/** Compila e liga um par de shaders GLSL ES 2.0. */
class GlProgram(vs: String, fs: String) {
    val id: Int

    init {
        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        id = GLES20.glCreateProgram()
        GLES20.glAttachShader(id, v)
        GLES20.glAttachShader(id, f)
        GLES20.glLinkProgram(id)
        val st = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, st, 0)
        if (st[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(id)
            GLES20.glDeleteProgram(id)
            throw RuntimeException("Falha ao ligar programa GL: $log")
        }
    }

    fun a(name: String): Int = GLES20.glGetAttribLocation(id, name)
    fun u(name: String): Int = GLES20.glGetUniformLocation(id, name)

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val st = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, st, 0)
        if (st[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(s)
            GLES20.glDeleteShader(s)
            throw RuntimeException("Falha ao compilar shader: $log")
        }
        return s
    }
}
