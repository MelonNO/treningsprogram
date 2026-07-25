---
name: maestro-flows-b08-b09
description: Locations + coverage of the B08/B09 + regression Maestro flows (refinements-2026-06)
metadata:
  type: reference
---

Flows authored for B08 (rest-day two-mode) + B09 (mid-week regenerate), all in `/home/migul/treningsprogram/flows/`. See [[selectors-b08-b09]].

- `regression_smoke_tabs.yaml` — launch + visit all 4 bottom-nav tabs, no crash; spot surfaces render.
- `b08_settings_rest_day_mode.yaml` — Settings Training Profile: switch toggle reveals/hides controls; rest-day chips update the hint (7→6→5→6→5); rest-day persistence round-trip (Sat+Sun, leave/return).
- `b08_settings_count_persist.yaml` — switch to count mode, erase+type a new days number, Save (Later), then `back` (NOTE: `back` after typing only closes the soft keyboard, so this flow leaves you on the screen — the verify is split out).
- `b08_settings_count_verify.yaml` — fresh relaunch confirms count mode + days field persisted (re-read from prefs).
- `b08_wizard_schedule_step.yaml` — first-run wizard: Home `card_first_launch`→`btn_start_setup`, step 0 Fitness Goal → `btn_wizard_next` → step 1 Training Schedule defaults (switch OFF, Sat+Sun rest, Mon–Fri hint). Needs first-run state (remove encrypted prefs, see harness notes).
- `b09_labels.yaml` — Program options dialog "Regenerate (keep logged days)" (old label absent); Settings AI caption full-week wording.
- `b09_noop_guard.yaml` — seed a fully-logged week then tap Regenerate; assert the "nothing to regenerate" snackbar. Needs DB seed (loggedDays ≥ daysPerWeek) + a non-blank apiKey (else blank-key msg fires first).
- `regression_spotcheck_surfaces.yaml` — B04 achievements collapsed-by-default (scrollUntilVisible `header_achievements`), Progress `ac_exercise` picker, Stats tab_layout, planned-day log screen opens (`btn_start_day_workout`→`et_weight`).

Gotchas hit:
- Reaching first-run for the wizard WITHOUT wiping the DB: hasCompletedOnboarding = KEY_ONBOARDING_DONE(encrypted prefs) OR (debug && files/.skip_onboarding). On this device .skip_onboarding is absent, so just force-stop + `run-as rm shared_prefs/treningsprogram_secure_prefs.xml` (back it up first) → Home shows first-launch. Restore the prefs file after.
- Opening "Start Workout"/`btn_start_day_workout` CREATES an empty incomplete workout_session draft immediately (isCompleted=0, 0 sets) even if you log nothing and back out — delete it (`DELETE FROM workout_sessions WHERE isCompleted=0`) to leave the DB as found.
- Day chips on the Program "This Week" card are dynamically built (no fixed ids) with a `tv_day_abbr` showing "Mon".."Sun" — select by `tapOn: text: "Mon"`.
