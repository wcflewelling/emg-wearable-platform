package com.example.nrf54l15_ble

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ImageViewCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

/**
 * Android analog of the iOS `CompanionView` — a simplified live activity view.
 * One big indicator, a live waveform, and a guidance card. No recording or export.
 */
class CompanionActivity : AppCompatActivity(), EmgManager.Listener {

    private lateinit var session: SessionManager
    private lateinit var emg: EmgManager

    private lateinit var heroRing: View
    private lateinit var heroIcon: ImageView
    private lateinit var heroLabel: TextView
    private lateinit var heroDescription: TextView
    private lateinit var tvConnBadge: TextView
    private lateinit var chart: LineChart

    private val mainHandler = Handler(Looper.getMainLooper())
    private val MAX_POINTS = 240
    private var xIndex = 0f
    private var currentLevel = ActivityLevel.LOW

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> if (results.values.all { it }) emg.startScan() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_companion)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        heroRing        = findViewById(R.id.heroRing)
        heroIcon        = findViewById(R.id.heroIcon)
        heroLabel       = findViewById(R.id.heroLabel)
        heroDescription = findViewById(R.id.heroDescription)
        tvConnBadge     = findViewById(R.id.tvConnBadge)
        chart           = findViewById(R.id.chart)

        setupChart()

        session = SessionManager(this, packageName)
        emg = EmgManager(this, session).also {
            it.setListener(this)
            it.createNotificationChannel()
        }

        val banner = findViewById<View>(R.id.demoBanner)
        banner.visibility = if (DemoMode.isEnabled) View.VISIBLE else View.GONE
        banner.findViewById<MaterialButton>(R.id.btnExitDemo).setOnClickListener {
            FirebaseManager.signOutDemo(); goToLogin()
        }

        applyLevel(ActivityLevel.LOW)
    }

    override fun onResume() {
        super.onResume()
        if (!emg.isConnected) requestPermissionsAndScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        emg.setListener(null)
        emg.disconnect()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_companion_overflow, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
        R.id.action_how_to   -> { startActivity(OnboardingActivity.intent(this, UserRole.COMPANION)); true }
        R.id.action_sign_out -> { emg.disconnect(); FirebaseManager.logout(); goToLogin(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun goToLogin() {
        startActivity(
            Intent(this, RouterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun requestPermissionsAndScan() {
        val needed = emg.requiredPermissions()
        if (needed.isEmpty()) emg.startScan() else permLauncher.launch(needed)
    }

    // ── EmgManager.Listener ────────────────────────────────────────────────────
    override fun onStatus(message: String) { }

    override fun onConnectionChanged(connected: Boolean) = runOnUiThread {
        tvConnBadge.text = if (connected) "Live" else "Disconnected"
        val color = if (connected) 0xFF34C759.toInt() else 0xFFFF3B30.toInt()
        val tint = if (connected) 0x1F34C759 else 0x1AFF3B30
        tvConnBadge.setTextColor(color)
        tvConnBadge.backgroundTintList = ColorStateList.valueOf(tint)
    }

    override fun onSamples(v1s: FloatArray, v2s: FloatArray) {
        mainHandler.post {
            val ds = chart.data?.getDataSetByIndex(0) as? LineDataSet ?: return@post
            for (v in v1s) { ds.addEntry(Entry(xIndex, v)); xIndex += 1f }
            while (ds.entryCount > MAX_POINTS) ds.removeFirst()
            chart.data.notifyDataChanged()
            chart.notifyDataSetChanged()
            chart.setVisibleXRangeMaximum(MAX_POINTS.toFloat())
            chart.moveViewToX(xIndex)
            chart.invalidate()
        }
    }

    override fun onActivity(tkeo1: Double, tkeo2: Double) = runOnUiThread {
        val level = ActivityLevel.from(maxOf(tkeo1, tkeo2))
        if (level != currentLevel) applyLevel(level)
    }

    override fun onSensor(tempC: Float, humidity: Float) { }
    override fun onAlarmTriggered() { }
    override fun onAlarmCleared() { }

    // ── UI ─────────────────────────────────────────────────────────────────────
    private fun applyLevel(level: ActivityLevel) {
        currentLevel = level
        val tint = ColorStateList.valueOf(level.colorInt)
        heroRing.backgroundTintList = tint
        heroIcon.setImageResource(level.iconRes)
        ImageViewCompat.setImageTintList(heroIcon, tint)
        heroLabel.text = level.label
        heroLabel.setTextColor(level.colorInt)
        heroDescription.text = level.description
        (chart.data?.getDataSetByIndex(0) as? LineDataSet)?.apply {
            color = level.colorInt
            fillColor = level.colorInt
        }
        chart.invalidate()
    }

    private fun setupChart() {
        chart.apply {
            description.isEnabled = false
            setBackgroundColor(0xFFFFFFFF.toInt())
            setDrawGridBackground(false)
            setNoDataText("Waiting for signal…")
            setNoDataTextColor(0xFF8E8E93.toInt())
            legend.isEnabled = false
            setTouchEnabled(false)
            axisRight.isEnabled = false
            axisLeft.isEnabled = false
            xAxis.isEnabled = false
        }
        val ds = LineDataSet(ArrayList(), "").apply {
            color = 0xFFFF3B30.toInt()
            setDrawCircles(false)
            lineWidth = 1.6f
            setDrawValues(false)
            setDrawFilled(true)
            fillAlpha = 40
            fillColor = 0xFFFF3B30.toInt()
            mode = LineDataSet.Mode.LINEAR
        }
        chart.data = LineData(ds)
    }
}
