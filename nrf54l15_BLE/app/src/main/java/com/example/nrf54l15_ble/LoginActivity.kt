package com.example.nrf54l15_ble

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * Android analog of the iOS `LoginView`. Local stub auth via [FirebaseManager], plus a
 * one-tap "Try Demo Mode" path that bypasses sign-in and streams a synthetic signal.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnDemo: MaterialButton
    private lateinit var tvError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmail    = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin   = findViewById(R.id.btnLogin)
        btnDemo    = findViewById(R.id.btnDemo)
        tvError    = findViewById(R.id.tvError)

        btnLogin.setOnClickListener { attemptLogin() }
        btnDemo.setOnClickListener { enterDemoMode() }
    }

    private fun attemptLogin() {
        tvError.visibility = View.GONE
        val email = etEmail.text.toString().trim()
        val pw    = etPassword.text.toString()
        if (email.isEmpty() || pw.length < 6) {
            tvError.text = "Enter an email and a 6+ character password"
            tvError.visibility = View.VISIBLE
            return
        }
        FirebaseManager.login(email, pw)
            .onSuccess { role ->
                Toast.makeText(this, "Signed in as ${role.name.lowercase()}", Toast.LENGTH_SHORT).show()
                routeTo(role)
            }
            .onFailure { err ->
                tvError.text = err.message ?: "Sign-in failed"
                tvError.visibility = View.VISIBLE
            }
    }

    private fun enterDemoMode() {
        FirebaseManager.signInDemo()
        routeTo(UserRole.PRO)
    }

    private fun routeTo(role: UserRole) {
        val next = when (role) {
            UserRole.PRO             -> ProActivity::class.java
            UserRole.COMPANION       -> CompanionActivity::class.java
            UserRole.UNAUTHENTICATED -> LoginActivity::class.java
        }
        startActivity(Intent(this, next).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
