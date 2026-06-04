# MyoSense Android — Project State

_Last updated: 2026-06-03 — iOS-parity UI/feature port (`app/`)_

## 2026-06-03 — Ported `app/` to match the iOS app (FNP-Monitor)

Reworked the active client (`nrf54l15_BLE/app/`) to resemble the iOS app and adopt its
better features. **No backend** — auth/demo are local stubs; recordings are saved to
device and browsable in-app.

- **Repositioned clinical → wellness.** Roles are now **Pro** / **Companion** (was
  Physician/Caregiver). Activity is **Low / Moderate / High** (Resting → Light → Strong).
  All patient/nerve language removed (`ActivityLevel.kt`, CSV header `# Session Tag:`).
- **iOS-style light UI.** New `Theme.MyoSense` (light, system blue, grouped background),
  rounded cards, connection pills, activity bars, session/alarm/demo banners. New brand
  wordmark in the toolbars and login.
- **Demo Mode.** `DemoMode.kt` + `DemoSignalGenerator` synthesize an EMG signal (no
  hardware). "Try Demo Mode" on login, orange banner, Settings toggle.
- **Recordings tab.** `RecordingsActivity` lists every CSV in `Sessions/` with share +
  delete; reachable from the Pro bottom tab bar and the Monitor data row.
- **New screens:** `ProActivity` (Monitor + bottom tabs Monitor/Recordings/Electrode Map),
  `CompanionActivity` (big activity hero), `SessionSummaryActivity`, `SettingsActivity`,
  `OnboardingActivity`, `InviteActivity`, `BodyMapActivity` (muscle placement guide).
- **Manager split preserved.** `EmgManager` (BLE/TKEO/alarm/demo), `SessionManager`
  (CSV lifecycle + file browser helpers), `FirebaseManager` (local stub), `Tkeo`.
