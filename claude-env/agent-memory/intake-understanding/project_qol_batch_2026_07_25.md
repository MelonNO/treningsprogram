---
name: qol-batch-2026-07-25
description: Confirmed post-v1.25.1 10-item QoL/generation/history batch at docs/intake/qol-batch-2026-07-25/ — clusters, delegated decisions, and grounded app facts learned during intake
metadata:
  type: project
---

Confirmed 10-item batch (2026-07-25, post-v1.25.1) at `docs/intake/qol-batch-2026-07-25/` (INDEX + briefs 01–10). One Q&A round; user answered every item; improvements 2b (per-gym "exercises to avoid" field) and 9c (touch-read also on strength/reps charts) explicitly accepted.

**Why:** user's daily-use pain points — misslogs, home-bench-incompatible exercise, silent generation deaths, Monday plans missing, music muting.

**How to apply:** don't re-ask the settled choices below; reuse the grounded facts for future intake questions.

Items & clusters: A = 01 delete-set-midworkout (delete-only + "are you sure") + 07 warm-up chip auto-clears after EVERY logged set + 10 moved-workout finish = normal finish (rebalance untouched) — all share LogWorkoutFragment, one worker. B = 05 gen must survive minimize (symptom: silent, no plan saved; screen-off foreground works) + 06 Monday plan ready WITHOUT opening app AND launch trigger broken for user (chose b+c, declined generate-on-week-finished). Hazard: 02 shares AiRepository.kt with B. 04 = History sub-tab → monthly week-browser (biggest; search+date-filter MUST survive; per-set delete + edit-date NOT required; same-tap combined performed+info details; UI shape delegated). 03 calories: completion summary + Recap + Stats weekly, 75 kg fallback, rough OK. 08 rest-timer chime over music (found: double sound fire + zero audio-focus handling). 09 BW chart follows range picker (today it ignores it) + scrub w/ vertical line snapping to nearest RAW weigh-in.

**Settled/delegated decisions:** 2c strictness method-delegated ("you choise") → I chose HARD guarantee (excluded exercise never in saved plan), veto-able. No in-place set editing (delete only). Not on Home for calories. Rebalance behavior sacred ("stay the same").

**Grounded app facts (as of v1.25.1) useful for future intake:** auto-gen trigger is Activity onCreate-only, Monday-keyed, skips if week has ANY plan rows, 3-failed-launches cap — resumed-from-recents app never re-triggers (prime suspect for 06c). Warm-up exists ONLY as per-set WorkoutSet.isWarmup; chip is sticky; warm-up-only session = deleted on complete. Gen runs on UI-owned coroutines, no WorkManager/foreground service anywhere except RestTimerService. GymPreset has equipment JSON + free-text notes (both reach prompt) but NO per-gym exercise exclusion; "blacklist" in AiRepository is anti-churn advisory, not exclusion. Chest-supported row comes only from AI free text (resolver-aliased to Dumbbell_Incline_Row). No calorie/energy code at all; sessions store durationMinutes; weigh-ins = body_measurements. History sub-tab = flat list w/ search + range picker + per-set delete ×; Program past-week swipe shows PLAN targets, never actuals. BW chart custom View, no touch, ignores progressDateRange (which filters only strength/reps). Rest timer completion: Ringtone.play + IMPORTANCE_HIGH channel default sound (double), no audio focus.

Related: [[feedback-method-delegation]], [[plain-language-questions]], [[intake-doc-format]].
