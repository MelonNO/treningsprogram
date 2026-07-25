---
name: reference_ondevice_test_harness
description: "On-device UI test harness for the Android app — Waydroid + adb + Maestro (NOT AVD, host is aarch64)"
metadata: 
  node_type: memory
  type: reference
  originSessionId: 0a2e2091-f9b9-45b9-8dd7-cb4e5e720bf5
---

On-device acceptance testing for [[project_treningsprogram]] runs through **Waydroid + adb + Maestro**, not an Android AVD.

**Why not AVD:** the Pi is aarch64. Google ships no ARM64 Linux build of the Android emulator, and there are no `emulator/`/`system-images/` dirs under `~/android-sdk`. `/dev/kvm` exists but there is no emulator binary that can use it here. So "AVD + KVM" is impossible on this host; Waydroid (native ARM Android via LXC, shares the host kernel) is the working substitute.

**Components:**
- Waydroid 1.6.2 installed; `waydroid-container.service` enabled. Container is already initialized (MAINLINE).
- Maestro 2.6.1 installed at `~/.maestro/bin/maestro` (runs fine on aarch64 under Java 21).
- adb at `~/android-sdk/platform-tools/adb`; device is the fixed Waydroid IP `192.168.240.112:5555`.

**Bring-up sequence:**
1. `export WAYLAND_DISPLAY=wayland-0 XDG_RUNTIME_DIR=/run/user/1000`
2. `waydroid session start &` (needs the labwc Wayland socket).
3. `waydroid show-full-ui &` — REQUIRED. Without a rendered surface the container auto-suspends (status shows `Container: FROZEN`) and **every adb call hangs**. Showing the UI thaws it. Also set `waydroid prop set persist.waydroid.suspend false` to keep it thawed.
4. `adb connect 192.168.240.112:5555` then `adb -s 192.168.240.112:5555 shell getprop sys.boot_completed` should be `1`.

**Run Maestro:** set `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64`, `ANDROID_HOME=~/android-sdk`, PATH += `~/.maestro/bin` and platform-tools; `MAESTRO_CLI_NO_ANALYTICS=1`. Then `maestro test flow.yaml`. Always write maestro output to a file (don't pipe through `tail` — it buffers until exit and hides hangs).

**Gotchas:**
- Memory is tight (8GB). Idle Gradle + Kotlin daemons eat ~2.8GB; `./gradlew --stop` (or kill them) before running the device, or swap fills and the container goes unresponsive.
- `waydroid status` may show `IP address: UNKNOWN` while the fixed `192.168.240.112` still works.
- App package / launcher: `com.migul.treningsprogram/.MainActivity`.
- **Always reinstall the freshly-built APK before trusting on-device results, and verify by MD5** (`adb shell md5sum $(adb shell pm path <pkg> | sed 's/package://')` vs the repo APK). In this session the device had a stale APK predating the source edits — without catching it, every behavioral test would have validated the wrong build.
- **Maestro text entry is unreliable on Waydroid** — `eraseText`/`inputText` produce garbage (saw "98 reps"/"450 kg" artifacts). Prefer the app's +/- steppers, or tap-then-type carefully. Put assertions + `takeScreenshot` INSIDE the Maestro flow with `extendedWaitUntil`; standalone `adb screencap`/`uiautomator dump` race the render and capture stale frames — use them only to read text on an already-settled screen.
- A release/R8 build (`assembleRelease`) is memory-heavy; `waydroid session stop` first to free ~1.5GB, then restart the session+UI afterward.
- To set up plan state without a live AI key: seed `planned_exercises` directly via `adb ... shell run-as com.migul.treningsprogram sqlite3 <db>` (same path AI generation writes) — `weekStart=thisMonday(UTC)`, `dayOfWeek` per WorkoutRepository helpers. **Only works on DEBUG builds** (see below).

**Gotchas added from the 2026-07-02 stage-4 run:**
- adb may be `unauthorized` on first connect — push `~/.android/adbkey.pub` to `/data/misc/adb/adb_keys` via `sudo waydroid shell`, then restart adbd.
- **Release APKs are not debuggable → `run-as` + sqlite3 DB seeding is UNAVAILABLE.** Testing a release build means all data must be driven through the UI or live generation; use a debug build when DB seeding is required.
- **Clock advance is impossible**: Waydroid shares the host kernel clock — the guest date reverts even with `auto_time=0`. Multi-day scenarios (streak break/keep, next-day sessions, 7-day PR windows, "beat last time" second-session tests) cannot be tested in Waydroid; use a debug build + DB seeding or a real device.
- Backup export fires a share `ACTION_CHOOSER`; Waydroid has no share-target apps, so the export-share UI flow is untestable (file-content verification still possible via the saved file).
