package com.example.nrf54l15_ble

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android analog of the iOS `SessionManager`.
 *
 * Owns the lifetime of a *recording session* independent of the BLE connection
 * (you can connect without recording, like iOS):
 *  - one or more part files (rolled every [PART_MAX_MS])
 *  - a streaming CSV writer (no in-memory accumulation)
 *  - sample index across all parts
 *
 * Threading: writes are guarded by [lock]; safe to call [writeSamples] from the BLE
 * callback or demo thread. UI threads call [startSession] / [finalizeSession].
 */
class SessionManager(
    private val appContext: Context,
    private val packageName: String
) {
    private val lock = Object()
    private var writer: BufferedWriter? = null
    private val parts = mutableListOf<File>()
    private var partStartMs = 0L
    private var sampleIdx = 0L
    private var sessionStartMs = 0L

    private var sessionTag = ""
    private var side = "Both"
    private var notes = ""

    val sampleCount: Long get() = sampleIdx
    val partCount: Int get() = parts.size
    val isRecording: Boolean get() = synchronized(lock) { writer != null }
    val startedAtMs: Long get() = sessionStartMs
    /** Phone-relative milliseconds since session start; used as a CSV column. */
    val phoneMsSinceStart: Long get() =
        if (sessionStartMs == 0L) 0L else System.currentTimeMillis() - sessionStartMs

    fun startSession(sessionTag: String, side: String, notes: String) {
        synchronized(lock) {
            this.sessionTag = sessionTag
            this.side = side
            this.notes = notes
            parts.clear()
            sampleIdx = 0L
            sessionStartMs = System.currentTimeMillis()
            openNewPartFile()
        }
    }

    /** Returns total sample count and closes the writer. Safe to call repeatedly. */
    fun finalizeSession(): Long {
        val count: Long
        synchronized(lock) {
            writer?.flush()
            writer?.close()
            writer = null
            count = sampleIdx
        }
        if (count > 0L) {
            Log.i(TAG, "Session finalized: $count samples across ${parts.size} part file(s)")
        }
        return count
    }

    fun writeSamples(
        tsUs: Long,
        phoneMs: Long,
        v1s: FloatArray,
        v2s: FloatArray,
        p1s: Array<Float?>,
        p2s: Array<Float?>
    ) {
        synchronized(lock) {
            if (writer == null) return   // not recording → no-op
            if (System.currentTimeMillis() - partStartMs >= PART_MAX_MS) {
                openNewPartFile()
            }
            val ww = writer ?: return
            for (i in v1s.indices) {
                ww.write(sampleIdx.toString());           ww.write(",")
                ww.write(tsUs.toString());                ww.write(",")
                ww.write(phoneMs.toString());             ww.write(",")
                ww.write("%.6f".format(v1s[i]));          ww.write(",")
                ww.write("%.6f".format(v2s[i]));          ww.write(",")
                ww.write(p1s[i]?.let { "%.6e".format(it) } ?: ""); ww.write(",")
                ww.write(p2s[i]?.let { "%.6e".format(it) } ?: ""); ww.write("\n")
                sampleIdx++
            }
            if (sampleIdx % 8000L < v1s.size) ww.flush()
        }
    }

    fun partFiles(): List<File> = synchronized(lock) { parts.toList() }

    // Must be called inside [lock].
    private fun openNewPartFile() {
        writer?.flush()
        writer?.close()
        val partNum = parts.size + 1
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = sessionsDir(appContext)
        val file = File(dir, "myosense_${stamp}_part${"$partNum".padStart(3, '0')}.csv")
        parts.add(file)
        partStartMs = System.currentTimeMillis()
        val w = BufferedWriter(FileWriter(file))
        w.write("# MyoSense Recording\n")
        w.write("# Session Tag: $sessionTag\n")
        w.write("# Notes: $notes\n")
        w.write("# Side: $side\n")
        w.write("# Part: $partNum\n")
        w.write("# StartTime: $stamp\n")
        w.write("idx,dev_ts_us,phone_ms,v1_volts,v2_volts,tkeo1,tkeo2\n")
        w.flush()
        writer = w
        Log.i(TAG, "Opened part $partNum: ${file.name}")
    }

    companion object {
        private const val TAG = "SessionManager"
        /** 1 h per file ≈ 180 MB at 1 kHz dual-channel. */
        private const val PART_MAX_MS = 3_600_000L

        /** All recordings live here (analogous to iOS Documents/Sessions/). */
        fun sessionsDir(context: Context): File {
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            val dir = File(base, "Sessions")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        /** Newest-first list of all recorded CSV files on the device. */
        fun listAllFiles(context: Context): List<File> =
            sessionsDir(context).listFiles { f -> f.isFile && f.extension == "csv" }
                ?.sortedByDescending { it.name }
                ?: emptyList()

        /** Fire a chooser to share [files] via FileProvider. Returns false if none. */
        fun shareFiles(activity: Activity, packageName: String, files: List<File>): Boolean {
            val existing = files.filter { it.exists() }
            if (existing.isEmpty()) return false
            val uris = ArrayList(existing.map { file ->
                FileProvider.getUriForFile(activity, "$packageName.fileprovider", file)
            })
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                    putExtra(Intent.EXTRA_SUBJECT, "MyoSense Session — ${existing[0].nameWithoutExtension}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "text/csv"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    putExtra(Intent.EXTRA_SUBJECT, "MyoSense Session — ${existing.size} files")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            activity.startActivity(Intent.createChooser(intent, "Share ${existing.size} file(s)…"))
            return true
        }
    }
}
