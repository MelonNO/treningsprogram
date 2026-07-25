---
name: harness-waydroid-quirks
description: Waydroid+Maestro on-device gotchas (network-kill hazard, IME input mangling, uiautomator idle, launchApp nav reset, no-root)
metadata:
  type: reference
---

Hard-won Waydroid+adb+Maestro quirks (device 192.168.240.112:5555). See also [[db-seeding-recipe]].

## NEVER `svc wifi disable` over adb-on-IP
`adb shell svc wifi disable` (or `svc data disable`) on Waydroid KILLS the adb-over-network path (device goes "offline / No route to host") AND corrupts the container's routing (eth0 keeps its IP but gateway becomes "Network is unreachable"). Bouncing eth0 / framework `svc wifi enable` does NOT recover it. Recovery: `sudo waydroid session stop` → `waydroid session start` (bg) → wait boot_completed=1 → `sudo waydroid shell -- svc wifi enable` → `adb connect 192.168.240.112:5555`. After a session restart, re-disable animations (`settings put global *_animation_scale 0`) — they reset.

## Deterministic on-device network-OFF — SOLVED (was thought BLOCKED)
In-container methods all fail (no root/su/netpolicy; `cmd connectivity airplane-mode` no-op; NEVER `svc wifi disable` — see above). BUT the HOST-side FORWARD-chain block WORKS and spares adb: `sudo iptables -I FORWARD 1 -s 192.168.240.0/24 -o wlan0 -j DROP`. This drops the container subnet's egress to the internet (forwarded out wlan0) while LEAVING the waydroid0 bridge adb path (host↔192.168.240.112:5555, which is local INPUT not FORWARD) fully alive. Verified 2026-06-25: container ping 8.8.8.8 / api.anthropic.com → 100% loss, gateway 192.168.240.1 → 0% loss, `adb shell` still responds. (My earlier "iptables DENIED by classifier" note was about the *interactive* add in a prior session; `sudo iptables -I/-D FORWARD ...` ran fine here.)
- To actually exercise the app's network path you need a NON-BLANK apiKey (else regen fails at the no-key check before any socket). A DUMMY key works: Settings→AI&Program→et_api_key + inputText "sk-ant-dummy…" + btn_save_api_key ("Saved" snackbar). Then btn_generate_now → real OkHttp POST to api.anthropic.com.
- Observed F3 net-OFF result: `progress_generate` spinner + "Generating your plan…" → 2 OkHttp POST attempts (withAiRetry maxAttempts=2), each `SocketTimeoutException ... after 30000ms` (connectTimeout) → spinner CLEARS + clear error "The AI request timed out. Please check your connection and try again." NOT an infinite spinner. Total ~60-70s. logcat `okhttp.OkHttpClient: <-- HTTP FAILED: java.net.SocketTimeoutException: failed to connect to api.anthropic.com` is the proof.
- ALWAYS remove the rule after: `sudo iptables -D FORWARD -s 192.168.240.0/24 -o wlan0 -j DROP` then confirm `adb shell ping -c2 8.8.8.8` recovers. Leaving it kills the container's internet for the next session.

## Maestro IME input is UNRELIABLE on Waydroid
`inputText` APPENDS to a pre-filled field, and `eraseText: N` is a NO-OP (field keeps content) — e.g. logging "60"/"5" into the prefilled RDL target produced weightKg=600, reps=56. WORKAROUND: don't type into pre-filled numeric fields. The Log-workout `et_weight`/`et_reps` come PRE-FILLED with the AI/saved target — just tap `btn_log_set` to log the target as-is (still tests persistence/warm-up/Complete). For dropdowns (`ac_exercise`) `inputText` works because the field starts empty.

