---
name: rest-ux-batch-2026-07
description: v1.22.0 manual rest-time mode + sticky ±30 rest adjustment + persistent exercise timer + globe removal — key seams, semantics, and the 1-call live proof
metadata:
  type: project
---

**SHIPPED v1.22.0 (2026-07-02, commit 19d69df, tag+release+asset API-verified live; DB unchanged v18, backup schema v4→v5, 712 tests +27).** Briefs at `docs/intake/rest-ux-batch-2026-07/` (item 3 withdrawn). Single orchestrator pass, no workers.

**Why:** user wants control over rest pacing (own category times), the ±30 press to stick per exercise, and the exercise timer to stop resetting on minimize.

**Key seams (for future work in this area):**
- `domain/RestTimePolicy.kt` — `ManualRestTimes(heavy,accessory)` + automatic heavy-compound name classification (cardio→accessory; NOT_HEAVY overrides: pallof/calf/pressdown/wrist/rowing/rower/erg) + forgiving `parseMinSec`/`formatMinSec` (floor 15 s, cap 600 s).
- `PreferencesManager.manualRestTimes` (null = AI mode) is THE switch every consumer keys off: LogWorkoutViewModel.resolveRestStart (pure), WorkoutTimeEstimator (optional `manualRest` param), AiRepository (injected prefs; gate + salvage trim + prompt of BOTH weekly and single-day paths), ProgramFragment "~Xm" labels (via ProgramViewModel.manualRestTimes so displayed = enforced math).
- In manual mode: trimOverflowToWindow SKIPS the rest-down lever (dead lever — would corrupt stored AI suggestions for zero minutes); dayDurationFeedback takes `restIsLever=false` and steers reps/sets/exercises instead; prompt states fixed rests + the classification rule verbatim so the model can mirror the Kotlin categories.
- Item 2 stickiness = `sessionRestAdjustments: Map<exerciseName, netSeconds>` in LogWorkoutViewModel, fed by RestTimerBottomSheet via `viewModels(ownerProducer={requireParentFragment()})` (same VM instance as LogWorkoutFragment). In-memory ON PURPOSE (never persisted, dies on process death — user-chosen semantics).
- Item 5 = persisted `prefs.exerciseTimerState` "sessionId|index|startMs" resolved by pure `resolveExerciseTimerStart`; the resolver-collector in the VM is gated on `_planLoaded` so the transient index-0 emission during resume can't clobber the stored start. Cleared on complete/abandon.
- Backup v5: three manualRest fields, stamp-only migration V4_TO_V5 (Gson fills absent keys from the all-default data-class constructor — verified pattern). NOTE: bumping CURRENT_BACKUP_VERSION breaks TWO pinned tests (BackupV4PrefsTest AND E2BackupProgramsTest both assert the number).

**Live proof (1 API call of the 10 budget):** rebuilt the throwaway harness from [[reference-live-gen-harness]] (buildPrompt is now **23 args**), manual 180/90 @ 50-min Hypertrophy/4-day → PASS attempt 1, all days 46–59 in 40–60 window; model even echoed 180/90 as recommendedRestSeconds. Borderline category mismatches (model called "Dumbbell Incline Press"/"Cable Seated Row" accessories, Kotlin calls them heavy) push the recount UP (benign — over gets auto-trimmed; under is the fatal direction). Harness deleted after use.

**How to apply:** any future rest-related change must go through resolveRestStart/RestTimePolicy, not the call sites; if a third rest category or per-exercise times are ever requested, ManualRestTimes.restSecondsFor is the single dispatch point.
