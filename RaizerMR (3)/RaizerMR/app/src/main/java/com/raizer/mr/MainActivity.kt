package com.raizer.mr

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import com.raizer.mr.vr.AppId
import com.raizer.mr.vr.VrActivity
import kotlin.math.max
import kotlin.math.min

/** Tela inicial (landscape): logo, botões, bateria, desempenho e informações do aparelho. */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 5000)
        }
    }

    private lateinit var tvBattery: TextView
    private lateinit var pbBattery: ProgressBar
    private lateinit var tvPerf: TextView
    private lateinit var tvDevice: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvBattery = findViewById(R.id.tvBattery)
        pbBattery = findViewById(R.id.pbBattery)
        tvPerf = findViewById(R.id.tvPerf)
        tvDevice = findViewById(R.id.tvDevice)
        tvDevice.text = deviceInfo()

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startActivity(Intent(this, VrActivity::class.java))
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { showSettings() }
        findViewById<Button>(R.id.btnApps).setOnClickListener { showApps() }
        findViewById<Button>(R.id.btnAbout).setOnClickListener { showAbout() }
    }

    override fun onResume() {
        super.onResume()
        immersive()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    private fun refreshStatus() {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        if (lvl >= 0 && scale > 0) {
            val pct = lvl * 100 / scale
            tvBattery.text = "Bateria: $pct%" + if (charging) " (carregando)" else ""
            pbBattery.progress = pct
        } else {
            tvBattery.text = "Bateria: indisponível"
            pbBattery.progress = 0
        }

        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val totalMb = mi.totalMem / 1048576
        val availMb = mi.availMem / 1048576
        val rec = when {
            totalMb < 3500 -> 0
            totalMb < 7000 -> 1
            else -> 2
        }
        tvPerf.text = "Memória livre: $availMb / $totalMb MB\n" +
                "Núcleos de CPU: ${Runtime.getRuntime().availableProcessors()}\n" +
                "Perfil recomendado: ${Prefs.QUALITY_LABELS[rec]}\n" +
                "Perfil atual: ${Prefs.QUALITY_LABELS[Prefs.quality(this)]}"
    }

    @Suppress("DEPRECATION")
    private fun deviceInfo(): String {
        val dm = resources.displayMetrics
        val hz = windowManager.defaultDisplay.refreshRate.toInt()
        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val head = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null ||
                sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return "${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "Tela: ${max(dm.widthPixels, dm.heightPixels)}x${min(dm.widthPixels, dm.heightPixels)} @ $hz Hz\n" +
                "OpenGL ES: ${am.deviceConfigurationInfo.glEsVersion}\n" +
                "Rastreamento de cabeça (giroscópio): ${if (head) "sim" else "não"}"
    }

    private fun showSettings() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(pad, pad / 2, pad, 0)

        val sw = Switch(this)
        sw.text = "Modo estéreo (para óculos VR)"
        sw.isChecked = Prefs.stereo(this)
        sw.setOnCheckedChangeListener { _, checked -> Prefs.setStereo(this, checked) }
        layout.addView(sw)

        val title = TextView(this)
        title.text = "\nQualidade gráfica"
        layout.addView(title)

        val rg = RadioGroup(this)
        for (i in Prefs.QUALITY_LABELS.indices) {
            val rb = RadioButton(this)
            rb.id = 100 + i
            rb.text = Prefs.QUALITY_LABELS[i]
            rg.addView(rb)
        }
        rg.check(100 + Prefs.quality(this))
        rg.setOnCheckedChangeListener { _, id ->
            Prefs.setQuality(this, id - 100)
            refreshStatus()
        }
        layout.addView(rg)

        AlertDialog.Builder(this)
            .setTitle("Configurações")
            .setView(layout)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showApps() {
        val apps = AppId.values().filter { it.showInLauncher }
        AlertDialog.Builder(this)
            .setTitle("Abrir no ambiente VR")
            .setItems(apps.map { it.label }.toTypedArray()) { _, which ->
                val intent = Intent(this, VrActivity::class.java)
                intent.putExtra("app", apps[which].name)
                startActivity(intent)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("RaizerMR 0.1.0")
            .setMessage(
                "Sistema de realidade mista para Android.\n\n" +
                        "Renderização: OpenGL ES 2.0\n" +
                        "Rastreamento de cabeça: sensor de rotação (giroscópio)\n" +
                        "Janelas 3D fixas no espaço do mundo.\n\n" +
                        "Projeto independente, sem relação com a Meta."
            )
            .setPositiveButton("OK", null)
            .show()
    }
}
