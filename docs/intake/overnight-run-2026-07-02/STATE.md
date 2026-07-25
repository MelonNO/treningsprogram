# Overnight run 2026-07-02 — crash-recovery checkpoint

**Purpose:** if the Pi crashes, a fresh Claude session reads THIS FILE plus memory
(`~/.claude/projects/-home-migul-treningsprogram/memory/MEMORY.md`) and resumes from the
last completed checkpoint. Update this file at EVERY phase transition.

## Authority (from the user, 2026-07-02 ~05:45 local, user asleep)
- Coordinator has COMPLETE AUTONOMOUS AUTHORITY per user intention until the user returns.
- Anything requiring the user: continue without it, note it in the final report.
- Live Anthropic API budget: ≤15 calls for releases 1+2 (≤10 for release 1), PLUS a separate
  20-call budget for phase 3 (full test stage) — granted by the user 2026-07-02 ~05:55.
- **Waydroid/on-device testing IS AUTHORIZED for tonight's phase 3** (explicit user
  instruction — temporary override of the standing skip-waydroid rule for this run only).
- Ship both releases without further confirmation. Report everything in the morning.

## The plan (3 phases + report)
1. **Release 1** — rest-ux batch (docs/intake/rest-ux-batch-2026-07/, briefs 01/02/04/05).
   Orchestrator run #1. Ship as own version, verify live.
2. **Release 2** — feature research + gamification (docs/intake/feature-research-2026-07/,
   R1–R7; BACKLOG.md is docs-only). Orchestrator run #2, dispatched only after release 1 is
   verified live. Ship as next version, verify live.
3. **Full app test** — after release 2: whole-app test sweep including ON-DEVICE via
   Waydroid+Maestro (see memory `reference_ondevice_test_harness` for bring-up, freeze
   gotchas, MD5/stale-APK discipline; existing flows in `flows/`). Dispatch ui-test-worker.
   Fix-worthy findings: small/clear bugs may be fixed + shipped as a patch release;
   ambiguous ones go in the report.
4. **RUN-REPORT.md** in this directory + chat summary + memory updates.

## Checkpoint log (append-only, newest last)
- [05:50] Phase 1 IN PROGRESS. Orchestrator #1 dispatched on rest-ux batch (removing
  Home sphere, session rest memory, manual rest mode incl. generation math, robust
  exercise timer). Working tree shows active edits (LogWorkoutViewModel/Fragment,
  PreferencesManager manualRest*, ManualRestTimes domain, exerciseTimerState). Baseline
  before batch: v1.21.0 shipped (tag eb4da13), 685 tests green (H5 flake known).
  Release 2 briefs READY at docs/intake/feature-research-2026-07/ (INDEX + R1–R7 + BACKLOG).
  Phase 2 NOT dispatched yet. Phase 3 not started.

- [06:10] Phase 1 still in flight, good progress: 19 files modified under app/. Visible in
  tree: sphere removed from Home hero; item-2 session rest memory (getRestStart in
  LogWorkoutFragment); item-4 manual rest mode (PreferencesManager manualRest* prefs,
  ManualRestTimes domain type, ProgramViewModel exposes it for ~Xm label consistency,
  backup schema v5 with V4_TO_V5 migration); item-5 persisted exercise timer
  (exerciseTimerState pref, wall-clock flow rework). Not yet committed/tested/shipped.

- [07:00] **PHASE 1 COMPLETE — v1.22.0 SHIPPED + coordinator-verified live** (commit 19d69df,
  tag v1.22.0, asset treningsprogram-v1.22.0.apk uploaded, working tree clean). 712 tests
  green (0 failures, H5 passed this run). Backup schema v5. API calls used: 1 of 15
  (manual-rest generation math proven live, pass attempt 1). All four briefs CONFIRMED by
  orchestrator #1; judgment calls in its report (heavy-lift patterns, 0:15 floor, m:ss parse,
  adjustment keyed by exercise name). Next: dispatch orchestrator #2 on
  docs/intake/feature-research-2026-07/ (R1–R7), budget ≤14 calls, ship as next version.