- **Deps added:** `androidx.recyclerview`, `androidx.cardview`.
- **Verify:** open `nrf54l15_BLE/` in Android Studio and run a Gradle sync + build
  (needs the Android SDK + JDK 17, which aren't in the headless tooling here).

---

_Earlier state (2026-05-27, Wyatt) follows._

This is the working folder for the Android port of **MyoSense** — a dual-channel EMG monitoring system that streams data over BLE from an nRF54L15 SoC to a phone, runs a TKEO (Teager-Kaiser Energy Operator) pipeline, charts it in real time, alerts on low nerve activity, and exports CSV recordings.

The repository on disk contains three coupled subprojects under `nrf54l15_BLE/` plus block-diagram references for both platforms.

---

## What is here

```
AndroidStudioProjects/
└── nrf54l15_BLE/
    ├── app/         ← New Android app (Views/XML, MPAndroidChart) — the active MyoSense port
    ├── projEMG/     ← Older Android app (Jetpack Compose, Firebase, Room, Ktor) — separate git repo
    ├── android/     ← nRF54L15 firmware (Zephyr/NCS, C) — separate git repo
    ├── Android_block_diagram.{png,pdf}
    └── iOS_block_diagram.{png,pdf}
```

There are effectively **two Android codebases living side by side**. They target the same hardware but were started at different times and have not been merged. Any decision about which to ship as "MyoSense Android" needs to happen before more parallel work goes into both.

---

## 1. `nrf54l15_BLE/app/` — primary Android client

The active, more recent client. Single-activity AppCompat app, XML layouts, MPAndroidChart for plotting.

- **Package:** `com.example.nrf54l15_ble`
- **compileSdk 36 / minSdk 26 / targetSdk 34**, Kotlin, JVM target 1.8
- **Charting:** MPAndroidChart v3.1.0 (Jitpack)
- **Single source file of substance:** `MainActivity.ky.kt` (≈663 lines) holds everything — BLE scan/connect, GATT callbacks, packet parsing, TKEO, streaming CSV writer, alarm logic, sharing intent.

Implementation highlights (already working in the source):
- BLE service UUID `75ab9000-…`, ADC characteristic `27314856-…`, sensor characteristic `27314857-…`.
- ADC conversion calibrated for nRF54L15 SAADC (gain 1/4, 1.024 V internal bandgap, 14-bit) → 250 µV/LSB.
- TKEO `ψ[n] = x[n-1]² − x[n-2]·x[n]` applied per-channel (CH1 and CH2 are symmetric pipelines).
- Streaming CSV: one writer flushes every ~8 000 samples and rolls to a new part file every 1 hour to keep file sizes manageable (~180 MB/hr at 1 kHz).
- Low-activity alarm matches the iOS threshold (`5e-5 V²` sustained for 30 s) — vibrates, plays default ringtone, shows banner.
- Share-out via `FileProvider` with `ACTION_SEND` / `ACTION_SEND_MULTIPLE`.

Things that stand out as cleanup candidates:
- Filename `MainActivity.ky.kt` looks like a typo (likely `.kt`) — worth renaming.
- All logic lives in one Activity. Splitting into BLE service / repository / view layer would make this maintainable as features grow.
- Namespace and applicationId still default to `com.example.nrf54l15_ble` — needs a real bundle ID before any Play Store submission.
- No unit tests beyond the Android Studio scaffold (`ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`).

---

## 2. `nrf54l15_BLE/projEMG/` — older Compose-based app

An earlier, more feature-rich attempt that was never folded into `app/`. It is a fully separate Gradle project with its own `git` remote.

- **Git remote:** `https://github.com/wcflewelling/projEMG.git` (branch `main`)
- **Package:** `com.example.projemg`
- **compileSdk 34 / minSdk 24 / targetSdk 34**, Kotlin, JVM 17, Jetpack Compose
- **Stack:** Firebase (Messaging, Analytics), Room 2.6.1, Ktor 2.3.11, Vico 1.13.1 charts, kotlinx-serialization
- **Source layout (~1 027 LOC of Kotlin):**
  - `MainActivity.kt` (440 lines) — Compose UI
  - `EMGService.kt` (322 lines) — background service (uncommitted, staged as new + further modified)
  - `HistoryActivity.kt` (158 lines)
  - `data/Database.kt` — Room schema
  - `MyFirebaseMessagingService.kt` — FCM push
  - `FnpMonitoringApp.kt` — Application subclass

Git state in `projEMG/`:
- **3 commits ahead of nothing in particular** (`Initial commit` → `Create README.md` → `Update 1`).
- **Large uncommitted diff:** 743 insertions / 401 deletions across 8 files including a brand-new `EMGService.kt` (staged) and substantial rewrites of `MainActivity.kt` and `HistoryActivity.kt`. This work is at risk until committed and pushed.

Open question: does anything in `projEMG/` need to be ported to `app/` before we abandon it (Firebase push notifications? Room-backed history? Background service?), or do we keep `projEMG/` as the shipping app and retire `app/`?

---

## 3. `nrf54l15_BLE/android/` — firmware (nRF54L15, Zephyr / NCS)

C firmware for the BLE peripheral. Separate git repo (`master` branch, one commit: `Initial commit for android`).

- **Source:** `src/main.c` — dual-channel ADC at 2 kHz + on-die TEMP sensor advertised as the "sensor" characteristic (humidity field retained in packet layout but always reports 0.0).
- **Build:** `build_ncs.sh`, flash via `flash_ncs.sh`. Two stale build trees (`build/`, `build_1/`, `build_2/`) present.
- **Configs:** `prj.conf` (default), `prj_battery.conf` (battery-optimized variant).
- **Device-tree overlay:** `app.overlay`.
- **Packet contract (must stay in sync with the Android client):**
  - ADC packet = `4 B timestamp (µs, LE)` + `BATCH_SIZE (=8) × (ch1 u16 LE + ch2 u16 LE)` = 36 bytes
  - Requires ATT MTU ≥ 39 — the Android client negotiates MTU 100.

Git state in `android/`:
- Uncommitted changes in `CMakeLists.txt`, `prj.conf`, `src/main.c` (~339 inserted lines in `main.c` alone).
- Untracked: `.claude/`, `app.overlay`, `build_ncs.sh`, `flash_ncs.sh`, `prj_battery.conf`. These look intentional — they should be added (or explicitly `.gitignore`d) so the build is reproducible.

---

## Hardware / protocol summary

| Layer        | Detail                                                                 |
|--------------|------------------------------------------------------------------------|
| SoC          | Nordic nRF54L15                                                        |
| ADC          | SAADC, dual channel (AIN0, AIN1), 14-bit, 1.024 V bandgap, gain 1/4    |
| Sample rate  | 1 kHz per channel (8 samples/packet → 125 notifications/s)             |
| BLE service  | `75ab9000-cd33-44dd-ba7e-2037855136d4`                                 |
| ADC char     | `27314856-fe77-45f0-b4f8-fed858ece431` (notify)                        |
| Sensor char  | `27314857-fe77-45f0-b4f8-fed858ece431` (notify) — die temp + 0.0       |
| Device name  | `WYATT nRF54`                                                          |
| Conversion   | `(1.024 / 0.25) / 16384` V/LSB → 250 µV/LSB                            |

---

## Risks and open work

1. **Two-codebase ambiguity.** Pick the canonical Android client (`app/` vs `projEMG/`) and start retiring the other. Keeping both alive multiplies bugs and review effort.
2. **Uncommitted work is significant.** `projEMG/` has ~1 100 lines of diff; firmware has ~340 lines in `main.c` plus several untracked files. Commit + push before any other work to protect it.
3. **No real package name / branding.** Both apps still ship `com.example.*`. Required for Play Store, ASO, signed builds.
4. **No tests, no CI.** Default test scaffolds only. For something heading to market, at minimum: BLE parser unit tests against captured byte vectors, plus an instrumentation test for the CSV writer.
5. **`MainActivity.ky.kt` filename typo** in `app/`. Likely meant `.kt`. The compiler accepts it but tooling and reviewers will flinch.
6. **Stale build outputs** (`android/build`, `android/build_1`, `android/build_2`, `app/build`, `projEMG/app/build`) bloat the working folder — safe to clean.
7. **Time gap.** Last meaningful file mtimes are April–May 2025; today is May 2026. Verify the toolchain (AGP, NCS, Compose BOM) still resolves before assuming the project builds clean.

---

## Suggested next moves (in order)

1. Decide canonical Android codebase.
2. Commit and push every dirty tree (`android/`, `projEMG/`).
3. Rename `MainActivity.ky.kt` → `MainActivity.kt`.
4. Rename package to a real bundle ID; create a signing keystore.
5. Add a `README.md` per subproject with build/flash instructions.
6. Stand up a minimum test suite around the BLE packet parser and CSV writer.
7. Verify build against current toolchains; pin versions in `gradle/libs.versions.toml` and NCS `west.yml`.
