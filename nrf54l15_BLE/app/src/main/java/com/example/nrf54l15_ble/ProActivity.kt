package com.example.nrf54l15_ble

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Android analog of the iOS `ProView` — the detailed interface.
 *
 * Bottom tabs: Monitor (this screen), Recordings ([RecordingsActivity]), and Electrode
 * Map ([BodyMapView]/[BodyMapActivity]). Connect and recording are independent, exactly
 * like iOS. All BLE/TKEO/alarm/demo logic lives in [EmgManager]; CSV in [SessionManager].
 */
class ProActivity : AppCompatActivity(), EmgManager.Listener {

    private lateinit var session: SessionManager
    private lateinit var emg: EmgManager

    private lateinit var pillLeft: TextView
    private lateinit var pillRight: TextView
    private lateinit var pillRec: TextView
    private lateinit var alarmBanner: View
    private lateinit var tvSessionTitle: TextView
    private lateinit var tvSessionSub: TextView
    private lateinit var btnSession: MaterialButton
    private lateinit var btnConnect: MaterialButton
    private lateinit var chart: LineChart
    private lateinit var ch1Level: TextView
    private lateinit var ch2Level: TextView
    private lateinit var ch1Bar: View
    private lateinit var ch2Bar: View
    private lateinit var ch1Tkeo: TextView
    private lateinit var ch2Tkeo: TextView
    private lateinit var ch1Temp: TextView
    private lateinit var ch2Temp: TextView

