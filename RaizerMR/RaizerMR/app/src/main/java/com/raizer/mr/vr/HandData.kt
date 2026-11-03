package com.raizer.mr.vr

import kotlin.math.max
import kotlin.math.sqrt

/** Uma mão detectada: rótulo e 21 pontos (x, y, z normalizados) em sequência. */
class HandData(val label: String, val lm: FloatArray)

/** Resultado de um quadro da câmera. aspect = largura/altura da imagem já girada. */
class HandFrame(val hands: List<HandData>, val aspect: Float, val timeMs: Long)

enum class Gesture(val label: String) {
    NONE("sem gesto"),
    OPEN("mão aberta"),
    FIST("mão fechada"),
    PINCH("pinça"),
    POINT("apontando")
}

/** Estado compartilhado entre a câmera (thread de análise), o renderer (GL) e a UI. */
class HandsState {
    @Volatile var frame: HandFrame? = null
    @Volatile var status: String = "Desligado"
    @Volatile var enabled: Boolean = false
    @Volatile var frontCamera: Boolean = false
    @Volatile var gain: Float = 1.0f
    @Volatile var pinchOn: Float = 0.30f
    @Volatile var pinchOff: Float = 0.50f
    @Volatile var openRecenter: Boolean = false
    @Volatile var gestureText: List<String> = emptyList()

    /** Calibração: 0 = nenhuma, 1 = mão aberta, 2 = pinça. */
    @Volatile var calibStep: Int = 0
    @Volatile var calibProgress: Float = 0f
    @Volatile var calibMessage: String = ""

    var onEnabledChange: (() -> Unit)? = null
    var onCameraChange: (() -> Unit)? = null

    fun setEnabled(v: Boolean) {
        enabled = v
        onEnabledChange?.invoke()
    }

    fun applyCamera(front: Boolean) {
        frontCamera = front
        onCameraChange?.invoke()
    }

    fun startCalibration() {
        calibStep = 1
        calibProgress = 0f
        calibMessage = "Calibração: abra a mão diante da câmera"
    }
}

object HandMath {
    /** Pares de pontos que formam o esqueleto da mão (MediaPipe, 21 landmarks). */
    val CONNECTIONS: Array<IntArray> = arrayOf(
        intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 4),
        intArrayOf(0, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 8),
        intArrayOf(5, 9), intArrayOf(9, 10), intArrayOf(10, 11), intArrayOf(11, 12),
        intArrayOf(9, 13), intArrayOf(13, 14), intArrayOf(14, 15), intArrayOf(15, 16),
        intArrayOf(13, 17), intArrayOf(17, 18), intArrayOf(18, 19), intArrayOf(19, 20),
        intArrayOf(0, 17)
    )

    fun dist(lm: FloatArray, a: Int, b: Int, aspect: Float): Float {
        val dx = (lm[a * 3] - lm[b * 3]) * aspect
        val dy = lm[a * 3 + 1] - lm[b * 3 + 1]
        return sqrt(dx * dx + dy * dy)
    }

    /** Tamanho de referência da mão: pulso até a base do dedo médio. */
    fun size(lm: FloatArray, aspect: Float): Float = max(dist(lm, 0, 9, aspect), 1e-4f)

    /** Distância polegar-indicador relativa ao tamanho da mão (pequena = pinça). */
    fun pinchRatio(lm: FloatArray, aspect: Float): Float = dist(lm, 4, 8, aspect) / size(lm, aspect)

    private fun isExtended(lm: FloatArray, tip: Int, pip: Int, aspect: Float): Boolean =
        dist(lm, tip, 0, aspect) > dist(lm, pip, 0, aspect) * 1.1f

    fun classify(lm: FloatArray, aspect: Float, pinching: Boolean): Gesture {
        if (pinching) return Gesture.PINCH
        val index = isExtended(lm, 8, 6, aspect)
        val middle = isExtended(lm, 12, 10, aspect)
        val ring = isExtended(lm, 16, 14, aspect)
        val pinky = isExtended(lm, 20, 18, aspect)
        var n = 0
        if (index) n++
        if (middle) n++
        if (ring) n++
        if (pinky) n++
        return when {
            n >= 4 -> Gesture.OPEN
            n == 0 -> Gesture.FIST
            n == 1 && index -> Gesture.POINT
            else -> Gesture.NONE
        }
    }
}
