---
name: selectors-u2-xp-patch
description: U2 XP-log + onboarding-gate selectors/nav (patch v1.9.0 follow-up, schema v14) — XP bar surfaces, empty/populated, deterministic non-AI onboarding via import-merge
metadata:
  type: reference
---

Verified on patch APK md5 5614a3b95f1f9d579f2a8002a0776951 (branch patch-ondevice-ux1, schema v14). See [[harness-waydroid-quirks]] [[db-seeding-recipe]] [[selectors-bugsweep-wave1]].

## U2 XP bar + XP log
- Home XP card id = `card_home_xp` (tv_xp_label, progress_xp). Profile XP card id = `card_profile_xp` (tv_profile_xp_label, progress_profile_xp). Tapping either → XP Log (nav actions action_home_to_xp_log / action_profile_to_xp_log; both guarded `if currentDestination==thisFragment` so rapid double-tap opens ONE instance, no crash, single back returns).
- XP Log screen: toolbar title "XP Log", `rv_xp_events` list, `layout_empty` empty state. Empty text = "No XP events yet" + "Complete a workout to start earning — your XP history will appear here." (star ⭐ icon). Row format (XpLogAdapter): tv_xp_reason / tv_xp_amount "+N XP" / tv_xp_date (Locale-default date-time).
- xp_events table is FORWARD-RECORDING only: 0 rows until a workout completes AFTER U2 ships (no backfill). Completing a workout writes rows via XpEventBuilder: always "Workout completed" (baseXp); plus "N set(s) logged", "Personal record ×N", "Daily challenge: <names>" when those components >0. sessionId stamped on each. Sum of amounts == xpEarned.

## CRITICAL onboarding gate (blocks Home XP card + all normal Home content)
- HomeFragment.onViewCreated does `if (viewModel.isFirstLaunch) { wire setup card; return }` BEFORE wiring card_home_xp — so on a NON-onboarded device the Home XP card has NO click listener (tap = no-op, NOT a bug). Home shows card_first_launch "Welcome to Treningsprogram"/"Get Started" instead of btn_start_workout. ProfileFragment is NOT gated → card_profile_xp + card_settings always work.
- isFirstLaunch = !prefs.hasCompletedOnboarding (encrypted prefs). hasCompletedOnboarding is set true ONLY on a SUCCESSFUL live AI generation (SetupWizardViewModel .onSuccess line ~113, or SettingsViewModel.generateProgramWithOnboarding). There is NO pure non-AI UI path.
- DETERMINISTIC non-AI onboarding (no API key needed): use the app's own Import-Backup merge. PreferencesMerger.merge does `hasCompletedOnboarding = current || backup` (OR logic) — so importing a backup JSON with preferences.hasCompletedOnboarding=true flips it. Recipe: write a minimal valid v3 BackupEnvelope JSON (schema_version:3, all entity lists [], preferences{...hasCompletedOnboarding:true...}), `adb push` to /sdcard/Download/, then Profile→card_settings→row_backup("Backup & Data")→btn_import_backup→SAF DocumentsUI picker (tap the .json in Recent)→"Import Backup?" dialog→tap button1 "IMPORT" (UPPERCASE — text="Import" match fails). force-stop+relaunch → onboarded. Import is MERGE so seeded sessions/sets/achievements survive. JSON kept at scratch + /sdcard/Download/onboard_backup.json.

## Freestyle workout (rest day) to generate an XP event
- Home "Log Freestyle Session" (btn_start_workout) when today is a rest day → Free Session log. et_freestyle_exercise starts EMPTY (label "Exercise name") so inputText works cleanly. et_weight/et_reps also EMPTY for freestyle — use btn_weight_plus (+2.5kg) / btn_reps_plus (+1) steppers, NOT IME numeric typing. btn_log_set logs; persists to workout_sets immediately. btn_next_exercise label = "Complete" on the single/last freestyle exercise → "Finish workout?" dialog ("End the session now?", positive button1 UPPERCASE "COMPLETE") → completeWorkout() writes xp_events. NB: after logging, a REST TIMER bottom bar counts down → uiautomator "could not get idle state" until it ends (~45s) or you Skip; Maestro still works.
- Maestro launchApp RESETS nav to Home AND loses the active log screen view — to resume mid-workout, don't launchApp; Home shows "Resume Workout" (btn_start_workout) to re-enter the active session.
