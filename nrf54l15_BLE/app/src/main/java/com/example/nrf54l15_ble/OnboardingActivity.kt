package com.example.nrf54l15_ble

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar

/**
 * Android analog of the iOS `OnboardingView` — a scrollable how-to tour. The page set
 * depends on the user's role (Pro guide vs Companion guide).
 */
class OnboardingActivity : AppCompatActivity() {

    private data class Page(val iconRes: Int, val color: Int, val title: String, val body: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val role = intent.getStringExtra(EXTRA_ROLE)?.let { UserRole.valueOf(it) } ?: UserRole.PRO
        supportActionBar?.title = if (role == UserRole.PRO) "Pro Guide" else "Companion Guide"

        val pages = if (role == UserRole.PRO) proPages() else companionPages()
        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = Adapter(pages)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun proPages() = listOf(
        Page(R.drawable.ic_monitor, BLUE, "Welcome to MyoSense",
            "MyoSense pairs with a Bluetooth muscle-signal sensor and shows you the live waveform in real time. Sessions can be recorded locally and exported as CSV for Excel, MATLAB, or Python. Demo Mode runs everything with a synthetic signal — no sensor needed."),
        Page(R.drawable.ic_monitor, BLUE, "Connecting your sensor",
            "Power on your Bluetooth sensor and tap Connect at the bottom of the Monitor screen. The status pills at the top turn green once connected."),
        Page(R.drawable.ic_warning, RED, "Starting a session",
            "Tap Start in the session banner to begin recording. Optionally add a tag and notes so you remember the context later. The red REC pill confirms you're recording."),
        Page(R.drawable.ic_doc, ORANGE, "Stopping a session",
            "Tap Stop to end the session. MyoSense closes the local CSV files and shows a summary with duration, sample count, and a share sheet."),
        Page(R.drawable.ic_share, GREEN, "Exporting data",
            "From the summary, tap Export CSV to share the file. You can also revisit any past session from the Recordings tab. Open the CSV in Excel, or load it in pandas.read_csv() / MATLAB readtable()."),
        Page(R.drawable.ic_body, PURPLE, "Electrode Map",
            "The Electrode Map tab shows where to place the sensor for common muscle groups, with the movements each one drives."),
        Page(R.drawable.ic_resting, GRAY, "Your data stays yours",
            "Live samples stay on your device. MyoSense is a wellness and biofeedback tool, not a medical device.")
    )

    private fun companionPages() = listOf(
        Page(R.drawable.ic_monitor, BLUE, "Welcome to MyoSense",
            "This simplified view shows a live activity level from a Bluetooth muscle-signal sensor. There's nothing to set up — just follow the indicator."),
        Page(R.drawable.ic_strong, GREEN, "High — Strong activation",
            "When the indicator is green and shows High, the muscle is firing strongly. This is the active state during effort."),
        Page(R.drawable.ic_light, YELLOW, "Moderate — Light activation",
            "Yellow means partial activation. Typical of warm-ups, light effort, or holding a position."),
        Page(R.drawable.ic_resting, BLUE, "Low — Resting",
            "Blue means the muscle is at rest. This is the baseline state between efforts — completely normal."),
        Page(R.drawable.ic_warning, GRAY, "Connection",
            "If the connection indicator goes red, the sensor has dropped Bluetooth. Power-cycle the sensor or move closer to the phone.")
    )

    private inner class Adapter(val pages: List<Page>) : RecyclerView.Adapter<Adapter.VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding, parent, false)
            return VH(v)
        }
        override fun getItemCount() = pages.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = pages[position]
            holder.title.text = p.title
            holder.body.text = p.body
            holder.counter.text = "${position + 1}/${pages.size}"
            holder.icon.setImageResource(p.iconRes)
            ImageViewCompat.setImageTintList(holder.icon, ColorStateList.valueOf(p.color))
            holder.iconBg.backgroundTintList = ColorStateList.valueOf((p.color and 0x00FFFFFF) or 0x22000000)
        }
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.icon)
            val iconBg: View = v.findViewById(R.id.iconBg)
            val title: TextView = v.findViewById(R.id.title)
            val counter: TextView = v.findViewById(R.id.counter)
            val body: TextView = v.findViewById(R.id.body)
        }
    }

    companion object {
        private const val EXTRA_ROLE = "role"
        private const val BLUE = 0xFF007AFF.toInt()
        private const val RED = 0xFFFF3B30.toInt()
        private const val GREEN = 0xFF34C759.toInt()
        private const val ORANGE = 0xFFFF9500.toInt()
        private const val YELLOW = 0xFFFFCC00.toInt()
        private const val PURPLE = 0xFFAF52DE.toInt()
        private const val GRAY = 0xFF8E8E93.toInt()

        fun intent(context: Context, role: UserRole): Intent =
            Intent(context, OnboardingActivity::class.java).putExtra(EXTRA_ROLE, role.name)
    }
}
