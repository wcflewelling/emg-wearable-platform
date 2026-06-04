package com.example.nrf54l15_ble

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * Android analog of the iOS `DataListView` / `DataManagementView`. Lists every CSV
 * recording on the device (the "files in a tab" the app saves to) and lets you share
 * or delete them. Opened from the Pro Recordings tab and the Monitor data row.
 */
class RecordingsActivity : AppCompatActivity() {

    private lateinit var list: RecyclerView
    private lateinit var emptyState: View
    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recordings)

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        list = findViewById(R.id.list)
        emptyState = findViewById(R.id.emptyState)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val files = SessionManager.listAllFiles(this)
        adapter.submit(files)
        emptyState.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE
        supportActionBar?.title = "Recordings (${files.size})"
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_recordings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.action_share_all -> {
            if (!SessionManager.shareFiles(this, packageName, adapter.items)) {
                Toast.makeText(this, "No files to share.", Toast.LENGTH_SHORT).show()
            }
            true
        }
        R.id.action_delete_all -> { confirmDeleteAll(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun confirmDeleteAll() {
        if (adapter.items.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete all recordings?")
            .setMessage("This permanently deletes all ${adapter.items.size} file(s) from this device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete Everything") { _, _ ->
                adapter.items.forEach { it.delete() }
                reload()
            }
            .show()
    }

    private fun confirmDelete(file: File) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete recording?")
            .setMessage(file.name)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> file.delete(); reload() }
            .show()
    }

    // ── Adapter ─────────────────────────────────────────────────────────────────
    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        val items = mutableListOf<File>()

        fun submit(files: List<File>) {
            items.clear(); items.addAll(files); notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recording, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val file = items[position]
            holder.name.text = file.name
            holder.meta.text = "${sizeString(file)} · ${dateString(file)}"
            holder.share.setOnClickListener {
                SessionManager.shareFiles(this@RecordingsActivity, packageName, listOf(file))
            }
            holder.itemView.setOnLongClickListener { confirmDelete(file); true }
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvName)
            val meta: TextView = v.findViewById(R.id.tvMeta)
            val share: ImageView = v.findViewById(R.id.btnShare)
        }
    }

    private fun sizeString(f: File): String {
        val b = f.length()
        return when {
            b > 1_000_000 -> "%.1f MB".format(b / 1_000_000.0)
            b > 1_000     -> "%.0f KB".format(b / 1_000.0)
            else          -> "$b B"
        }
    }

    private fun dateString(f: File): String =
        DateFormat.getDateFormat(this).format(f.lastModified()) + " " +
            DateFormat.getTimeFormat(this).format(f.lastModified())
}
