---
name: selectors-b08-b09
description: B08 rest-day two-mode + B09 mid-week regenerate selectors/nav (refinements-2026-06, APK e1a6c10…)
metadata:
  type: reference
---

Verified on-device APK md5 e1a6c10e4ad8341b62d8435b707f8089 (branch refinements-2026-06). See [[selectors-s2-program-tab]] [[selectors-u2-xp-patch]] [[harness-waydroid-quirks]] [[db-seeding-recipe]].

## B08 — two day-selection modes (Settings Training Profile AND Setup Wizard, SAME ids)
- Switch `switch_let_ai_choose_days` ("Let the AI choose which days to train"). CHECKED(ON)=COUNT mode; UNCHECKED(OFF)=REST-DAY mode. (Counterintuitive: ON hides the rest chips.)
- REST mode container `layout_rest_mode`: chip group `chip_group_rest_days` with `chip_rest_1`..`chip_rest_7` = Mon..Sun (filter chips, multi-select = REST days), derived hint `tv_training_days_hint`.
- COUNT mode container `layout_count_mode`: Settings uses field `et_days_per_week`; Wizard uses single-select chip group `chip_group_days` (`chip_days_2`..`chip_days_6`, default `chip_days_4`).
- Hint text format (TrainingDaySelection): "Training N day(s)/week: Mon, Tue, …" listing the TRAINING days (7 − rest). Empty training set → "Pick at least one training day (leave a day un-selected)." Rest days are the CHECKED chips; training = unchecked days. So checking Sat+Sun → "Training 5 days/week: Mon, Tue, Wed, Thu, Fri".
- Settings nav: profileFragment → `card_settings` → settingsFragment → `row_training_profile` → settingsTrainingFragment. Save = `btn_save` ("Save Changes") — VISIBLE only when hasChanges()==true. Tapping Save on a real change pops a "Settings saved" AlertDialog → tap "Later" to avoid triggering generation; "Generate now" would fire a live AI call.
- Settings persistence: rest mode stores `restDaysCsv` (e.g. "6,7") + derives daysPerWeek=7−rest; count mode clears restDaysCsv="" + saves the number. Survives leave/return AND fresh relaunch (re-read from encrypted prefs). prefs key = rest_days_csv; daysPerWeek key separate.
- Wizard defaults (fresh first-run): switch OFF (rest mode), `chip_rest_6`(Sat)+`chip_rest_7`(Sun) android:checked=true → hint "Training 5 days/week: Mon, Tue, Wed, Thu, Fri" computed on step load. Wizard steps order: 0 Fitness Goal, 1 "Training Schedule", 2 Equipment, 3 Training Profile, 4 Connect Claude. tv_wizard_step_title shows the title; btn_wizard_next advances (label "Generate My Program" on last input step).

## B09 — mid-week regenerate (Program tab) + full-week regen moved to Settings
- Program options dialog (`btn_program_options` on `card_program_switcher`) now lists "Regenerate (keep logged days)" (NOT old "Regenerate program now"). "Delete this program" only appears with >1 program.
- Settings → AI & Program (`row_ai_program` → settingsAiFragment) PROGRAM GENERATION caption: "…Generate Now regenerates the FULL week — it replaces every day, including ones you've already logged. To keep logged days … use Regenerate on the Program tab." Field `et_api_key` + `btn_save_api_key`; `btn_generate_now` = full-week regen.
- No-op guard: regeneratePreservingLoggedDays checks apiKey.isBlank() FIRST (→ "Set your API key in Profile → Settings first."), THEN nothingToRegenerate(loggedDayCount ≥ eff.daysPerWeek) → error Snackbar "All {eff.daysPerWeek} of this week's training days are already logged — nothing to regenerate." (em dash) and returns BEFORE any AiRepository call. Surfaces via dayGenerationError.collect → Snackbar.make(LENGTH_LONG) in ProgramFragment.
- A day is "logged" iff ≥1 of its planned_exercises rows has isLogged=1 (RegeneratePlanner.loggedDays). Logged SETS/history live in workout_sessions/workout_sets and are NEVER touched by regenerate — only non-logged days' planned_exercises rows are replaced (onSuccess only).
