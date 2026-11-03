package com.raizer.mr.vr

import kotlin.math.hypot
import kotlin.math.max

/**
 * Transforma as mãos detectadas em ações na interface (roda na thread GL):
 *  - apontar com o indicador move o cursor;
 *  - pinça = clicar; manter a pinça e mover = arrastar (janelas, rolagem);
 *  - pinça nas duas mãos sobre uma janela = redimensionar;
 *  - mão aberta por 2 s (opcional) = recentralizar;
 *  - calibração em dois passos (mão aberta, pinça).
 */
class GestureEngine(
    private val state: HandsState,
    private val wm: WindowManager3D,
    private val makeRay: (Float, Float) -> Ray,
    private val recenter: () -> Unit,
    private val saveCalibration: (Float, Float) -> Unit
) {
    private class Slot {
        var has = false
        var pinching = false
        var ratio = 1f
        var gesture = Gesture.NONE
        var x = 0f
        var y = 0f
    }

    private val prim = Slot()
    private val sec = Slot()

    var cursorVisible = false
    var cursorX = 0f
    var cursorY = 0f
    var cursorPinch = false

    private var down = false
    private var scaling = false
    private var scaleBase = 1f
    private var openSince = 0L
    private var openFired = false

    // calibração
    private var calibStart = 0L
    private var calibSum = 0f
    private var calibN = 0
    private var openRatio = 0f

    private fun lost() {
        prim.has = false; prim.pinching = false
        sec.has = false; sec.pinching = false
        if (down) { wm.cancelPointer(); down = false }
        if (scaling) { wm.endScale(); scaling = false }
        cursorVisible = false
    }

    private fun process(slot: Slot, hand: HandData, aspect: Float) {
        val lm = hand.lm
        slot.ratio = HandMath.pinchRatio(lm, aspect)
        if (!slot.pinching && slot.ratio < state.pinchOn) slot.pinching = true
        else if (slot.pinching && slot.ratio > state.pinchOff) slot.pinching = false
        slot.gesture = HandMath.classify(lm, aspect, slot.pinching)

        // ponta do indicador (8) ou ponto médio polegar(4)/indicador(8) durante a pinça
        val px: Float
        val py: Float
        if (slot.pinching) {
            px = (lm[12] + lm[24]) / 2f
            py = (lm[13] + lm[25]) / 2f
        } else {
            px = lm[24]
            py = lm[25]
        }
        val g = state.gain
        val nx = ((px * 2f - 1f) * g).coerceIn(-1f, 1f)
        val ny = ((1f - py * 2f) * g).coerceIn(-1f, 1f)
        if (!slot.has) {
            slot.x = nx; slot.y = ny
        } else {
            slot.x += (nx - slot.x) * 0.5f
            slot.y += (ny - slot.y) * 0.5f
        }
        slot.has = true
    }

    fun update(nowMs: Long, fresh: Boolean, touching: Boolean) {
        val f = state.frame
        if (!state.enabled || !fresh || f == null) {
            lost()
            state.gestureText = emptyList()
            return
        }
        if (f.hands.isEmpty()) {
            lost()
            state.gestureText = listOf("Nenhuma mão detectada")
            return
        }
        val p = f.hands.firstOrNull { it.label == "Direita" } ?: f.hands[0]
        val s = f.hands.firstOrNull { it !== p }
        process(prim, p, f.aspect)
        if (s != null) process(sec, s, f.aspect) else { sec.has = false; sec.pinching = false }

        val texts = ArrayList<String>()
        texts.add("${p.label}: ${prim.gesture.label}")
        if (s != null) texts.add("${s.label}: ${sec.gesture.label}")
        state.gestureText = texts

        cursorVisible = true
        cursorX = prim.x
        cursorY = prim.y
        cursorPinch = prim.pinching

        if (state.calibStep != 0) {
            if (down) { wm.cancelPointer(); down = false }
            calibrate(prim, nowMs)
            return
        }
        if (touching) {
            if (down) { wm.cancelPointer(); down = false }
            return
        }

        // gesto de mão aberta por 2 s = recentralizar (opcional)
        if (state.openRecenter && prim.gesture == Gesture.OPEN) {
            if (openSince == 0L) openSince = nowMs
            else if (!openFired && nowMs - openSince > 2000L) { recenter(); openFired = true }
        } else {
            openSince = 0L
            openFired = false
        }

        // duas mãos em pinça sobre uma janela = redimensionar
        if (sec.has && prim.pinching && sec.pinching) {
            val d = hypot(prim.x - sec.x, prim.y - sec.y)
            if (!scaling) {
                val ray = makeRay((prim.x + sec.x) / 2f, (prim.y + sec.y) / 2f)
                if (wm.beginScale(ray)) {
                    scaling = true
                    scaleBase = max(d, 0.05f)
                    down = false
                }
            }
            if (scaling) {
                wm.updateScale(d / scaleBase)
                return
            }
        } else if (scaling) {
            wm.endScale()
            scaling = false
        }

        // mão principal: apontar / pinçar / arrastar
        val ray = makeRay(prim.x, prim.y)
        if (prim.pinching) {
            if (!down) { wm.pointerDown(ray); down = true } else wm.pointerMove(ray)
        } else if (down) {
            wm.pointerUp(ray)
            down = false
        }
    }

    private fun calibrate(slot: Slot, now: Long) {
        when (state.calibStep) {
            1 -> {
                if (slot.ratio > 0.7f) {
                    if (calibStart == 0L) { calibStart = now; calibSum = 0f; calibN = 0 }
                    calibSum += slot.ratio
                    calibN++
                    state.calibProgress = ((now - calibStart) / 1500f).coerceAtMost(1f)
                    if (now - calibStart >= 1500L) {
                        openRatio = calibSum / calibN
                        state.calibStep = 2
                        state.calibProgress = 0f
                        calibStart = 0L
                        state.calibMessage = "Agora faça a pinça (polegar + indicador) e segure"
                    }
                } else {
                    calibStart = 0L
                    state.calibProgress = 0f
                }
            }
            2 -> {
                if (slot.ratio < openRatio * 0.6f) {
                    if (calibStart == 0L) { calibStart = now; calibSum = 0f; calibN = 0 }
                    calibSum += slot.ratio
                    calibN++
                    state.calibProgress = ((now - calibStart) / 1500f).coerceAtMost(1f)
                    if (now - calibStart >= 1500L) {
                        val closed = calibSum / calibN
                        calibStart = 0L
                        state.calibProgress = 0f
                        if (openRatio - closed < 0.2f) {
                            state.calibStep = 1
                            state.calibMessage = "Calibração inválida (diferença pequena). Abra a mão e tente de novo"
                        } else {
                            val on = closed + 0.30f * (openRatio - closed)
                            val off = closed + 0.55f * (openRatio - closed)
                            state.pinchOn = on
                            state.pinchOff = off
                            saveCalibration(on, off)
                            state.calibStep = 0
                            state.calibMessage = "Calibração concluída"
                        }
                    }
                } else {
                    calibStart = 0L
                    state.calibProgress = 0f
                }
            }
        }
    }
}