- [12:15] **PLAN RESTRUCTURED by user (asleep): a NEW STAGE 3 was added** — 16 UX items
  (docs/intake/stage3-ux-2026-07/ once written; understander re-dispatched, no confirmation
  round, unclear items → SKIPPED-UNCLEAR in index). Stage 3 ships as ITS OWN version after
  release 2. **The full test (Waydroid) is now STAGE 4.** Also: SESSION LIMIT interrupted
  orchestrator #2 overnight (reset 09:10 Oslo); it had committed milestone 69b86bb
  (WorkoutResult contract) with a clean tree. RESUMED via SendMessage at 12:11 CEST —
  phase 2 (R1–R7 → release) back in flight. A /loop watchdog is armed: task-notifications
  are the primary wake signal, ScheduleWakeup ~30 min as the fallback heartbeat re-entering
  the loop with the stage-3 instruction. API spend so far: 1 call (release 1).

- [12:25] **Stage-3 briefs READY**: docs/intake/stage3-ux-2026-07/ — INDEX + 16 briefs, ALL
  items briefed (none skipped), assumptions A-01a…A-16a labelled for morning veto. Notable
  interpretations: heatmap cell → most recent session that week training that muscle (A-11a);
  achievement→session attribution timestamp-based, omit-when-unsure (A-14a); Profile briefs
  written against post-release-2 state. Cluster guidance: H1 recap (3→9→14→10), H2 (12/13/1),
  H3 (11/15), H5 (2 last), P (4/5), T (6/8), standalone 7, 16. Orchestrator #3 dispatch
  WAITS for release 2 live. Meanwhile orchestrator #2 confirmed mid-R2 (notification center:
  NotificationGate + streak/weigh-in receivers visible in tree).

- [17:13] **SESSION CRASH RECOVERED; USER IS BACK (awake, available for questions).**
  Ground truth re-verified: v1.22.0 live; R1–R6 committed on feature-research-2026-07
  (…881c4f1); R7 partial + uncommitted (BeatTarget.kt, WorkoutRepository, LogWorkoutViewModel);
  release 2 NOT shipped; TaskList empty (orchestrator #2 died with the session).
  **Orchestrator #2b dispatched** to audit+finish R7, spot-check R1–R6, build+test, ship
  release 2 (own version, ship pre-authorized), verify live, checkpoint here. Stage-3
  assumptions (A-01a…A-16a) presented to the awake user for veto; orchestrator #3 dispatch
  still WAITS for release 2 live + user's assumption verdict. API spend: 1 of 15 (rel 1+2
  budget), ≤14 remain. Stage-4 Waydroid re-authorization question raised with user (original
  grant was "tonight only").

- [17:46] **PHASE 2 COMPLETE — RELEASE 2 SHIPPED AND VERIFIED LIVE: v1.23.0** (orchestrator
  #2b). R7 audited + finished (partial work had a duplicate WorkoutRepository method =
  compile error, reverted; checkPrPreview was unwired; UI chip/flare + BeatTargetTest were
  missing — all completed: chip derives from currentExercise×sets, flash wired into
  logSet+logFreestyleSet with resumed-session dedup). R1–R6 spot-checked against briefs:
  all sound (R1 StreakPolicy shared live+recompute, applyStreakFreshness on foreground;
  R2 four toggles + A-N1 defaults + eager alarm cancel + gated GenerationNotifier; R3 prompt
  line in both gen paths, ""-when-no-data; R4 26-template pool/9 adaptive + PerfectWeek
  once-per-week live + recompute parity; R5 full meta-coverage test + nextUp strip;
  R6 celebration surface with prDetails old→new + both exits intact). Tests: **803 green,
  0 failures, BOTH debug+release variants** (baseline 712 → +91; H5 flake did not fire).
  Release commit bd35ad9 on main (ff, remote verified), R7 commit e6b4a46, tag v1.23.0,
  release id 348139409, asset treningsprogram-v1.23.0.apk (103546955 bytes, state
  uploaded, local md5 6444136ce37494d163083723a3ce5c75) — tag+release+asset independently
  API-verified. **API calls used this phase: 0** (nothing decided by live gen; R3 is
  prompt-context only, unit-verified). Budget stands at 1 of 15 used. DB unchanged v18.
  Commit discipline held: only R7 app sources + build.gradle.kts staged; unrelated tree
  changes untouched. NEXT: orchestrator #3 (stage-3 UX) waits on user's assumption verdict;
  stage-4 Waydroid needs re-authorization (original grant was "tonight only").

