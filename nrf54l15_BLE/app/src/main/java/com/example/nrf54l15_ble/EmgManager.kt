package com.example.nrf54l15_ble

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.abs

/**
 * Android analog of the iOS `EMGManager`.
 *
 * Owns: BLE scan + GATT lifecycle, packet parsing, per-channel TKEO, the low-signal
 * alarm, the synthetic Demo-Mode signal, and the streaming hand-off to [SessionManager].
 *
 * The UI talks to this through [Listener]; the manager never touches Views. Channels are
 * symmetric (CH1 = AIN0, CH2 = AIN1) — same gain, reference, and TKEO pipeline.
 */
class EmgManager(
    private val context: Context,
    private val session: SessionManager
) {

    // ── BLE UUIDs (must stay in sync with firmware) ──────────────────────────
    private val SERVICE_UUID     = UUID.fromString("75ab9000-cd33-44dd-ba7e-2037855136d4")
    private val ADC_CHAR_UUID    = UUID.fromString("27314856-fe77-45f0-b4f8-fed858ece431")
    private val SENSOR_CHAR_UUID = UUID.fromString("27314857-fe77-45f0-b4f8-fed858ece431")
    private val CCCD_UUID        = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Hardcoded firmware advertising name; must match `prj.conf`. */
    val deviceName: String get() = "WYATT nRF54"

    interface Listener {
        fun onStatus(message: String)
        fun onConnectionChanged(connected: Boolean)
        /** Raw per-sample voltages for the live chart (iOS `graphData1/2`). */
        fun onSamples(v1s: FloatArray, v2s: FloatArray)
        /** Latest TKEO energy per channel, drives activity level + bars (iOS `tkeoValue1/2`). */
        fun onActivity(tkeo1: Double, tkeo2: Double)
        fun onSensor(tempC: Float, humidity: Float)
        fun onAlarmTriggered()
        fun onAlarmCleared()
    }

    private var listener: Listener? = null
    fun setListener(l: Listener?) { listener = l }

    // ── State ────────────────────────────────────────────────────────────────
    private var gatt: BluetoothGatt? = null
    private var adcChar: BluetoothGattCharacteristic? = null
    private var sensorChar: BluetoothGattCharacteristic? = null
    var isConnected: Boolean = false
        private set

    /** "Disconnected" | "Scanning..." | "Connected" — mirrors iOS `status`. */
    var status: String = "Disconnected"
        private set

    private val tkeo1 = Tkeo()
    private val tkeo2 = Tkeo()

    private var lowActivitySinceMs = 0L
    private var alarmActive = false

    private var demoGenerator: DemoSignalGenerator? = null

    // ── Permissions helper ───────────────────────────────────────────────────
    fun requiredPermissions(): Array<String> {
        if (DemoMode.isEnabled) return emptyArray()
        val out = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPerm(Manifest.permission.BLUETOOTH_SCAN))    out += Manifest.permission.BLUETOOTH_SCAN
            if (!hasPerm(Manifest.permission.BLUETOOTH_CONNECT)) out += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            if (!hasPerm(Manifest.permission.ACCESS_FINE_LOCATION))
                out += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasPerm("android.permission.POST_NOTIFICATIONS")) {
            out += "android.permission.POST_NOTIFICATIONS"
        }
        return out.toTypedArray()
    }

    private fun hasPerm(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    // ── Connect / Disconnect (recording is controlled separately via SessionManager) ──
    @SuppressLint("MissingPermission")
    fun startScan() {
        tkeo1.reset(); tkeo2.reset()
        lowActivitySinceMs = 0L
        alarmActive = false

        if (DemoMode.isEnabled) {
            startDemoSignal()
            return
        }

        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bm.adapter
        if (adapter == null || !adapter.isEnabled) {
            setStatus("Bluetooth not available / disabled")
            return
        }
        setStatus("Scanning...")
        listener?.onStatus("Scanning for $deviceName…")
        val filter   = ScanFilter.Builder().setDeviceName(deviceName).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val scanner  = adapter.bluetoothLeScanner
        scanner.startScan(listOf(filter), settings, scanCallback)
        mainHandler.postDelayed({
            try { scanner.stopScan(scanCallback) } catch (_: Exception) { }
            if (!isConnected) {
                setStatus("Disconnected")
                listener?.onStatus("Device not found — tap Connect to retry")
            }
        }, 10_000)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopDemoSignal()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        isConnected = false
        setStatus("Disconnected")
        listener?.onConnectionChanged(false)
        if (alarmActive) { alarmActive = false; listener?.onAlarmCleared() }
    }

    // ── Demo signal ────────────────────────────────────────────────────────────
    fun startDemoSignal() {
        if (demoGenerator != null) return
        isConnected = true
        setStatus("Connected")
        listener?.onConnectionChanged(true)
        listener?.onStatus("Demo signal streaming")
        demoGenerator = DemoSignalGenerator(this).also { it.start() }
    }

    fun stopDemoSignal() {
        demoGenerator?.stop()
        demoGenerator = null
    }

    /** Called by [DemoSignalGenerator] on the main thread, ~20×/s with 8 samples each. */
    fun pushDemoSamples(v1s: FloatArray, v2s: FloatArray, tkeo1v: Double, tkeo2v: Double, temp: Float) {
        // Synthetic per-sample TKEO arrays so recorded CSVs look like a real session.
        val p1s = arrayOfNulls<Float>(v1s.size).also { it.fill(tkeo1v.toFloat()) }
        val p2s = arrayOfNulls<Float>(v2s.size).also { it.fill(tkeo2v.toFloat()) }
        session.writeSamples(0L, session.phoneMsSinceStart, v1s, v2s, p1s, p2s)

        evaluateAlarm(tkeo1v, tkeo2v)
        listener?.onSamples(v1s, v2s)
        listener?.onActivity(tkeo1v, tkeo2v)
        listener?.onSensor(temp, 0f)
    }

    // ── Notification channel (call once from Activity.onCreate) ──────────────
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID, "Sensor Events", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "BLE disconnect and low-signal alerts" }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(chan)
        }
    }

    // ── BLE callbacks ──────────────────────────────────────────────────────────
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            try { bm.adapter.bluetoothLeScanner.stopScan(this) } catch (_: Exception) { }
            connect(result.device)
        }
        override fun onScanFailed(errorCode: Int) {
            listener?.onStatus("Scan failed: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        listener?.onStatus("Connecting to ${device.address}…")
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isConnected = true
                    setStatus("Connected")
                    listener?.onStatus("Connected — negotiating MTU…")
                    listener?.onConnectionChanged(true)
                    gatt.requestMtu(100)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val wasConnected = isConnected
                    isConnected = false
                    setStatus("Disconnected")
                    listener?.onConnectionChanged(false)
                    if (wasConnected) showDisconnectNotification()
                    gatt.close()
                    this@EmgManager.gatt = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(TAG, "onMtuChanged mtu=$mtu status=$status")
            listener?.onStatus("MTU=$mtu — discovering services…")
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status"); return
            }
            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                listener?.onStatus("Service not found on device"); return
            }
            adcChar    = service.getCharacteristic(ADC_CHAR_UUID)
            sensorChar = service.getCharacteristic(SENSOR_CHAR_UUID)
            enableNotify(gatt, adcChar)
            mainHandler.postDelayed({ enableNotify(gatt, sensorChar) }, 300)
            listener?.onStatus("Connected & streaming")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            when (characteristic.uuid) {
                ADC_CHAR_UUID    -> parseAdcPacket(value)
                SENSOR_CHAR_UUID -> parseSensorPacket(value)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            onCharacteristicChanged(gatt, characteristic, characteristic.value ?: return)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotify(gatt: BluetoothGatt, char: BluetoothGattCharacteristic?) {
        char ?: return
        gatt.setCharacteristicNotification(char, true)
        val cccd = char.getDescriptor(CCCD_UUID) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(cccd)
        }
    }

    // ── Packet parsers ───────────────────────────────────────────────────────
    private fun parseAdcPacket(data: ByteArray) {
        if (data.size < 4 + BATCH_SAMPLES * 4) return
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val tsUs = bb.int.toLong() and 0xFFFFFFFFL
        val phoneMs = session.phoneMsSinceStart

        val v1s = FloatArray(BATCH_SAMPLES)
        val v2s = FloatArray(BATCH_SAMPLES)
        val p1s = arrayOfNulls<Float>(BATCH_SAMPLES)
        val p2s = arrayOfNulls<Float>(BATCH_SAMPLES)

        for (i in 0 until BATCH_SAMPLES) {
            val r1 = bb.short.toInt() and 0xFFFF
            val r2 = bb.short.toInt() and 0xFFFF
            v1s[i] = r1 * ADC_VOLTS_PER_COUNT
            v2s[i] = r2 * ADC_VOLTS_PER_COUNT
            p1s[i] = tkeo1.push(v1s[i])
            p2s[i] = tkeo2.push(v2s[i])
        }

        session.writeSamples(tsUs, phoneMs, v1s, v2s, p1s, p2s)

        val cur1 = meanAbs(p1s)
        val cur2 = meanAbs(p2s)
        evaluateAlarm(cur1, cur2)

        listener?.onSamples(v1s, v2s)
        listener?.onActivity(cur1, cur2)
    }

    private fun parseSensorPacket(data: ByteArray) {
        if (data.size < 12) return
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        bb.int  // skip timestamp
        val t = bb.float
        val h = bb.float
        listener?.onSensor(t, h)
    }

    private fun meanAbs(p: Array<Float?>): Double {
        val vals = p.filterNotNull()
        if (vals.isEmpty()) return 0.0
        return vals.map { abs(it).toDouble() }.average()
    }

    // ── Low-signal alarm ───────────────────────────────────────────────────────
    private fun evaluateAlarm(tkeo1v: Double, tkeo2v: Double) {
        val low = tkeo1v < LOW_ALARM_THRESHOLD && tkeo2v < LOW_ALARM_THRESHOLD
        if (low) {
            if (lowActivitySinceMs == 0L) {
                lowActivitySinceMs = System.currentTimeMillis()
            } else if (!alarmActive &&
                System.currentTimeMillis() - lowActivitySinceMs > LOW_ALARM_DURATION_MS
            ) {
                alarmActive = true
                triggerLowActivityAlarm()
            }
        } else {
            lowActivitySinceMs = 0L
            if (alarmActive) {
                alarmActive = false
                listener?.onAlarmCleared()
            }
        }
    }

    private fun triggerLowActivityAlarm() {
        listener?.onAlarmTriggered()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Ringtone failed", e)
        }
        try {
            @Suppress("DEPRECATION")
            val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 200, 600), -1))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(longArrayOf(0, 600, 200, 600), -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibrate failed", e)
        }
    }

    private fun showDisconnectNotification() {
        if (Build.VERSION.SDK_INT >= 33 && !hasPerm("android.permission.POST_NOTIFICATIONS")) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Sensor Disconnected")
            .setContentText("$deviceName lost connection")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(1001, notification)
    }

    private fun setStatus(s: String) {
        status = s
        listener?.onStatus(s)
    }

    companion object {
        private const val TAG = "EmgManager"
        private const val CHANNEL_ID = "ble_events"

        /** Must match firmware BATCH_SIZE. 8 samples → 36-byte packet, 125 notifs/s at 1 kHz. */
        const val BATCH_SAMPLES = 8

        /**
         * nRF54L15 SAADC conversion: gain=1/4, internal ref=1.024 V bandgap, 14-bit.
         * Full-scale = 1.024 / 0.25 = 4.096 V → 16384 codes → 250 µV / LSB.
         */
        const val ADC_VOLTS_PER_COUNT = (1.024f / 0.25f) / 16384f

        /** Matches iOS `ActivityLevel.lowThreshold = 5e-5 V²`. */
        const val LOW_ALARM_THRESHOLD = 5e-5
        const val LOW_ALARM_DURATION_MS = 30_000L
    }
}
