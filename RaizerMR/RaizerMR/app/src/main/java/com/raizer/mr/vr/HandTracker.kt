package com.raizer.mr.vr

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Câmera (CameraX) + MediaPipe Hand Landmarker.
 * Só roda quando o usuário liga o rastreamento; nada é simulado: sem câmera/modelo/permissão,
 * o estado mostra o motivo e o modo por toque continua funcionando.
 */
class HandTracker(private val activity: ComponentActivity, private val state: HandsState) {
    companion object {
        const val MODEL = "hand_landmarker.task"
        const val REQ_CAMERA = 77
    }

    private var executor: ExecutorService? = null
    private var provider: ProcessCameraProvider? = null
    private var landmarker: HandLandmarker? = null
    @Volatile private var generation = 0
    private var lastTs = 0L

    /** Liga ou desliga conforme state.enabled. Chamar na thread principal. */
    fun apply() {
        if (state.enabled) start() else stop()
    }

    private fun hasModel(): Boolean = try {
        activity.assets.open(MODEL).close()
        true
    } catch (e: Exception) {
        false
    }

    fun start() {
        stop()
        if (!state.enabled) return
        if (!hasModel()) {
            state.status = "Modelo $MODEL ausente em app/src/main/assets"
            return
        }
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            state.status = "Aguardando permissão da câmera"
            activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
            return
        }
        state.status = "Iniciando câmera e MediaPipe..."
        val gen = ++generation
        val ex = Executors.newSingleThreadExecutor()
        executor = ex
        ex.execute {
            try {
                val base = BaseOptions.builder().setModelAssetPath(MODEL).build()
                val opts = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(base)
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumHands(2)
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinHandPresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .build()
                val lm = HandLandmarker.createFromOptions(activity, opts)
                if (gen != generation) {
                    lm.close()
                    return@execute
                }
                landmarker = lm
                activity.runOnUiThread { bindCamera(gen, ex) }
            } catch (t: Throwable) {
                state.status = "Erro ao iniciar MediaPipe: ${t.message}"
            }
        }
    }

    private fun bindCamera(gen: Int, ex: ExecutorService) {
        val future = ProcessCameraProvider.getInstance(activity)
        future.addListener({
            try {
                if (gen != generation) return@addListener
                val p = future.get()
                provider = p
                val selector = if (state.frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(ex) { proxy -> analyze(proxy, gen) }
                p.unbindAll()
                p.bindToLifecycle(activity, selector, analysis)
                state.status = "Câmera ativa"
            } catch (t: Throwable) {
                state.status = "Erro na câmera: ${t.message}"
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    private fun analyze(proxy: ImageProxy, gen: Int) {
        try {
            val lm = landmarker
            if (lm == null || gen != generation) return
            val src = proxy.toBitmap()
            val m = Matrix()
            m.postRotate(proxy.imageInfo.rotationDegrees.toFloat())
            val front = state.frontCamera
            if (front) m.postScale(-1f, 1f)   // selfie espelhada
            val img = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)

            var ts = SystemClock.uptimeMillis()
            if (ts <= lastTs) ts = lastTs + 1
            lastTs = ts

            val mp = BitmapImageBuilder(img).build()
            val result = try {
                lm.detectForVideo(mp, ts)
            } finally {
                mp.close()
            }

            val hands = ArrayList<HandData>()
            val all = result.landmarks()
            val sides = result.handednesses()
            for (i in all.indices) {
                val pts = all[i]
                val arr = FloatArray(63)
                for (j in 0 until 21) {
                    arr[j * 3] = pts[j].x()
                    arr[j * 3 + 1] = pts[j].y()
                    arr[j * 3 + 2] = pts[j].z()
                }
                val name = if (i < sides.size && sides[i].isNotEmpty()) sides[i][0].categoryName() else "Right"
                // MediaPipe assume imagem espelhada. Câmera traseira não é espelhada: inverte o rótulo.
                val isLeft = if (front) name == "Left" else name == "Right"
                hands.add(HandData(if (isLeft) "Esquerda" else "Direita", arr))
            }
            state.frame = HandFrame(hands, img.width.toFloat() / img.height.toFloat(), ts)
            state.status = "Câmera ativa - ${hands.size} mão(s)"

            if (img !== src) src.recycle()
            img.recycle()
        } catch (t: Throwable) {
            state.status = "Erro no rastreamento: ${t.message}"
        } finally {
            proxy.close()
        }
    }

    fun stop() {
        generation++
        try {
            provider?.unbindAll()
        } catch (e: Exception) {
        }
        val ex = executor
        val lm = landmarker
        executor = null
        landmarker = null
        ex?.execute {
            try {
                lm?.close()
            } catch (e: Exception) {
            }
        }
        ex?.shutdown()
        state.frame = null
        if (!state.enabled) state.status = "Desligado"
    }
}
