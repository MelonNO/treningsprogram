---
name: selectors-wave1
description: Stable selectors and navigation paths for Wave 1 feature screens (C1 e1RM trend, C4 recovery, E3 library, B3 stall)
metadata:
  type: reference
---

Selectors/nav verified from source (feature batch 2026-06, Wave 1). Bottom nav tabs: Home, Program, "Stats" (historyFragment), Profile (settings).

## C4 recovery (Home tab)
- MaterialCardView `card_recovery` titled "Muscle Recovery", containing vertical list `layout_home_recovery`.
- Each row = colored dot + group name + subtitle + state label. State labels (exact text): `Recovering` / `Ready` / `Overdue` / `Untrained`.
- Bands ([[MuscleRecovery]] domain): RECOVERING <48h, READY 48h–7d, OVERDUE >7d, UNTRAINED if never trained (lastTrainedMs null). Colors: amber/green/red/grey.
- Groups shown: Chest, Back, Legs, Shoulders, Arms, Core (fixed list; untrained ones still appear as Untrained).

## E3 exercise library (Profile/Settings → "Exercise Library" row `row_exercise_library`)
- Opens ExerciseLibraryFragment: search `et_search`, dropdowns `dd_muscle` ("All muscles") + `dd_equipment` ("All equipment"), count `tv_count` ("<N> exercises"), RecyclerView `rv_exercises`, empty `tv_empty`.
- Catalog = `assets/exercise_db/exercises.json`, **873 entries** (far > 26). All have images; **5 lack instructions** (e.g. "Iron Cross", "Push Press", "One-Arm Kettlebell Swings") → use for graceful-degradation check: detail shows "No instructions available."
- dd_muscle values are capitalized RAW muscle names from the JSON (Chest, Biceps, Quadriceps, Abdominals, Shoulders, Lats, Triceps...). dd_equipment values capitalized (Barbell, Dumbbell, Cable, Machine, Body only, Bands, Kettlebells...).
- Detail (ExerciseDetailFragment): `tv_name`, `iv_image` (hidden if no/failed image), `tv_muscles` ("Target: ...\nSecondary: ..."), `tv_equipment` ("Equipment: ..."), `tv_meta` (level • category), `tv_instructions` (numbered or "No instructions available.").

## C1 e1RM trend + PR history (Stats → Recap sub-tab, TabLayout pos 0)
- IMPORTANT: Recap tab has NO standalone exercise cards. It has a session dropdown (`acSession`/`til_session`) and a "Vs. last time" card listing exercise ROWS — each row is tappable → opens RecapTrendsFragment. Tip text "Tip: tap an exercise above to see its trend over time." is at the BOTTOM totals card.
- RecapTrendsFragment: title `tv_trend_title` = exercise name; e1RM chart `chart_trend_e1rm` OR empty `tv_trend_e1rm_empty` ("Log a couple of working sets...") shown when <2 trend points; single "Estimated 1RM: ~N kg (estimate, Epley formula)" `tv_trend_e1rm`; PR list `layout_trend_pr_history` (rows: date + "<weight> kg × <reps>"; empty = "No PR history yet — log a few sessions to build it.").
- PR history uses ALL history (not the chip-windowed view); trend chart uses windowed. PR = session whose Epley e1RM strictly exceeds all prior. Warm-ups excluded upstream in `getStrengthHistory` (isWarmup=0).

## B3 stall alert (Stats → Progress sub-tab, TabLayout pos 2)
- MaterialCardView `card_stalled` titled "Plateau detected", list `layout_stalled`. Each = exercise name + suggestion (deload / rep scheme / variation). Card only VISIBLE when ≥1 lift stalled.
- [[StallDetector]]: STALL_WINDOW=3 sessions, IMPROVEMENT_EPSILON_KG=0.5. Stalled = last 3 sessions' e1RM (Epley) never improves by >0.5kg. <3 sessions never flagged. Double-progression (reps rising at same weight) NOT flagged because Epley rises with reps.
