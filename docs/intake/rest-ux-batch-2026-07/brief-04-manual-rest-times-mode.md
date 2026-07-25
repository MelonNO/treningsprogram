# Brief 04 — Let the user choose AI rest times or their own per-category rest times

**Type:** Feature
**Cluster:** A (rest-timer machinery) — build **first**; Brief 02 layers on top. Same worker.

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
Today the only live source of rest times is the AI's per-exercise suggestion (`PlannedExercise.recommendedRestSeconds`; 90 s fallback in `LogWorkoutViewModel`). There is **no user-visible rest-time setting anywhere** — `PreferencesManager.restTimerSeconds` exists but is wired only into backup/export, not into any screen or into the timer. The rest sheet (`RestTimerBottomSheet`) shows the line "AI suggested: m:ss". Training settings live in `SettingsTrainingFragment`; first-run setup is `ui/setup/SetupWizardFragment` (step 1 holds training days + session duration). Workout generation enforces a session-duration window using its own deterministic time estimate, which counts per-exercise rest (`domain/WorkoutTimeEstimator`, and the prompt/budget machinery in `AiRepository`).

## What the user wants (end result)
A choice between two rest-time modes:

1. **AI suggestions** (default, current behavior): each exercise's rest timer uses the AI's per-exercise recommendation.
2. **My own times:** the user sets one rest time per exercise category, and the rest timer uses the category time for every exercise instead of the AI's suggestion.

Details, all user-confirmed:

- **Categories (two):** **Heavy compounds** (heavy compound work — squat/bench/deadlift/row/press type lifts) and **Accessories** (everything else).
- **Control shape:** a checkbox/toggle that, once checked, **reveals the input fields** for the specific times; unchecked = AI mode, fields hidden.
- **Defaults pre-filled:** 3:00 for Heavy compounds, 1:30 for Accessories, in m:ss form (the user approved this sample).
- **Available in BOTH places:** the setup wizard (on the same step as training days / session duration) and Settings → Training. Both edit the same stored preference.
- **Rest sheet label reflects the source** (accepted suggestion): AI mode shows "AI suggested: m:ss" as today; manual mode shows the user's time labeled as theirs, e.g. "Your time: 3:00" — so the user always knows which mode is active.
- **Generation must respect manual times (user-flagged as important):** when manual mode is on, workout **generation's session-duration math counts the user's rest times**, not the AI's suggested rest values — so generated plans still genuinely fit the configured session duration when rests are actually taken at the user's times.

## Acceptance criteria
- Done when Settings → Training shows the mode toggle; checking it reveals two labeled time fields (Heavy compounds, Accessories) pre-filled 3:00 / 1:30; unchecking returns to AI mode; the choice and times persist across app restarts.
- Done when the setup wizard's training step offers the same toggle + fields, and a choice made in the wizard shows up in Settings → Training (and vice versa).
- Done when, in manual mode, completing a set on a heavy compound starts the rest timer at the Heavy-compounds time and on any other exercise at the Accessories time — regardless of what the AI suggested for that exercise.
- Done when, in AI mode, behavior is exactly today's (AI per-exercise time, 90 s fallback).
- Done when the rest sheet's label says "AI suggested: …" in AI mode and "Your time: …" (or equivalent clearly-owned wording) in manual mode.
- Done when, in manual mode, a newly generated program's estimated day durations are computed with the user's category rest times, and plans still land inside the configured session-duration window under that math. (Behavioral generation checks are live-gen-only — user verifies on device; keep any live-API verification frugal.)
- Done when the manual times are included in backup/export/import like other training preferences.

## Scope and constraints
- **In scope:** the mode preference + two category times; wizard and Settings surfaces; the rest timer's base-time resolution; generation's duration math honoring manual times; the rest-sheet source label.
- **Out of scope:** per-exercise custom times, more than two categories, changing what the AI *recommends* (its suggestions remain stored and return the moment the user switches back to AI mode); redesigning the rest sheet beyond the label.
- **Interaction with Brief 02:** the mid-session +30/−30 adjustment layers on top of whichever mode is active, with identical session-only semantics.

## Decisions baked in
- Two categories, named "Heavy compounds" and "Accessories" (Q4a).
- Defaults 3:00 / 1:30, m:ss presentation (Q4b).
- Wizard placement: the training-schedule step (Q4c).
- Source label change accepted (suggestion).
- Generation duration math must use manual times when active (Q4b addition, user-flagged).

## Assumptions (user may override)
- **A1:** Default mode for existing installs and fresh installs is **AI suggestions** (manual mode is opt-in).
- **A2:** Cardio/duration-style entries and warm-ups — where a rest timer barely applies — fall in the **Accessories** bucket if their timer fires at all; no third category is introduced.
- **A3:** Which exercises count as "heavy compounds" is derived automatically from the exercise itself (the user never tags exercises by hand); the builder grounds this in the app's existing exercise/muscle/role knowledge.

## Considerations for whoever builds it
- Input validation: keep the m:ss fields forgiving (sane bounds, no crash on blank/garbage; falling back to the defaults is fine).
- The wizard step already carries several controls; keep the revealed fields compact so the step stays scannable.
- Generation side: the deterministic duration gate and the prompt's declared rest metadata should stay mutually consistent under manual times — flag, not prescription.
