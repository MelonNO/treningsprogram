---
name: selectors-bugsweep-wave1
description: Selectors/nav for U1 recovery, F3 regen, S1 logging, S7 backup, S8 wizard/nav (post-v1.8.0 bug-sweep wave 1)
metadata:
  type: reference
---

Verified on-device (APK md5 025ccf0…, bug-sweep wave1). Bottom-nav ids: homeFragment, programFragment, historyFragment("Stats"), profileFragment. See also [[selectors-wave1]], [[harness-waydroid-quirks]].

## U1 Muscle Recovery (Home, card_recovery / layout_home_recovery) — NEW behavior
Recovering-only fine-grain muscles. Each row: fine muscle name (Chest, Front Delts, Side Delts, Rear Delts, Triceps, Biceps, Upper/Lower Back, Core, Quads, Hamstrings, Glutes, Calves), subtitle "trained Xh/Xd ago", right-side "Xh left" (or "<1h left"), + progress bar (recoveryFraction*100). Empty state text: "All muscles are rested and ready." Tap a row → RecapTargetViewModel.request(lastSessionId) → switches to History/Recap of that muscle's last session (NOT a logging screen). Domain: MuscleRecovery.kt (RECOVERING <48h effectiveElapsed = rawElapsed/weight; weights 1.0/0.6/0.3 from MuscleClassifier.finerMusclesFor). READY/OVERDUE/UNTRAINED muscles are HIDDEN.

## F3 full regeneration (Program → btn_program_options → "Regenerate program now")
Options dialog items: Rename / "Regenerate program now" / mesocycle toggle / freeze toggle / delete. Progress indicator = `layout_regen_progress` (VISIBLE while generating, GONE after) with `tv_regen_status`. Errors → Snackbar (friendlyAiErrorMessage): no-key="Set your API key in Profile → Settings first."; timeout="The AI request timed out…try again."; IO="Network error while reaching the AI…try again."; parse fail="No JSON found in AI response". Live regen makes real Claude calls (observed 1 call = 163s, 200 OK); callTimeout 240s. To force the no-key state: back up + delete shared_prefs/treningsprogram_secure_prefs.xml (encrypted; can't edit a single key), restore after.

## S1 Log workout (Program day → "Start Workout"; Home shows "View Recap" when today logged)
Log layout ids: et_weight, et_reps, btn_weight_plus/minus, btn_reps_plus/minus, chip_warmup (text "Warm-up"), cg_rpe (chip_rpe_easy/moderate/hard), btn_log_set, layout_logged_sets, btn_prev_exercise, btn_next_exercise (text "Next"/"Finish"/"Complete" on last), btn_pause_workout. Logged row text: working "S{n}: {reps} reps @ {weight}kg" (purple), warm-up "W{n}: {reps} reps @ {weight}kg" (grey); summary header "N working set(s)  +  M warm-up". Logged sets persist to DB immediately (workout_sets); the draft store (default prefs) only holds un-logged typed input. Finish: btn_next_exercise on last ex → "Finish workout?" dialog → "Complete" → completeWorkout() sets isCompleted=1. Verified: process-death survival, warm-up flag round-trip, Complete persists exactly entered, no double-insert on rapid log.

## S7 Backup (Profile → card_settings → row_backup)
Backup layout: btn_export_backup, btn_import_backup, btn_reset_workouts, btn_factory_reset, btn_cloud_* (cloud GATED). Export: writes JSON to cacheDir (treningsprogram-backup-YYYY-MM-DD.json) then ACTION_SEND share chooser — file IS produced (verifiable in cache/). Import: SAF OpenDocument picker → "Import Backup?" dialog. **IMPORT IS MERGE, NOT wipe-and-replace** (dialog: "This merges…nothing is deleted; backup entries are added in") — deviates from any "wipe-and-replace" spec. Factory Reset → "Reset All User Data?" dialog ("Erase Everything"/"Cancel"). Round-trippable setting for restart test: SettingsTraining switch_separate_cardio + btn_save (Profile→card_settings→row_training_profile).

## S8 Setup wizard (Home first-launch card "Get Started"; gated by isFirstLaunch=!hasCompletedOnboarding)
Onboarding-done is forced in debug builds by files/.skip_onboarding (move it aside to reach the wizard, restore after). Wizard ids: btn_wizard_next, btn_wizard_back, tv_wizard_step_title, chip_goal_strength/hypertrophy/endurance/weightloss, chip_exp_*, step_flipper. Step titles: Fitness Goal / Training Schedule / Equipment / Training Profile / Connect Claude. S8 FIX: onSaveInstanceState persists step+selections → rotation keeps the step AND the selection (verified Strength still checked after rotate-and-back). Nav double-tap guards: every navigate() is wrapped `if (currentDestination?.id == <thisFragment>)` across Home/Program/Settings/Library/Profile — rapid double-tap = no crash.

## Settings sub-screen rows (Profile → card_settings)
card_settings (Profile→Settings). Settings rows: row_training_profile, row_exercise_library, row_coach_summary, row_ai_program, row_backup, row_debug, row_about. Library: rv_exercises + et_search; row → ExerciseDetail (tv_name). Achievements header (Profile): "Achievements (N/200)" — denominator = achievements table row count; this device has exactly 200 (no orphans). N rises as you unlock (saw 9→12 after completing a workout).
