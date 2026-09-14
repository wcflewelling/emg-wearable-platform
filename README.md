# EMG Wearable Platform

**Dual-channel surface-EMG sensing, hardware to app — nRF54L15 firmware, BLE transport, and an Android client.**

Streams EMG from a Nordic nRF54L15 over BLE, runs Teager–Kaiser Energy Operator conditioning to separate muscle activation from motion artifact, charts it live, and exports CSV recordings.

> ⚠️ **Research and educational use only. Not a medical device, and it has no regulatory clearance.** Nothing here should be used to make a clinical decision.

---

## What's in here

```
nrf54l15_BLE/
├── app/       Android client — Kotlin, Views/XML, MPAndroidChart
├── projEMG/   earlier client — Jetpack Compose, Room, Ktor
└── android/   nRF54L15 firmware — Zephyr / nRF Connect SDK, C
```

This repo holds **both halves of the embedded side**: the firmware running on the sensor and the Android app that consumes it. The iOS client and the clinical framing of the project live elsewhere and are not public.

## The signal problem

Sampling EMG is the easy part. The hard part is that the wearer moves, and motion artifact lands in the same amplitude range as the muscle signal you care about.

`Tkeo.kt` implements Teager–Kaiser Energy Operator conditioning, which emphasizes instantaneous energy — the product of amplitude *and* frequency — so a genuine activation burst separates from the low-frequency, high-amplitude swing of the limb moving. That single stage is what makes the activation classifier usable rather than noise-triggered.

## The Android client

- **BLE central** — connects to the nRF54L15, two channels
- **Live charting** with connection state, activity bars, and alarm banners
- **Two roles** — *Pro* (monitor, recordings, electrode map) and *Companion* (one large activity readout)
- **Demo mode** — `DemoSignalGenerator` synthesizes EMG so the app runs with no hardware attached, which also makes the UI testable in CI
- **Recordings** — every session written to CSV in `Sessions/`, browsable in-app with share and delete
- **Body map** — muscle placement guide for electrode positioning

Managers are split by concern: `EmgManager` (BLE, TKEO, alarms, demo), `SessionManager` (CSV lifecycle), `Tkeo` (signal math). No backend — auth is a local stub and recordings never leave the device.

## Two clients, one open decision

`app/` and `projEMG/` are two Android codebases targeting the same hardware, started at different times and never merged. `app/` is the active one. `projEMG/` is the earlier Compose attempt, kept for architecture worth salvaging.

Picking one is still an open call, and it's written down here rather than left for whoever reads the repo next to discover.

## Building

Open `nrf54l15_BLE/` in Android Studio — needs the Android SDK and JDK 17. Gradle sync, then build `app`.

The firmware under `android/` needs its own nRF Connect SDK / Zephyr workspace; the SDK tree itself is not vendored here.

---

Built by [Wyatt Flewelling](https://github.com/wcflewelling) · Biomedical Engineering, UW–Madison
