package com.example.nrf54l15_ble

/**
 * Teager-Kaiser Energy Operator, identical to the iOS implementation:
 *
 *   ψ[n] = x[n-1]² − x[n-2] · x[n]      (one-sample latency)
 *
 * Per-channel state, no buffering — call [push] for every sample. Returns `null` for
 * the first two priming samples, then the energy value thereafter.
 *
 * CH1 and CH2 are processed by independent instances; the algorithm is symmetric.
 */
class Tkeo {
    private var xm2 = 0f
    private var xm1 = 0f
    private var primed = 0

    fun reset() {
        xm2 = 0f
        xm1 = 0f
        primed = 0
    }

    fun push(x: Float): Float? = when (primed) {
        0 -> { xm2 = x; primed = 1; null }
        1 -> { xm1 = x; primed = 2; null }
        else -> { val p = xm1 * xm1 - xm2 * x; xm2 = xm1; xm1 = x; p }
    }
}
