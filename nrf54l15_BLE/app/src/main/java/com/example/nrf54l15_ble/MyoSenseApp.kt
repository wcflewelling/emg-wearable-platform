package com.example.nrf54l15_ble

import android.app.Application

/**
 * Application subclass — initializes the local [FirebaseManager] and [DemoMode]
 * singletons so every screen can read auth/demo state without a context dance.
 * Registered in `AndroidManifest.xml` via `android:name=".MyoSenseApp"`.
 */
class MyoSenseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DemoMode.init(this)
        FirebaseManager.init(this)
    }
}
