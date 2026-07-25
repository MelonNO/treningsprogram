---
name: reference-ondevice-test-harness
description: On-device UI test harness for the Android app — Waydroid + adb + Maestro on this Pi (REPLACES the old "no on-device" note)
metadata:
  type: reference
---

On-device UI testing for the treningsprogram Android app IS available on this Pi via **Waydroid + adb + Maestro** (NOT AVD — host is aarch64, no x86 nested virt). This supersedes the older claim in [[reference-test-harness]] that on-device was impossible.

**Device & tools**
- Waydroid container, fixed adb IP `192.168.240.112:5555` (pre-connected).
- adb: `/home/migul/android-sdk/platform-tools/adb -s 192.168.240.112:5555`
- App package `com.migul.treningsprogram`, launcher `.MainActivity`. Debug build is `run-as`-able.
- Maestro 2.6.1 at `~/.maestro/bin/maestro`. Run flows with `maestro --device 192.168.240.112:5555 test FLOW.yaml`.
- Required env for every maestro/adb/build call:
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64`, `ANDROID_HOME=/home/migul/android-sdk`,
  `PATH` prepend `~/.maestro/bin` and `android-sdk/platform-tools`,
  `MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true`,
  `WAYLAND_DISPLAY=wayland-0 XDG_RUNTIME_DIR=/run/user/1000`.

**Gotchas that actually bit (load-bearing)**
- Standalone `adb screencap` / `adb shell uiautomator dump` RACE the render and often capture a blank screen or a STALE Home even when the real screen is the Log/other fragment. Do NOT trust them for "what screen am I on". Instead drive + assert + screenshot all *inside one Maestro flow* using `extendedWaitUntil: { visible: { id: ... } }` then `takeScreenshot`. Maestro times its screenshot after its own waits, so it shows the truth.
- uiautomator dump IS reliable for reading *text content of a screen Maestro has already settled on* (e.g. to scrape exact strings), just not for deciding the current fragment right after a tap.
- Waydroid freezes its container when no UI surface shows → every adb call then hangs. Before a batch: `timeout 10 waydroid status | grep Container` must say RUNNING; if FROZEN run `waydroid show-full-ui &` (with WAYLAND env). Always wrap adb in `timeout`.
- Device clock is **GMT/UTC**, ~2h behind host CEST. `dumpsys package ... lastUpdateTime` is in device-UTC, so a fresh install can look "older" than now — verify installs by MD5 of base.apk vs the on-disk APK instead.
- Memory ~8GB, swap tight. `./gradlew --stop` before driving the device if you built.
- Device `date` is toybox (no `-d`, no `%u`); compute dates on the host with `TZ=UTC`.
- **No-network/airplane: NOW ACHIEVABLE (corrected 2026-06-25).** Earlier (2026-06-24) thought impossible — the working method is a HOST-side firewall rule dropping container egress while sparing the adb-over-IP bridge: `sudo iptables -I FORWARD 1 -s 192.168.240.0/24 -o wlan0 -j DROP` (adjust subnet/uplink iface). Confirm with container `ping 8.8.8.8` → 100% loss while adb stays alive; set a dummy API key so the app actually attempts a call; trigger Generate → spinner clears + clear timeout error (SocketTimeout after ~30s connect timeout ×withAiRetry). REMOVE the rule after (`sudo iptables -D FORWARD ...`) + verify internet restored. (Does NOT work: `cmd connectivity airplane-mode` no-op; `svc wifi disable` kills adb + corrupts routing.)

**State setup without the Claude API**
- The app needs a generated program (planned_exercises) for guided-workout flows; AI generation needs a live API key. Instead seed planned_exercises directly: `adb shell run-as com.migul.treningsprogram sqlite3 databases/treningsprogram.db` (sqlite3 exists on device). This is a faithful state path — it's exactly what AI generation writes. Compute `weekStart = thisMonday()` as Monday 00:00 **UTC** epoch-ms, and `dayOfWeek` Mon=1..Sun=7 (matches `date +%u`). Library `exercises` table is pre-seeded (36 rows) for Item-6 search.
- DB schema is at user_version=10 (loggedAtMs column present). `workout_sessions` has `durationMinutes` not `durationMin`.

**How to apply:** For any behavioral/UI verification, write per-item Maestro flows under `/home/migul/.claude/jobs/.../tmp/flows/`, assert on resource-ids/text, screenshot for evidence. Pure logic still goes to JVM via `./build.sh test`. See [[reference-test-harness]] (now partially superseded) and [[feedback-no-unprompted-testing]] (on-device only when explicitly asked).