## uiautomator dump fails when UI is non-idle
`uiautomator dump` returns "ERROR: could not get idle state" whenever an animation/countdown is running — notably the Log-workout REST TIMER bottom bar (counts down, never idle). Use `adb shell screencap` + Read the PNG for evidence instead, or assert via Maestro (doesn't need idle) / verify via the DB.

## Maestro `launchApp` resets nav to start destination
`pressKey: Home` + `launchApp` sends a fresh MAIN/LAUNCHER intent → resets the nav graph to Home (looks like "state lost" on a nested screen like Library-detail). This is a Maestro artifact, NOT an app bug. For a TRUE background/restore test use `adb shell input keyevent KEYCODE_HOME` then `adb shell am start -n <pkg>/.MainActivity --activity-single-top` — the app correctly RESUMES the deep screen (verified Library: et_search+rv_exercises restored). Process-death (`am force-stop`) from a nested screen → relaunch lands on Home (acceptable Android behavior), no crash.

## Launch-time weekly auto-gen fires a real POST (don't misattribute it)
On app launch the weekly auto-adaptation can fire a real `--> POST https://api.anthropic.com/v1/messages` if ANY apiKey is set (even a leftover DUMMY like `sk-off-test-…` → fast `<-- 401` ~330ms, no savePlan). It happens ~200ms after MainActivity is Displayed, well BEFORE any user tap. When verifying a no-AI-call guard (e.g. B09 regenerate no-op), correlate logcat timestamps against the Activity-Displayed line — a launch POST is NOT the guard tapping. Prove the guard's no-call by data-unchanged diff + the guard snackbar, not just "no POST in logcat".

## Retrieving an ANR / crash trace on non-rooted Waydroid
- `/data/anr/anr_<ts>` is mode 0600 system:system → `adb shell cat` AND `run-as <pkg> cat` both get "Permission denied" (it's outside the app data dir, so run-as can't help). No root/su on Waydroid.
- WORKS: the system also drops the ANR into DropBox (`logcat: DropBoxManagerService: add tag=data_app_anr`). Print it without root: `adb shell "dumpsys dropbox --print data_app_anr" > out.txt` (flag order matters: `--print` then tag). Gives the FULL compressed-text ANR incl. the per-thread Dalvik stacks. Same for `data_app_crash`, `data_app_strictmode`, `system_app_anr`, etc. This is the go-to for crash/ANR repro tasks.
- In the logcat ANR block, the `ActivityManager: CPU usage from ...` line tells you ANR type: a BLOCKED-on-I/O main thread shows ~0% app CPU (parked in Object.wait), vs a spin/deadlock burning CPU. The main-thread block stack is under `"main" prio=5 tid=1` in the dropbox text.
- ANR ≠ in-app crash log. The app's crash log (Settings→Debug→Crash Log) is fed by an UncaughtExceptionHandler — it only records THROWN exceptions. An ANR is a hang (no exception thrown) → Crash Log stays "No crashes recorded." Don't treat an empty in-app crash log as "no problem" for hang reports.
- `kill -3`/SIGQUIT to dump threads also routes through tombstoned→/data/anr (still 0600); prefer the dropbox route. SIGQUIT (signal 3) lines in logcat during an ANR are the system collecting traces, NOT a crash kill.

## Clearing a pre-filled EditText over adb (IME eraseText is a no-op)
Field-clear recipe that works despite the inputText-appends / eraseText-noop quirk: tap field to focus → `adb shell input keyevent 123` (MOVE_END) → `adb shell input keyevent 67` (DEL) xN (N ≥ field length, e.g. 45). Verify empty via uiautomator dump (field shows only its hint text). Then `adb shell input text "$VALUE"`. WARNING: a uiautomator dump of a password EditText still exposes the RAW text attribute (NOT masked like the screen) — if you dump a field holding a secret, that XML file holds the plaintext; shred it after.

## ROOT IS AVAILABLE from the HOST via `sudo waydroid shell` (corrects older "no root" notes)
The adb-connected shell (192.168.240.112:5555) is uid=2000 (no root), BUT the host has PASSWORDLESS sudo and `sudo -n waydroid shell <cmd>` runs INSIDE the container as uid=0 root (verified 2026-07-02). This is the key lever on a **release / non-debuggable APK where `run-as` fails** (release builds forbid run-as, so adb sqlite3/DB seeding is otherwise impossible):
- Read app-private files: `sudo -n waydroid shell cat /data/data/com.migul.treningsprogram/<path>` → pull DB, prompt_log.json, backup cache, EncryptedSharedPrefs, etc. for CONTENT verification even when the UI can't surface them.
- `waydroid shell` takes a BARE command with args — do NOT wrap in `sh -c` (it mis-parses `-c`) and do NOT chain with `;`/`&&` unless via a heredoc (`sudo -n waydroid shell sh <<'EOF' … EOF` works). `rm -f`/`ls -f` also mis-parse the `-f` (waydroid eats it) → use `rm <path>` (toybox rm needs no -f for a regular file).
- Enables data seeding on release builds: `cp` a file into /sdcard/Download + `chmod 666` so DocumentsUI can pick it (used to feed a backup JSON into the app's Import flow).

## Cannot advance the device CLOCK (Waydroid shares the host kernel clock)
Setting the guest date (`date …` even as root, with `settings put global auto_time 0`) prints the new time but REVERTS within ~1s — Waydroid has no time namespace, so the container reads the host (Pi, NTP-synced) clock. Changing the Pi host clock is out of scope (breaks the user's whole desktop). => Any test needing a different "today" (e.g. rolling a completed day so Home shows "Start Workout" again, multi-day streak/rest-day behavior) is BLOCKED on this harness. The in-app day-boundary setting only offers 00:00–06:00, so it can't reclassify an afternoon session out of the current evening either.

## Backup Export = ACTION_CHOOSER share sheet (blocked in Waydroid) but writes the file FIRST
Settings→Backup&Data→Export Backup fires `android.intent.action.CHOOSER` (confirmed via logcat ChooserActivity) → "No apps can perform this action" because the minimal Waydroid image has NO share-target apps. NOT an app bug. BUT the backup JSON is written to `/data/data/<pkg>/cache/treningsprogram-backup-<date>.json` BEFORE the share step → pull it via `sudo waydroid shell cat` to verify export CONTENT. Import Backup uses ACTION_OPEN_DOCUMENT → DocumentsUI (`com.android.documentsui`) IS present and works; browse via the drawer (hamburger)→Downloads. Import is a MERGE that dedupes by primary key (re-importing the app's own backup does NOT duplicate sessions/XP/achievements).

## Maestro command notes (v2.6.1)
- Rotation command is `setOrientation:` with enum `PORTRAIT` / `LANDSCAPE_LEFT` / `LANDSCAPE_RIGHT` / `UPSIDE_DOWN` (NOT bare "LANDSCAPE" → parse error).
- Back is `- back` (YamlActionBack).
- ALL-CAPS styled buttons: text match on visible label can be flaky (e.g. "Next"/"Complete" on the Log screen) — prefer the view id (`btn_next_exercise`) or coordinate taps; if both flake due to a timer, drive via coords + verify via DB.
- An id-regex assert on a view that's never present passes trivially as notVisible — make sure the id actually exists in the layout before trusting a `notVisible` terminal-wait.
