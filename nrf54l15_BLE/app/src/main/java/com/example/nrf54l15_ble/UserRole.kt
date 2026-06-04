package com.example.nrf54l15_ble

/**
 * Mirrors the iOS app's role-based switch (see `ContentView` / `FirebaseManager` in
 * MyoSense iOS). The app was repositioned from clinical → wellness, so the roles are:
 *
 *  - [UNAUTHENTICATED] → show [LoginActivity]
 *  - [PRO]             → show [ProActivity]        (detailed interface + recording + export)
 *  - [COMPANION]       → show [CompanionActivity]  (simplified live activity view)
 */
enum class UserRole { UNAUTHENTICATED, PRO, COMPANION }
