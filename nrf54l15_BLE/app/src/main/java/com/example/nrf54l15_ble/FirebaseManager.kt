package com.example.nrf54l15_ble

import android.content.Context
import android.content.SharedPreferences

/**
 * Android analog of the iOS `FirebaseManager`.
 *
 * Intentionally a **local stub** (SharedPreferences, no cloud) so the app runs with no
 * backend — exactly what the project calls for. It mirrors the iOS façade: auth state,
 * role, and Demo Mode. Wire in real Firebase later by replacing the bodies of [login],
 * [logout], and [inviteCompanion]; nothing else in the app needs to change.
 */
object FirebaseManager {

    private const val PREFS = "myosense_auth"
    private const val KEY_ROLE = "role"
    private const val KEY_EMAIL = "email"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    val currentRole: UserRole
        get() {
            if (DemoMode.isEnabled) return UserRole.PRO
            return runCatching {
                UserRole.valueOf(prefs.getString(KEY_ROLE, null) ?: return UserRole.UNAUTHENTICATED)
            }.getOrDefault(UserRole.UNAUTHENTICATED)
        }

    val currentEmail: String?
        get() = if (DemoMode.isEnabled) DemoMode.DEMO_EMAIL else prefs.getString(KEY_EMAIL, null)

    val isAuthenticated: Boolean
        get() = currentRole != UserRole.UNAUTHENTICATED

    /**
     * Stub login. Routes by email so both interfaces are reachable without a backend:
     *   - contains "pro", "physician", "doctor", or "coach" → [UserRole.PRO]
     *   - anything else                                     → [UserRole.COMPANION]
     */
    fun login(email: String, @Suppress("UNUSED_PARAMETER") password: String): Result<UserRole> {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Email required"))
        val role = when {
            listOf("pro", "physician", "doctor", "coach").any { trimmed.contains(it, true) } ->
                UserRole.PRO
            else -> UserRole.COMPANION
        }
        DemoMode.disable()
        prefs.edit()
            .putString(KEY_ROLE, role.name)
            .putString(KEY_EMAIL, trimmed)
            .apply()
        return Result.success(role)
    }

    /** Enter Demo Mode and sign in as the synthetic PRO account. */
    fun signInDemo() {
        prefs.edit().clear().apply()
        DemoMode.enable()
    }

    /** Leave Demo Mode and return to the sign-in screen. */
    fun signOutDemo() {
        DemoMode.disable()
    }

    fun logout() {
        if (DemoMode.isEnabled) {
            signOutDemo()
            return
        }
        prefs.edit().clear().apply()
    }

    /** Stub invite — generates a one-time code so the Pro UI flow is runnable end-to-end. */
    fun inviteCompanion(@Suppress("UNUSED_PARAMETER") companionEmail: String): String {
        val alphabet = ('A'..'Z') + ('0'..'9')
        return (1..6).map { alphabet.random() }.joinToString("")
    }
}
