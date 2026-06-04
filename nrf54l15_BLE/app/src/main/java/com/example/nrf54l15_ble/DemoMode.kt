package com.example.nrf54l15_ble

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Android port of the iOS `DemoMode` flag + `DemoSignalGenerator`.
 *
 * When demo mode is on:
 *   - [LoginActivity] enters the app with one tap (no auth).
 *   - [FirebaseManager] synthesizes an in-memory PRO account.
 *   - [EmgManager] skips Bluetooth and feeds the UI a synthetic EMG signal.
 *
 * The flag is persisted in SharedPreferences so a demo session survives a relaunch.
 */
object DemoMode {

    private const val PREFS = "myosense_demo"
    private const val KEY_ENABLED = "enabled"

    const val DEMO_EMAIL = "demo@myosense.app"
    const val DEMO_UID = "demo-pro-uid"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    var isEnabled: Boolean
        get() = ::prefs.isInitialized && prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    fun enable() { isEnabled = true }
    fun disable() { isEnabled = false }
}

/**
 * Ticks at 20 Hz on the main looper. Each tick synthesizes 8 samples (~160 Hz) and
 * hands them to [EmgManager.pushDemoSamples]. The signal mimics a single-ended ADC
 * reading a surface-EMG electrode: a ~1.8 V mid-rail baseline, small resting ripple,
 * and random ~0.3–0.8 s activation bursts of 80 Hz oscillation.
 */
class DemoSignalGenerator(private val manager: EmgManager) {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private var t = 0.0
    private var burstCh1Until = 0.0
    private var burstCh2Until = 0.0

    private val baseline = 1.80
    private val restingAmp = 0.030
    private val burstAmp = 0.400
    private val burstFreq = 80.0

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            tick()
            handler.postDelayed(this, 50)
        }
    }

    fun start() {
        if (running) return
        running = true
        handler.post(tickRunnable)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tickRunnable)
        t = 0.0
        burstCh1Until = 0.0
        burstCh2Until = 0.0
    }

    private fun tick() {
        val samplesPerTick = 8
        val dt = 1.0 / 160.0

        if (Random.nextDouble() < 0.017) burstCh1Until = t + Random.nextDouble(0.30, 0.80)
        if (Random.nextDouble() < 0.017) burstCh2Until = t + Random.nextDouble(0.30, 0.80)

        val v1 = FloatArray(samplesPerTick)
        val v2 = FloatArray(samplesPerTick)

        for (i in 0 until samplesPerTick) {
            t += dt
            val rest1 = restingAmp * (sin(2 * Math.PI * 1.7 * t) + 0.4 * Random.nextDouble(-1.0, 1.0))
            val rest2 = restingAmp * (sin(2 * Math.PI * 1.9 * t + 0.7) + 0.4 * Random.nextDouble(-1.0, 1.0))
            val burst1 = burstEnvelope(t, burstCh1Until) * sin(2 * Math.PI * burstFreq * t)
            val burst2 = burstEnvelope(t, burstCh2Until) * sin(2 * Math.PI * (burstFreq + 5) * t + 1.1)
            v1[i] = (baseline + rest1 + burst1).toFloat()
            v2[i] = (baseline + rest2 + burst2).toFloat()
        }

        val inBurst1 = burstCh1Until > t
        val inBurst2 = burstCh2Until > t
        val tkeo1 = if (inBurst1) Random.nextDouble(0.00080, 0.00180) else Random.nextDouble(0.0, 0.00005)
        val tkeo2 = if (inBurst2) Random.nextDouble(0.00080, 0.00180) else Random.nextDouble(0.0, 0.00005)
        val temp = (27.0 + sin(t / 30.0) * 0.5).toFloat()

        manager.pushDemoSamples(v1, v2, tkeo1, tkeo2, temp)
    }

    private fun burstEnvelope(t: Double, end: Double): Double {
        if (end <= t) return 0.0
        val remaining = end - t
        val approxLen = 0.55
        val pos = (1 - remaining / approxLen).coerceIn(0.0, 1.0)
        val env = 0.5 * (1 - cos(2 * Math.PI * pos))
        return burstAmp * env
    }
}
