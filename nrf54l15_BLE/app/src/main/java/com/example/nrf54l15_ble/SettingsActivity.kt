package com.example.nrf54l15_ble

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Android analog of the iOS `SettingsView` — account info, Demo Mode toggle, about,
 * and sign out. Toggling Demo Mode cleanly re-routes through [RouterActivity].
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var switchDemo: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<TextView>(R.id.tvEmail).text = FirebaseManager.currentEmail ?: "Not signed in"
        findViewById<TextView>(R.id.tvRole).text =
            FirebaseManager.currentRole.name.lowercase().replaceFirstChar { it.uppercase() }
        findViewById<TextView>(R.id.tvVersion).text = appVersion()
        findViewById<TextView>(R.id.tvDemoSub).text =
            if (DemoMode.isEnabled) "Streaming a synthetic EMG signal."
            else "Stream a synthetic EMG signal instead of pairing hardware."

        switchDemo = findViewById(R.id.switchDemo)
        switchDemo.isChecked = DemoMode.isEnabled
        switchDemo.setOnClickListener {
            if (switchDemo.isChecked) confirmEnterDemo() else confirmExitDemo()
        }

        findViewById<MaterialButton>(R.id.btnSignOut).setOnClickListener {
            FirebaseManager.logout()
            goToRouter()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun confirmEnterDemo() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Enter Demo Mode?")
            .setMessage("You'll be signed out of your account and a synthetic EMG signal will start streaming.")
            .setNegativeButton("Cancel") { _, _ -> switchDemo.isChecked = false }
            .setPositiveButton("Enter Demo Mode") { _, _ ->
                FirebaseManager.signInDemo(); goToRouter()
            }
            .setOnCancelListener { switchDemo.isChecked = false }
            .show()
    }

    private fun confirmExitDemo() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Exit Demo Mode?")
            .setMessage("Demo Mode will turn off and you'll return to the sign-in screen.")
            .setNegativeButton("Cancel") { _, _ -> switchDemo.isChecked = true }
            .setPositiveButton("Exit") { _, _ ->
                FirebaseManager.signOutDemo(); goToRouter()
            }
            .setOnCancelListener { switchDemo.isChecked = true }
            .show()
    }

    private fun goToRouter() {
        startActivity(
            Intent(this, RouterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "—"
    } catch (e: Exception) { "—" }
}
