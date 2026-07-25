---
name: selectors-settings-debug
description: Settings hub + AI&Program + Debug-log screen selectors/nav (Profile→Settings), API-key set recipe, week-plan generate trigger, prompt/crash-log behavior
metadata:
  type: reference
---

Verified on v1.10.4 (main @ c566063, APK md5 cdf1eb33…). See [[harness-waydroid-quirks]] [[selectors-u2-xp-patch]] [[selectors-s2-program-tab]].

## Nav (bottom_nav, 4 tabs, content-desc = tab name)
homeFragment | programFragment | historyFragment ("Stats") | profileFragment. On the standard 1192-wide Waydroid window the tab centers are ~786 / ~902 / ~1018 / ~1134, y~873. Profile is rightmost.

## Profile tab → Settings hub
- Profile shows `card_settings` (top card, "Settings ›", center ~960,194) → opens the Settings hub. Also `card_profile_xp`, stats, `tv_prs`.
- Settings hub rows (each an item with title + subtitle + ›): `row_training_profile` (Goal/experience/schedule/equipment), `row_exercise_library`, `row_coach_summary`, `row_ai_program` (API key & program generation), `row_backup` (Export/import/reset), `row_debug` (Prompt/rejection/crash log + diagnostics), `row_about`. Header id `tv_header_title`, back `btn_header_back`.

## AI & Program screen (row_ai_program)
- `et_api_key` (EditText, password-masked on screen; hint "Claude API Key"). `btn_save_api_key` ("Save API Key") → "Saved" snackbar.
- Set the key over adb: focus et_api_key, CLEAR any leftover dummy first (see [[harness-waydroid-quirks]] field-clear recipe — inputText APPENDS), then `adb shell input text "$(cat '/home/migul/treningsprogram/claude k')"`. Key chars [A-Za-z0-9_-] are safe for `input text`. Verify by LENGTH from a dump (don't print the key); then SHRED that dump (a uiautomator dump exposes the field's raw plaintext even though the screen masks it).
- `btn_generate_now` ("Generate Now", center ~955,615) = the WEEK-PLAN generation trigger. Caption: "Generate Now regenerates the FULL week — it replaces every day, including ones you've already logged." (Per-day keep-logged regen lives on the Program tab instead, see [[selectors-s2-program-tab]].) While generating, an inline spinner + "Generating your plan…" appears next to the button.

## Debug screen (row_debug)
Rows: `row_prompt_log`, `row_rejection_log`, `row_unrecognized`, `row_crash_log`.
- Prompt Log: cards titled "GENERATE ATTEMPT n" / "WEEKLY SUMMARY" with timestamp + "Show". `Clear` action top-right. IMPORTANT: an entry is written on COMPLETION of the call, not at send — a generation that hangs/ANRs before the response is fully read leaves NO prompt-log entry for that attempt. Launch-time weekly auto-adaptation also writes entries (correlate timestamps; see [[harness-waydroid-quirks]] launch-POST note).
- Crash Log: `Share` + `Clear` actions; empty state text "No crashes recorded." Fed by an UncaughtExceptionHandler → records THROWN exceptions only; an ANR/hang leaves it empty.

## v1.10.4 streaming generate = main-thread ANR (reported, repro 2026-06-27)
`btn_generate_now` → `AiRepository.sendStreaming` (AiRepository.kt:588) calls `okhttp3.ResponseBody.string()` to read the streaming (HTTP/2, gzip, unknown-length) response while the coroutine runs on `Dispatchers.Main` (stack: DispatchedTask.run → Handler.handleCallback → Looper.loop → ActivityThread.main). Main thread blocks in Http2Stream.waitForIo (Object.wait, ~0.3% CPU) → ANR ("App isn't responding"), NO FATAL EXCEPTION, NO NetworkOnMainThreadException, process not killed. v1.10.4 removed the OkHttp timeout ("no timeout") so it can hang indefinitely. The launch-time auto-adaptation path (background) is NOT affected — it completed + logged normally on relaunch. Handed to orchestrator to fix; do not fix here.