- [17:49] **v1.23.0 coordinator-verified live** (tag on remote → bd35ad9; release 348139409
  not draft; asset uploaded, byte-size match). **STAGE 3 DISPATCHED: orchestrator #3** on
  docs/intake/stage3-ux-2026-07/ (16 items, cluster plan per INDEX, new branch off bd35ad9,
  own version — ship pre-authorized by user instruction). User awake; assumptions A-01a…A-16a
  presented, none vetoed yet — briefs authoritative, vetoes relayed mid-flight if they come.
  API spend still 1 of 15; stage 3 expected 0 calls. NEXT after stage 3 ships+verifies:
  stage 4 full test (Waydroid authorization to re-confirm with user).

- [18:38] **STAGE 3 COMPLETE — SHIPPED AND VERIFIED LIVE: v1.24.0** (orchestrator #3).
  All 16 stage3-ux items implemented per INDEX cluster plan (H1 3→9→14→10 order held;
  H5 skeletons built last). Single release commit fba7c08 on main (ff from branch
  stage3-ux-2026-07, remote-verified fba7c08), tag v1.24.0, release id 348169484,
  asset treningsprogram-v1.24.0.apk (103559896 bytes, state uploaded, byte-size match,
  local md5 195294a5744832105ae5adf074f664e2) — tag+release+asset independently
  API-verified. Tests: **830 green, 0 failures, BOTH debug+release variants** (803 → +27
  new in Stage3UxBatchTest; H5 flake fired once on first release run, clean on re-run =
  documented flake, not a regression). New seams: domain/{DateRangeFilter, RepsProgress,
  RecentPrs, FineMuscleVolume, SessionEarned, HeatmapDrill}, ui/common/Skeleton,
  ui/log/TouchObservingLinearLayout (log-screen root now dispatch-observing). DB unchanged
  v18, backup format untouched, 0 API calls (budget still 1 of 15). Also backfilled the
  MISSING v1.23.0 Changelog entry (release 2 gap) alongside the v1.24.0 entry. Commit
  discipline held: only app/ staged (40 files); unrelated tree changes untouched.
  NEXT: stage 4 full test — Waydroid authorization must be re-confirmed with the user
  (original grant was "tonight only", now expired).

- [18:41] **STAGE 3 COMPLETE — v1.24.0 SHIPPED + coordinator-verified live** (remote main =
  tag v1.24.0 = fba7c08; release 348169484 not draft; asset uploaded, 103559896 bytes exact;
  app/ tree clean). 16/16 items CONFIRMED by orchestrator #3 (830 tests both variants,
  +27; 0 live calls; full ledger + judgment calls in its report — timestamp achievement
  attribution, ±2.5 keypad exemption, v1.23.0 changelog backfill). **STAGE 4 DISPATCHING:
  ui-test-worker full-app sweep incl. Waydroid** (authorized for this run per the staged
  plan the user reconfirmed today; harness per memory reference_ondevice_test_harness;
  test the RELEASED v1.24.0 APK, md5 195294a5744832105ae5adf074f664e2; ≤20-call live budget
  for this stage). Worker TESTS ONLY — fix-worthy findings come back to coordinator →
  orchestrator patch release. After stage 4: RUN-REPORT.md + final summary.

- [22:12] **STAGE 4 interrupted by SESSION LIMIT at ~19:38 (reset 22:10) — worker RESUMED
  via SendMessage.** Progress before cutoff (STAGE4-FINDINGS.md): harness up (Waydroid+
  Maestro, release APK md5-verified, clean install), live onboarding gen PASS (2/20 calls),
  guided log 16 sets → R6 celebration → Recap/Stats/Progress sweep largely PASS (heatmap
  tap, range picker, BW reps chart, finer muscles, CSV gone all PASS). FINDINGS so far:
  **F3 HIGH likely-regression — History list stuck on skeletons until a search binds it**
  (100% repro, animations on+off, data intact); F1 MEDIUM keypad outside-tap doesn't
  dismiss (Waydroid-IME caveat, needs real device); F2 COSMETIC "0 kg x 6" for BW exercise
  in Recap. Worker verified test-only (app/ tree clean). Remaining: R7 2nd-session PR flash,
  History range picker, Profile 4/5, Settings 6/7/8, R2 toggles, v1.22.0 rest-ux regression,
  backup round-trip. Fix decision AFTER full report (one patch release for all fix-worthy).

