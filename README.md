# MyoSense — Android

**Android client for the MyoSense dual-channel surface-EMG platform.**

Streams EMG from an nRF54L15 over BLE, runs Teager–Kaiser Energy Operator conditioning, charts it live, and exports CSV recordings. The iOS client and the clinical (FNP) framing of this platform are kept private.

> ⚠️ **Research and educational use only. Not a medical device.**

---

## Layout

```
nrf54l15_BLE/
├── app/       active Android client — Views/XML, MPAndroidChart
├── projEMG/   earlier client — Jetpack Compose, Room, Ktor
└── android/   nRF54L15 firmware (Zephyr / NCS, C)
```

**Two Android codebases live side by side.** They target the same hardware but were started at different times and have never been merged. `app/` is the active one — it was reworked to reach parity with the iOS client. `projEMG/` is the older Compose attempt, kept because it has architecture worth salvaging.

Picking one is an open decision, and it's documented as such rather than quietly left for whoever reads the repo next.

## What `app/` does

- **BLE central** — connects to the nRF54L15, two channels
- **TKEO pipeline** (`Tkeo.kt`) — activation detection from raw EMG
- **Live charting** with connection state, activity bars, and alarm banners
- **Two roles** — *Pro* (monitor + recordings + electrode map) and *Companion* (a single large activity readout)
- **Demo mode** — `DemoSignalGenerator` synthesizes EMG so the app runs with no hardware
- **Recordings** — every session written to CSV in `Sessions/`, browsable in-app with share and delete
- **Body map** — muscle placement guide for electrode positioning

Managers are split cleanly: `EmgManager` (BLE, TKEO, alarms, demo), `SessionManager` (CSV lifecycle), `Tkeo` (signal math).

No backend — auth is a local stub and recordings stay on the device.

## Building

Open `nrf54l15_BLE/` in Android Studio. Needs the Android SDK and JDK 17. Gradle sync, then build `app`.

---

Built by [Wyatt Flewelling](https://github.com/wcflewelling) · UW–Madison Biomedical Engineering
