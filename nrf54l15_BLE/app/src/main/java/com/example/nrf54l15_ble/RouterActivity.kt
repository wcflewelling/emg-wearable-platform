package com.example.nrf54l15_ble

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Android analog of the iOS `ContentView` — a role-based switch.
 *
 *   - UNAUTHENTICATED → [LoginActivity]
 *   - PRO             → [ProActivity]
 *   - COMPANION       → [CompanionActivity]
 *
 * Draws nothing (transparent NoDisplay theme in the manifest).
 */
class RouterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route()
    }

    override fun onResume() {
        super.onResume()
        route()
    }

    private fun route() {
        val next = when (FirebaseManager.currentRole) {
            UserRole.UNAUTHENTICATED -> LoginActivity::class.java
            UserRole.PRO             -> ProActivity::class.java
            UserRole.COMPANION       -> CompanionActivity::class.java
        }
        startActivity(Intent(this, next).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
