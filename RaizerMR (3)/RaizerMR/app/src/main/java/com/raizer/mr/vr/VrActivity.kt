package com.raizer.mr.vr

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.raizer.mr.Prefs

class VrActivity : ComponentActivity(), UiBridge {
    private lateinit var glView: VrSurfaceView
    private lateinit var head: HeadTracker
    private lateinit var tracker: HandTracker
    private lateinit var webRoot: FrameLayout
    private val hands = HandsState()
    private val gains = floatArrayOf(0.8f, 1.0f, 1.3f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        hands.frontCamera = Prefs.handFront(this)
        hands.gain = gains[Prefs.handSens(this)]
        hands.pinchOn = Prefs.pinchOn(this)
        hands.pinchOff = Prefs.pinchOff(this)
        hands.openRecenter = Prefs.handRecenter(this)

        head = HeadTracker(this)
        tracker = HandTracker(this, hands)
        hands.onEnabledChange = { runOnUiThread { tracker.apply() } }
        hands.onCameraChange = { runOnUiThread { if (hands.enabled) tracker.start() } }

        val app = intent.getStringExtra("app")?.let { runCatching { AppId.valueOf(it) }.getOrNull() }

        // WebViews ficam ATRÁS da superfície GL: renderizam normalmente, mas quem aparece é a textura 3D.
        val root = FrameLayout(this)
        webRoot = FrameLayout(this)
        webRoot.clipChildren = false
        root.addView(webRoot, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        glView = VrSurfaceView(this, head, app, this, hands)
        root.addView(glView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        immersive()
        glView.onResume()
        head.start()
        for (i in 0 until webRoot.childCount) (webRoot.getChildAt(i) as? WebView)?.onResume()
    }

    override fun onPause() {
        for (i in 0 until webRoot.childCount) (webRoot.getChildAt(i) as? WebView)?.onPause()
        head.stop()
        glView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        tracker.stop()
        for (i in webRoot.childCount - 1 downTo 0) {
            (webRoot.getChildAt(i) as? WebView)?.destroy()
        }
        webRoot.removeAllViews()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == HandTracker.REQ_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tracker.start()
            } else {
                hands.status = "Permissão da câmera negada"
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    // ---------- UiBridge ----------

    override fun runOnUi(r: Runnable) {
        runOnUiThread(r)
    }

    override fun attachWebView(w: Int, h: Int): WebView {
        val wv = WebView(this)
        webRoot.addView(wv, FrameLayout.LayoutParams(w, h))
        return wv
    }

    override fun detachWebView(v: WebView) {
        webRoot.removeView(v)
        v.stopLoading()
        v.destroy()
    }

    override fun promptText(title: String, initial: String, onResult: (String) -> Unit) {
        runOnUiThread {
            val et = EditText(this)
            et.setText(initial)
            et.setSelectAllOnFocus(true)
            et.setSingleLine(true)
            AlertDialog.Builder(this)
                .setTitle(title)
                .setView(et)
                .setPositiveButton("Ir") { _, _ -> onResult(et.text.toString()) }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    override fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun requestLocationPermission() {
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 78)
    }
}
