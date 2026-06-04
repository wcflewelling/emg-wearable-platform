package com.example.nrf54l15_ble

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.io.File

/**
 * Android analog of the iOS `SessionSummaryView` — shown after a recording stops.
 */
class SessionSummaryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_summary)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
        val samples    = intent.getLongExtra(EXTRA_SAMPLES, 0L)
        val parts      = intent.getIntExtra(EXTRA_PARTS, 0)
        val filePaths  = intent.getStringArrayExtra(EXTRA_FILES) ?: emptyArray()
        val files      = filePaths.map { File(it) }.filter { it.exists() }

        findViewById<TextView>(R.id.tvDuration).text = durationString(durationMs)
        findViewById<TextView>(R.id.tvSamples).text = samples.toString()
        findViewById<TextView>(R.id.tvSaved).text =
            if (files.isEmpty()) "No files" else "$parts file(s) on device ✓"

        val export = findViewById<MaterialButton>(R.id.btnExport)
        export.isEnabled = files.isNotEmpty()
        export.setOnClickListener {
            if (!SessionManager.shareFiles(this, packageName, files)) {
                Toast.makeText(this, "No files to export.", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<MaterialButton>(R.id.btnDone).setOnClickListener { finish() }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun durationString(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else  -> "${s}s"
        }
    }

    companion object {
        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_SAMPLES = "samples"
        const val EXTRA_PARTS = "parts"
        const val EXTRA_FILES = "files"
    }
}
