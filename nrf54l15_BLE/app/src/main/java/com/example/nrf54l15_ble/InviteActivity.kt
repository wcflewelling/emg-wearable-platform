package com.example.nrf54l15_ble

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

/**
 * Android analog of the iOS `InviteView` — invite a Companion. With no backend, this
 * generates a one-time invite code locally (via [FirebaseManager.inviteCompanion]).
 */
class InviteActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invite)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val tvResult = findViewById<TextView>(R.id.tvResult)
        findViewById<MaterialButton>(R.id.btnSend).setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (!isValidEmail(email)) {
                tvResult.visibility = View.VISIBLE
                tvResult.setTextColor(0xFFFF3B30.toInt())
                tvResult.text = "Enter a valid email address."
                return@setOnClickListener
            }
            val code = FirebaseManager.inviteCompanion(email)
            tvResult.visibility = View.VISIBLE
            tvResult.setTextColor(0xFF34C759.toInt())
            tvResult.text = "Invite sent ✓\nCode for $email: $code"
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun isValidEmail(email: String): Boolean {
        val parts = email.split("@")
        return parts.size == 2 && parts[1].contains(".")
    }
}
