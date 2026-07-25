---
name: bugsweep-ux-wave2
description: Bug-sweep batch Wave 2 (SW-B) — S3 AI generation/parsing + S2 Program tab; dispatch, fixes, verification record
metadata:
  type: project
---

Wave 2 = SW-B remainder of the bug-sweep batch (docs/intake/bug-sweep-ux-2026-06). Built on branch `wave1-integration` on top of Wave 1. SEQUENCE rule: SW-B is the HOTTEST seam (AiRepository/WorkoutRepository) — run S3 then S2 SERIALIZED (one at a time), never blind-concurrent on the seam. Coordinator released Wave 2 after its own read-only Wave 1 confirmation.

**S3 (AI generation/adaptation/coaching) — AiRepository.kt only.**
- Hardened JSON parsing (root cause of Wave-1's intermittent "No JSON found"): old extractJson matched a fence only if BOTH ``` present → opening-only/truncated/prose-wrapped responses failed. New package-level internal fns: stripJsonFences (complete OR opening-only), balancedJsonSpan (brace-depth + string/escape aware), stripTrailingCommas (gson throws on trailing comma in OBJECT, materializes phantom null in ARRAY), extractJsonOrThrow. Old extractJson is now a 1-line delegate → ALL 4 call sites (onboarding/generate/validate/single-day) get it. HARD INVARIANT (tested + I verified): genuinely-unparseable input STILL throws "No JSON found" — never a silent empty plan.
- Fixed empty-plan silent-success: `{"days":[]}` passed duration check vacuously + validator defaults accepted=true → empty plan SAVED. Now empty plan = rejected attempt → retries → throws "Program rejected" → existing onFailure msg.
- B1 trigger cadence / B2 rationale-hidden-when-blank / B3+C4 prompt inputs reviewed = correct-by-design (not changed).
- Tests: S3ParsingTest.kt (30) incl. must-throw battery.

**S2 (Program tab) — ProgramViewModel/WorkoutRepository/DeloadPolicy.**
- HIGH lost-edit race: deleteExercise/addExercise/moveExercise did read-modify-write on a STALE derived StateFlow snapshot in independent coroutines → rapid taps clobber. Fix: new `WorkoutRepository.editDayPlan(weekStart,day,transform)` serialized by `dayEditMutex` + reads freshly-persisted rows INSIDE the locked db.withTransaction; field edits via `editPlannedExerciseFields` under same mutex. All 3 structural editors + editExercise rerouted.
- Deload dropped on same-week re-regen: nextDeloadState's "already deloading→exit" models a WEEK TRANSITION, but regenerateFullProgram can run twice in one week → 2nd tap flipped deload off mid-week. Fix: `DeloadPolicy.nextDeloadStateForRegen(currentlyDeloading,stalledCount,replacingCurrentWeek)` — keep deload when currentlyDeloading && replacingCurrentWeek (signal = getActiveProgramPlanForWeek(monday).isNotEmpty()).
- Visual: rationale blanked after manual edit on single-workout-day program (saveDayPlan derived rationale only from OTHER days). Fix: fallback to edited day's own rationale.
- Correct-by-design (not changed): delete-active-program guards+promotes, switch-mid-week reactivity via flatMapLatest, saveCurrentAsProgram fresh periodization, weekInBlock DST drift (<168h, prompt-only).
- Tests: S2ProgramTabTest.kt (8).

**JVM gate (orchestrator's own clean run):** 383 tests, 0 fail (Wave1 345 → +30 S3 → +8 S2). assembleDebug green. Scope-clean: AiRepository untouched by S2; Wave 1 ProgramFragment nav guard intact. APK for on-device md5 e058a93e.

**Carry-forward into later waves:** none new beyond Wave 1's (S7 import merge-vs-wipe still pending user product decision). The overwrite-on-regenerate "surprise" is a Phase-2 UX candidate (UX1/Wave 4), not a defect.

See [[bugsweep-ux-wave1]], [[ship-handoff]], [[relayed-consent]], [[reference-ondevice-test-harness]].

---

## BATCH COMPLETE (2026-06-24) — all-clear given to coordinator at code/test bar (on-device DROPPED for this ship)

Full batch implemented on branch `wave1-integration` (10 commits, nothing on main). Waves: 1 (F3/U1/U3/S1/S4/F2/F1/S6/S7/S8), 2 (S3,S2), UX1 (richer Recap graphs + PR coherence), U2 (XP log, Room 13->14), U3 (already in Wave 1).
- **UX1 scope judgment:** brief had a user approve/cut gate; coordinator relay said "build the redesign". I built ONLY the confirmed low-risk subset (richer Recap graphs reusing StrengthChartView; PR terminology coherence; empty states). Deferred the subjective IA/restyle to the post-ship list (did NOT impose an open-ended redesign on a relay).
- **Whole-batch gate (my own clean run):** clean assembleDebug + assembleRelease + test = BUILD SUCCESSFUL, 804 execs / 0 fail (402 distinct). Release APK md5 81a437a7. Version recommendation = **v1.9.0** (MINOR; versionCode 36->37) — net-new features (XP log, richer Recap, recovery rework) + bug sweep. I do NOT bump the version myself; shipper applies it.
- **On-device DROPPED from gate** (user process change) → ship is code/test-verified only; a background ui-test-worker ran non-blocking (findings opportunistic). POST-SHIP Waydroid pass → follow-up patch.
- **Post-ship Waydroid scrutiny list (handed to coordinator):** (1) U2 Room 13->14 migration on a REAL upgraded v13 device (JVM can't exercise real migration); (2) UX1 Recap graphs render on real data/empty/single-point; (3) S2 rapid-edit race under real touch timing + the same-week deload path; (4) F3 true network-OFF (harness can't cut network); (5) U2 XP capture end-to-end (complete a workout -> event appears) + XP-bar tap from both Home & Profile.
- **Open product item (NON-blocking):** Finding 1 backup import = MERGE was CLOSED by user decision (keep merge); brief annotated. UX1 subjective items await user approve/cut post-ship.

## Opportunistic background on-device coverage (folded in pre-ship, 2026-06-24/25)
A NON-BLOCKING background ui-test-worker verified S2+S3 on the Wave-2 APK (md5 e058a93e — NOTE: predates UX1+U2, so those remain on-device-UNVERIFIED → top of post-ship list). Results: ZERO defects any tier.
- **S2 rapid-edit race fix HELD on-device with DB proof:** 12/16 parallel arrow taps + delete-interleaved-with-reorder → planned_exercises orderInDay stayed contiguous (0..n-1), nothing lost/duplicated, visible list == DB. The editDayPlan mutex works on a real device.
- S2: delete-active→promote, can't-delete-last, switch-no-merge (plan COPIED not merged), field-edit persist across restart/rotation, unsaved-edit discarded on process-death (no partial persist), same-week deload PRESERVED, empty/first-run — all PASS.
- S3: failure path clean ("No JSON found" Snackbar, spinner clears, existing plan preserved, no hang/blank); B1/B2 readouts correct. Live-AI SUCCESS generation BLOCKED on Waydroid (consistent 200-but-rejected gen flakiness — not an app defect; failure path correct). A 153s generate call completing is F3 WORKING (within new 180s read/240s call timeout), not a regression.
- Tester added 7 untracked flows/s2_*.yaml (artifacts); cleaned its screenshots. Standing concern: a ui-test-worker briefly touched app files mid-run once (out of its test-only lane) — had zero effect on committed tree; watch for it.