    private val mainHandler = Handler(Looper.getMainLooper())
    private val MAX_POINTS = 300
    private var xIndex = 0f

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) emg.startScan()
        else Toast.makeText(this, "Permissions denied — cannot connect", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pro)

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayShowTitleEnabled(false)

        pillLeft       = findViewById(R.id.pillLeft)
        pillRight      = findViewById(R.id.pillRight)
        pillRec        = findViewById(R.id.pillRec)
        alarmBanner    = findViewById(R.id.alarmBanner)
        tvSessionTitle = findViewById(R.id.tvSessionTitle)
        tvSessionSub   = findViewById(R.id.tvSessionSub)
        btnSession     = findViewById(R.id.btnSession)
        btnConnect     = findViewById(R.id.btnConnect)
        chart          = findViewById(R.id.chart)
        ch1Level       = findViewById(R.id.ch1Level)
        ch2Level       = findViewById(R.id.ch2Level)
        ch1Bar         = findViewById(R.id.ch1Bar)
        ch2Bar         = findViewById(R.id.ch2Bar)
        ch1Tkeo        = findViewById(R.id.ch1Tkeo)
        ch2Tkeo        = findViewById(R.id.ch2Tkeo)
        ch1Temp        = findViewById(R.id.ch1Temp)
        ch2Temp        = findViewById(R.id.ch2Temp)

        setupChart()

        session = SessionManager(this, packageName)
        emg = EmgManager(this, session).also {
            it.setListener(this)
            it.createNotificationChannel()
        }

        btnConnect.setOnClickListener {
            if (emg.isConnected) emg.disconnect() else requestPermissionsAndScan()
        }
        btnSession.setOnClickListener { toggleSession() }
        findViewById<View>(R.id.rowRecordings).setOnClickListener {
            startActivity(Intent(this, RecordingsActivity::class.java))
        }

        setupDemoBanner()
        setupBottomNav()

        // In Demo Mode there's no hardware to pair, so start the synthetic signal
        // immediately (mirrors the iOS app auto-streaming on entry).
        if (DemoMode.isEnabled) emg.startScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        emg.setListener(null)
        emg.disconnect()
    }

    override fun onResume() {
        super.onResume()
        findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.tab_monitor
    }

    // ── Demo banner + bottom nav ───────────────────────────────────────────────
    private fun setupDemoBanner() {
        val banner = findViewById<View>(R.id.demoBanner)
        banner.visibility = if (DemoMode.isEnabled) View.VISIBLE else View.GONE
        banner.findViewById<MaterialButton>(R.id.btnExitDemo).setOnClickListener {
            FirebaseManager.signOutDemo()
            goToLogin()
        }
    }

    private fun setupBottomNav() {
        findViewById<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.tab_recordings -> { startActivity(Intent(this, RecordingsActivity::class.java)); false }
                R.id.tab_map        -> { startActivity(Intent(this, BodyMapActivity::class.java)); false }
                else -> true
            }
        }
    }

    // ── Overflow menu ──────────────────────────────────────────────────────────
    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_pro_overflow, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean = when (item.itemId) {
        R.id.action_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
        R.id.action_how_to   -> { startActivity(OnboardingActivity.intent(this, UserRole.PRO)); true }
        R.id.action_invite   -> { startActivity(Intent(this, InviteActivity::class.java)); true }
        R.id.action_sign_out -> { signOut(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun signOut() {
        emg.disconnect()
        FirebaseManager.logout()
        goToLogin()
    }

    private fun goToLogin() {
        startActivity(
            Intent(this, RouterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    // ── Connect ────────────────────────────────────────────────────────────────
    private fun requestPermissionsAndScan() {
        val needed = emg.requiredPermissions()
        if (needed.isEmpty()) emg.startScan() else permLauncher.launch(needed)
    }

    // ── Session start/stop ─────────────────────────────────────────────────────
    private fun toggleSession() {
        if (session.isRecording) stopSession() else showSessionStartDialog()
    }

    @SuppressLint("InflateParams")
    private fun showSessionStartDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_session_start, null)
        MaterialAlertDialogBuilder(this)
            .setTitle("Session Setup")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Start") { _, _ ->
                val tag = view.findViewById<android.widget.EditText>(R.id.etTag).text.toString().trim()
                val notes = view.findViewById<android.widget.EditText>(R.id.etNotes).text.toString().trim()
                val side = when (view.findViewById<RadioGroup>(R.id.rgSide).checkedRadioButtonId) {
                    R.id.sideLeft  -> "Left"
                    R.id.sideRight -> "Right"
                    else           -> "Both"
                }
                startSession(tag, side, notes)
            }
            .show()
    }

    private fun startSession(tag: String, side: String, notes: String) {
        xIndex = 0f
        clearChart()
        session.startSession(tag, side, notes)
        pillRec.visibility = View.VISIBLE
        tvSessionTitle.text = "Recording"
        tvSessionTitle.setTextColor(0xFFFF3B30.toInt())
        tvSessionSub.text = "Capturing samples to CSV"
        btnSession.text = "Stop"
        btnSession.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFF3B30.toInt())
    }

    private fun stopSession() {
        val samples = session.finalizeSession()
        val parts = session.partFiles()
        val durationMs = session.phoneMsSinceStart
        pillRec.visibility = View.GONE
        tvSessionTitle.text = "No Session"
        tvSessionTitle.setTextColor(0xFF8E8E93.toInt())
        tvSessionSub.text = "Tap Start to begin recording"
        btnSession.text = "Start"
        btnSession.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF34C759.toInt())

        startActivity(
            Intent(this, SessionSummaryActivity::class.java)
                .putExtra(SessionSummaryActivity.EXTRA_DURATION_MS, durationMs)
                .putExtra(SessionSummaryActivity.EXTRA_SAMPLES, samples)
                .putExtra(SessionSummaryActivity.EXTRA_PARTS, parts.size)
                .putExtra(SessionSummaryActivity.EXTRA_FILES, parts.map { it.absolutePath }.toTypedArray())
        )
    }

    // ── EmgManager.Listener ────────────────────────────────────────────────────
    override fun onStatus(message: String) = runOnUiThread { /* status reflected via pills */ }

    override fun onConnectionChanged(connected: Boolean) = runOnUiThread {
        setConnectionPills(connected)
        btnConnect.text = if (connected) "Disconnect" else "Connect"
        btnConnect.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (connected) 0xFFFF3B30.toInt() else 0xFF007AFF.toInt())
        if (!connected) {
            alarmBanner.visibility = View.GONE
            if (session.isRecording) stopSession()
        }
    }

    override fun onSamples(v1s: FloatArray, v2s: FloatArray) {
        mainHandler.post {
            val d1 = chart.data?.getDataSetByIndex(0) as? LineDataSet ?: return@post
            val d2 = chart.data?.getDataSetByIndex(1) as? LineDataSet ?: return@post
            for (i in v1s.indices) {
                d1.addEntry(Entry(xIndex, v1s[i]))
                d2.addEntry(Entry(xIndex, v2s[i]))
                xIndex += 1f
            }
            while (d1.entryCount > MAX_POINTS) d1.removeFirst()
            while (d2.entryCount > MAX_POINTS) d2.removeFirst()
            chart.data.notifyDataChanged()
            chart.notifyDataSetChanged()
            chart.setVisibleXRangeMaximum(MAX_POINTS.toFloat())
            chart.moveViewToX(xIndex)
            chart.invalidate()
        }
    }

    override fun onActivity(tkeo1: Double, tkeo2: Double) = runOnUiThread {
        updateChannel(ch1Level, ch1Bar, ch1Tkeo, tkeo1)
        updateChannel(ch2Level, ch2Bar, ch2Tkeo, tkeo2)
    }

    override fun onSensor(tempC: Float, humidity: Float) = runOnUiThread {
        ch1Temp.text = "🌡 %.1f°C".format(tempC)
        ch2Temp.text = "🌡 %.1f°C".format(tempC)
    }

    override fun onAlarmTriggered() = runOnUiThread {
        alarmBanner.visibility = View.VISIBLE
    }

    override fun onAlarmCleared() = runOnUiThread {
        alarmBanner.visibility = View.GONE
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────
    private fun setConnectionPills(connected: Boolean) {
        val text = if (connected) "Connected" else "Disconnected"
        val color = if (connected) 0xFF34C759.toInt() else 0xFFFF3B30.toInt()
        val tint = if (connected) 0x1F34C759 else 0x1AFF3B30
        pillLeft.text = "Left: $text"; pillLeft.setTextColor(color); pillLeft.backgroundTintList = android.content.res.ColorStateList.valueOf(tint)
        pillRight.text = "Right: $text"; pillRight.setTextColor(color); pillRight.backgroundTintList = android.content.res.ColorStateList.valueOf(tint)
    }

    private fun updateChannel(level: TextView, bar: View, tkeoText: TextView, tkeo: Double) {
        val lvl = ActivityLevel.from(tkeo)
        level.text = lvl.label
        level.setTextColor(lvl.colorInt)
        level.backgroundTintList = android.content.res.ColorStateList.valueOf((lvl.colorInt and 0x00FFFFFF) or 0x22000000)
        bar.backgroundTintList = android.content.res.ColorStateList.valueOf(lvl.colorInt)
        bar.animate().scaleX(ActivityLevel.fraction(tkeo)).setDuration(150).start()
        tkeoText.text = "%.5f V²".format(tkeo)
    }

    private fun setupChart() {
        chart.apply {
            description.isEnabled = false
            setBackgroundColor(0xFFFFFFFF.toInt())
            setDrawGridBackground(false)
            setNoDataText("Waiting for samples…")
            setNoDataTextColor(0xFF8E8E93.toInt())
            legend.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            axisRight.isEnabled = false
            xAxis.apply {
                setDrawGridLines(false); setDrawAxisLine(false)
                textColor = 0xFFAAAAAA.toInt()
            }
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = 0xFFEFEFF4.toInt()
                textColor = 0xFF8E8E93.toInt()
                setDrawAxisLine(false)
            }
        }
        val ds1 = makeSet(0xFF007AFF.toInt())
        val ds2 = makeSet(0xFFFF3B30.toInt())
        chart.data = LineData(ds1, ds2)
    }

    private fun makeSet(color: Int) = LineDataSet(ArrayList(), "").apply {
        this.color = color
        setDrawCircles(false)
        lineWidth = 1.6f
        setDrawValues(false)
        setDrawFilled(false)
        mode = LineDataSet.Mode.LINEAR
    }

    private fun clearChart() {
        (chart.data?.getDataSetByIndex(0) as? LineDataSet)?.clear()
        (chart.data?.getDataSetByIndex(1) as? LineDataSet)?.clear()
        chart.data?.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.invalidate()
    }
}
