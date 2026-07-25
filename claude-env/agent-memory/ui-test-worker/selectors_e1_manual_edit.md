---
name: selectors-e1-manual-edit
description: Selectors, dialog field order, and nav for E1 manual program editing (edit/delete/add/reorder a day's plan on the Program tab)
metadata:
  type: reference
---

E1 = manual program editing on the Program tab. Verified on APK md5 `2c0d89aa327491257945a97a29517e2b` (v1.7.0, versionCode 35, schema v13). See [[selectors-wave3]] [[db-seeding-recipe]] [[maestro-flows-e1]].

## Where the controls live
- Program tab opens on `selectedDay = currentDayOfWeek()` (TODAY) by default — no need to tap a day chip to test today. Day chips: `tv_day_abbr` text = "Mon".."Sun"; tapping selects that day (`viewModel.selectDay(i)`).
- E1 exercise cards are inflated from **`item_day_overview.xml`** into `layout_exercises` (NOT `item_program_exercise.xml`, which is the Log-screen card). Plan-empty/rest-day hides the whole day section.
- Per-card buttons (all share an id across cards — disambiguate by `index` in list order, or by scrolling the target card's row into view):
  - `btn_edit_exercise` (text "Edit") → dialog "Edit <name>"
  - `btn_move_up` (text "↑") — `isEnabled=false` + alpha 0.35 on the TOP row (index 0)
  - `btn_move_down` (text "↓") — `isEnabled=false` + alpha 0.35 on the BOTTOM row (last index)
  - `btn_delete_exercise` (text "Delete", colorError) → confirm "Remove <name>?" / positive "Remove"
- Day-level `btn_add_exercise` (text "+ Add exercise", below all cards) → dialog "Add exercise". GONE on rest days.
- Card text to assert: `tv_exercise_name`, chips `chip_sets`="N sets", `chip_reps`="X reps", `chip_weight`="W kg" or "Bodyweight" (weight=0). `tv_notes` shows notes (GONE if blank). Cardio-classified exercises show chip_sets="cardio" and HIDE chip_weight — pick non-cardio catalog names for add tests (Plank is "Core", fine).

## Dialog fields (NO stable ids — same gotcha as E2 save dialog)
All dialog inputs are programmatic stacked `EditText`s with labels above them. Target by PREFILLED text or by `below: {text: "<label>"}`.
- **Edit dialog** field order: Sets, Reps, Weight, Notes. Prefilled with current values, so `tapOn: "<currentSets>"` → `eraseText` → `inputText`. Notes field is empty → target with `tapOn: {below: {text: "Notes (optional)"}}`. Positive button "Save".
- **Add dialog** field order: Exercise(AutoCompleteTextView, hint "Start typing an exercise…"), Sets(prefill "3"), Reps(prefill "8-12"), Weight(prefill "0"), Notes(hint "Notes (optional)"). Autocomplete: `tapOn: {text: "Start typing an exercise…"}` → `inputText: "Plan"` → `tapOn: "Plank"`. Positive button "Add". Blank name → Snackbar "Enter an exercise name.", no row added.

## Persistence / data model
- All edits go through the program-scoped repo at `weekStart = thisMonday()` for the active program (programId). thisMonday() = Monday 00:00 UTC of the current week (device TZ=GMT). Edits reflect on Program tab AND Home today-view AND survive restart. Regen-overwrite is by-design (don't test it).
- **orderInDay re-indexes contiguously** after delete/add/move (verified: delete middle → remaining are 0,1; add → appended at next index; move → swapped indices). Good sqlite3 corroboration: `SELECT orderInDay,exerciseName FROM planned_exercises WHERE dayOfWeek=? AND programId=? ORDER BY orderInDay`.
- editExercise does NOT touch isLogged/actuals (intentional — only swap un-logs). delete/add/move call saveDayPlan which rewrites the whole day.
- Home today-view = `tv_today_plan` (single multi-line TextView, below the fold — scroll to it). Format: `"• <name>  <sets>x<targetReps>" + (weight>0 ? " @ <W>kg" : "")`. e.g. `• Bench Press  5x6 @ 65kg`. Pulls getPlannedForDay(thisMonday(), currentDayOfWeek()).