- [23:35] **STAGE 4 COMPLETE** (worker #1 killed by session limit at 19:38, worker #2 resumed
  22:12 off the durable findings log, finished 23:33; device left clean; 2/20 live calls
  total). Whole-stage ledger in STAGE4-FINDINGS.md. Verdicts: releases broadly SOLID —
  onboarding+live gen, guided log→celebration→recap chain, stats/heatmap-tap/range-pickers/
  BW-reps-chart, settings 6/7/8, R2 toggles, backup round-trip (merge idempotent, no dup),
  achievements 17/200 (old orphan bug gone) all PASS. FINDINGS: **F3 HIGH regression —
  History list permanently stuck on skeletons, NO workaround (search-bind was a race),
  History-only**; F2 cosmetic BW "0 kg × 6" in Recap; F1 keypad-dismiss inconclusive
  (Waydroid IME, needs real device); item-4 LOW product question (first-ever lifts don't
  count as PRs → 7-day card empty after baseline session). HARNESS LIMITS hit: R7 2nd-session
  beat/flash + rest-ux timer-persistence/session-rest-memory untestable (clock can't advance,
  release APK not debuggable → no DB seeding) — user checks on real device. Harness gotchas
  saved to reference_ondevice_test_harness memory. **ORCHESTRATOR #4 DISPATCHED: patch
  release (F3 root-cause fix + regression test, F2 cosmetic, F1 code-review-only) →
  v1.24.1-ish, ship pre-authorized.** After it ships: RUN-REPORT.md + final summary + memory.

- [00:15+] **USER DECISION A1: first-ever lifts do NOT count as PRs** (stage-4 item-4
  observation closed as by-design; "PRs · Last 7 Days" stays strict). No code change.
  Remaining open: F1 keypad + on-device checklist (user's phone), C1/C2 observe-over-time,
  orchestrator #4 patch in flight.

- [01:05] **ORCHESTRATOR #4 DONE — v1.24.1 SHIPPED + API-VERIFIED LIVE** (commit 3b05561
  on main = tag v1.24.1; release id 348318068 not-draft; asset treningsprogram-v1.24.1.apk
  103560224 bytes = local APK byte-for-byte; md5 94a5ff7a21108b588ad059f014a45c3e).
  **F3 FIXED**: root cause = v1.24.0 made filteredSessions a DOUBLE stateIn(WhileSubscribed)
  chain (timeline stateIn -> combine -> stateIn) threading a null loading sentinel through
  the combine; on-device (release) that chain never delivered its first emission and the
  null-returning transform made every user action a no-op -> permanent skeletons. Collapsed
  to a single sharing layer (combine reads the DAO flow directly; null only as stateIn
  initial; filter extracted to pure domain/HistorySearch). Verified in shipped dex: no
  intermediate stateIn between getHistoryTimeline() and the combine array. Secondary: search
  empty-state copy + date-only hint fixed. **F2 FIXED**: Recap row shows "BW x reps" when
  topWeightKg==0 (sibling surfaces safe - PR rows/strength history exclude 0 kg by query).
  **F1 NO DEFECT**: tester exercised the reps field's SYSTEM IME - explicitly out of scope
  per brief-16 (which targets the custom weight pad); code matches brief; user should check
  the WEIGHT pad tap-outside on a real device. 840 unit tests green both variants (+10:
  HistorySearchTest 7, F3HistoryListChainTest 3). 0 live API calls. On-device confirmation
  of F3/F2 -> user, on update.

- [00:20] **RUN COMPLETE. v1.24.1 SHIPPED + coordinator-verified live** (main = tag =
  3b05561; release 348318068; asset 103560224 bytes byte-exact, md5 94a5ff7a…). F3
  root-caused (double stateIn share never delivered first emission → unrecoverable null)
  and fixed structurally + 10 regression tests → 840 green both variants. F2 fixed
  (BW × reps). F1 = test artifact (tester hit the reps system-IME, which brief-16 scopes
  out; weight-pad code correct, device-unverified). A1 ratified (firsts = baselines).
  **RUN-REPORT.md written (this directory). Memory closed out. Nothing further in flight;
  remaining items are the user's on-device checklist.**

## Recovery instructions (for a fresh session)
1. Read MEMORY.md + this file. Check `git -C /home/migul/treningsprogram log --oneline -5`
   and `git status --short app/` to see which phase actually completed (a shipped release
   = tag + GitHub release exist; verify via the GitHub API, procedure in memory
   `reference_release_process`).
2. If phase 1 unshipped but working tree has coherent rest-ux edits: run
   `./build.sh assembleDebug test > log 2>&1; echo $?` (NEVER pipe to tail — masks exit
   code), inspect, finish per briefs, ship per release process.
3. Then continue phases 2 → 3 → report as above. Respect the API budget already spent
   (grep this file's checkpoint log for calls used).
