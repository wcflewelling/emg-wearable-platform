package com.example.nrf54l15_ble

/**
 * Android port of the iOS `ActivityLevel` (Resting → Light → Strong) state machine.
 *
 * Thresholds apply to TKEO values. These mirror the iOS constants exactly so the two
 * apps classify activity identically. See `ActivityLevel.swift`.
 */
enum class ActivityLevel(
    val label: String,
    val colorInt: Int,
    val iconRes: Int,
    val description: String
) {
    LOW("Low", 0xFFFF3B30.toInt(), R.drawable.ic_resting,
        "Resting — little to no muscle activity"),
    MODERATE("Moderate", 0xFFFFCC00.toInt(), R.drawable.ic_light,
        "Light activation — partial effort"),
    HIGH("High", 0xFF34C759.toInt(), R.drawable.ic_strong,
        "Strong activation — muscle firing");

    companion object {
        /** Rest (no contraction) ceiling, in V². Matches iOS `lowThreshold`. */
        const val LOW_THRESHOLD = 5e-5
        /** Strong-contraction floor, in V². Matches iOS `highThreshold`. */
        const val HIGH_THRESHOLD = 5e-4

        fun from(tkeo: Double): ActivityLevel = when {
            tkeo < LOW_THRESHOLD  -> LOW
            tkeo < HIGH_THRESHOLD -> MODERATE
            else                  -> HIGH
        }

        /** Normalised 0..1 fraction for the activity bar, clamped to [HIGH_THRESHOLD]. */
        fun fraction(tkeo: Double): Float =
            (tkeo.coerceIn(0.0, HIGH_THRESHOLD) / HIGH_THRESHOLD).toFloat()
    }
}
