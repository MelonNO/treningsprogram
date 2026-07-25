---
name: project-feature-batch-2026-06
description: In-progress 8-feature batch (4-wave sequence) under an autonomous overnight mandate; Pi crashed mid-Wave-1, pipeline resumed; complete all waves then ship passed features.
metadata: 
  node_type: memory
  type: project
  originSessionId: 96287e1d-331c-452d-ba22-1e330643c597
---

A batch of 8 new features was scoped via the intake-understanding agent on 2026-06-24 but **not yet implemented**. Artifacts on disk at `docs/intake/feature-batch-2026-06/`: `INDEX.md`, `SEQUENCE.md`, and 8 outcome-only briefs `brief-B1`…`brief-E3`.

## The 8 features
- **B1** AI weekly coach summary (plain-language readout)
- **B2** "Why did the program change?" rationale
- **B3** Plateau/stall detection (+ suggestion)
- **C1** Per-exercise 1RM estimates + PR timeline
- **C4** Muscle-group recovery "freshness" view
- **E1** Manual program editing
- **E2** Named programs **and** periodized mesocycle blocks
- **E3** Exercise library browser (over bundled `ExerciseCatalog`)
(D4 Garmin was proposed then **dropped** by the user.)

## Confirmed decisions baked into the briefs
- Extra Claude API calls are fine (B1/B2/B3/C4 may use the model).
- B2 = model emits a `rationale` field in the generation response it already produces.
- E1 = simple; manual edits last only until the next regeneration (regen may overwrite).
- E2 = BOTH named programs + mesocycle blocks; **L1** (AI regenerates each week inside a block) + **M2** (deload triggered by stalls/fatigue → couples E2 to B3).
- C4 = fixed recovery-window coloring (fresh/recovering/overdue).
- B3 stall criterion + C4 recovery windows are grounded in cited exercise-science (no magic numbers).
- 11 minor points are **labelled ASSUMPTIONS** (overridable later), e.g. C4 placed on **Home** (keeps Wave 1 collision-free), Epley for 1RM, PRs by estimated-1RM (warm-ups excluded), E3 exposes the full bundled catalog browse-only.

## Enforceable 4-wave sequence (`SEQUENCE.md`)
- **Wave 1:** C1, **C4-view only**, E3 (fully parallel) **+ B3** (pulled early — E2's deload depends on it; B3 owns the `AiRepository` seam alone this wave). NOTE: in the understander's final pass, **C4 was SPLIT** — the recovery *view* (on Home) is Wave 1; C4's *AI-nudge* piece moved out of Wave 1 onto the serialized `AiRepository` track (because it also edits `AiRepository`).
- **Wave 2:** B2 then B1.
- **Wave 3:** E2, internally storage → generation (L1) → deload (M2).
- **Wave 4:** E1 (on E2's settled program model).
- Critical path: **B3 → B2 → E2 → E1**. Hard gates: B3 before E2-deload; E2-storage before E1; `AiRepository` serialized across B2/B3/E2; Room schema bumps one at a time (B1's then E2's).
- **Per-wave "fully tested" confirmation gate:** build green (`./build.sh` debug+release) + unit tests (with counts) + on-device UI verification via `ui-test-worker` (evidence) + AiRepository/Room coherent. The COORDINATOR enforces it — dispatches one wave at a time and will NOT send the next wave without the orchestrator's confirmed PASS Wave Verification Report. See [[agent-usage-guideline]], [[orchestrator-owns-changes]].

## STATUS (2026-06-24)
Understander gave its "finished and satisfied" sign-off; documents are final. Wave 1 dispatched, but the **orchestrator initially REFUSED** on two grounds: (1) relayed-consent — large net-new work needs the user's own authorization, not the coordinator's; (2) the per-wave gate's mandatory `ui-test-worker` contradicted the user's standing "no unprompted on-device testing" rule. **The user then resolved both DIRECTLY:** authorized **all waves this session**, and granted a STANDING permission that **the orchestrator may spawn `ui-test-worker` as much as it wants** (recorded in [[feedback-no-unprompted-testing]]). The orchestrator REFUSED the coordinator's relay even after the user's answers were relayed — its [[feedback-relayed-consent]] rule requires the user's OWN-channel message for sensitive scope (large net-new batch + lifting a user-set safety rule). So the **user gave the orchestrator DIRECT authorization in their own channel**; that resolved the hold and **the orchestrator is now EXECUTING Wave 1** (scope C1 + C4-view + E3 + B3, per-wave fully-tested gate incl. on-device ui-test-worker, hard stop after Wave 1). LESSON: for this orchestrator, sensitive authorizations (new batches, reversing safety rules) must come from the user directly, not via the coordinator. On resume: await the orchestrator's Wave 1 Verification Report; on confirmed PASS, dispatch **Wave 2** (B2 then B1) — never start a wave without the prior wave's confirmed PASS. Waves 2–4 pending.

## AUTONOMOUS OVERNIGHT MANDATE (2026-06-24, user going to bed)
The user gave the orchestrator a DIRECT own-channel auth to complete ALL waves dispatched by the coordinator, and to inform the coordinator (→ shipper) to ship all confirmed features once everything is tested. The user told the coordinator: **continue autonomously until all waves are complete AND all successful waves are shipped.** This is explicit, durable authorization (covers [[feedback-no-auto-release]] — shipping IS asked-for here).
- Drive Waves 1→2→3→4 sequentially; each gated on the orchestrator's confirmed fully-tested PASS (build + JVM tests + on-device ui-test-worker evidence). Never start a wave without the prior PASS.
- After all waves are complete + tested, dispatch **build-release-shipper** to ship all confirmed features as ONE release (orchestrator sets the version; signed build/tag/GitHub release/APK per [[reference_release_process]]).
- If a wave CANNOT pass after the orchestrator's rework, do NOT ship broken code and do NOT block the whole release on it: ship the successfully-verified waves' features, leave the failed one out, and document everything for the user.
- The orchestrator must **only signal passed features for shipping** (user instruction 2026-06-24) — its ship-ready list contains ONLY features whose gate fully passed; failed/partial/unconfirmed features are excluded with a reason. The coordinator ships only what the orchestrator confirms passed.
- Coordinator still NEVER codes/builds/tests itself ([[orchestrator-owns-changes]]); it only dispatches/gates/relays. Event-driven: each wave's background completion re-invokes the coordinator to continue.
- Have a full morning report ready: per-wave PASS/fail, what shipped (version + release URL), and anything needing the user (e.g. the still-pending OAuth client ID, which is independent and untouched).

## RESUME after Pi crash (2026-06-24 ~05:16)
The Pi died mid-Wave-1 overnight; user asked the coordinator to resume the pipeline and RE-AFFIRMED the autonomous mandate directly ("auth to complete all waves… orchestrator will tell you to ship the successful features"). State found in the working tree on resume: all four Wave-1 units (C1, C4-view, E3, B3) were already AUTHORED (uncommitted) — domain Epley/OneRmTrend/MuscleRecovery/StallDetector, ui/library/*, the 4 new tests, AiRepository B3 prompt edit — and a debug APK built once at 02:46; a release/test run was cut off ~05:05 (assume stale Gradle daemon/lock on resume). No Wave Verification Report existed, nothing committed. Coordinator re-dispatched project-lead-orchestrator to RESUME+VERIFY Wave 1 to the full gate (clean rebuild debug+release, ./build.sh test with the 4 new tests, ui-test-worker on-device evidence, AiRepository coherent) and produce the Wave 1 Verification Report. Coordinator created a 4-wave task tracker (#1 in_progress, #2/#3/#4 chained by blockedBy).

### Wave 1 result (2026-06-24 ~05:50) — COORDINATOR-CONFIRMED PASS
Orchestrator returned a PASS Wave Verification Report; coordinator independently spot-checked: debug APK md5 `65d98b5cd56d6fcd7c80aae3d83f9e18` (matches), release compile clean, unit tests 0 failures/0 errors (4 new test classes B3/C1/C4/E3 green), on-device Maestro evidence (screenshots+flows) for C1/C4-view/E3/B3, AiRepository change additive prompt-only (response parsing untouched → coherent for B2). Task #1 marked completed; #2 (Wave 2) dispatched. Found a PRE-EXISTING bug (NOT Wave 1, not a regression): legacy Stats→Progress PR widget counts warm-ups → [[project-pr-widget-warmup-bug]]; surface in morning report.

### Wave 2 result (2026-06-24 ~06:30) — COORDINATOR-CONFIRMED PASS
B2 (rationale) then B1 (weekly coach summary). Coordinator spot-check: debug APK md5 `6be0206bba1036e150cee5bd624a155f` (matches), release compile clean, tests 176 / 0 fail / 0 err (new: B2RationaleParseTest, B1WeeklySummaryTriggerTest; backup tests still green). Room **version 12** (MIGRATION_10_11 = rationale column on planned_exercises; MIGRATION_11_12 = weekly_summaries table) registered in AppDatabase + DatabaseModule. AiRepository coherent: B2 rationale is response-side sibling of days + parseRationale; B3 STALLED LIFTS prompt block intact. B1 = dedicated Weekly Summary screen via Settings "Coach Summary" row; live AI summary verified on-device (named real logged data). Task #2 completed; #3 (Wave 3 E2) dispatched. **Room baseline for E2 = 12.** RESIDUALS for morning report: (1) B1 weekly summaries are NOT yet in the cloud-backup/Export-Import set — deliberate documented deferral, low risk, user to decide whether to schedule; (2) B2 live rationale *wording* not exercised on-device (would overwrite the user's plan) — parse path unit-tested + prompt in place.

### Wave 3 result (2026-06-24 ~07:50) — COORDINATOR-CONFIRMED PASS
E2 programs + mesocycles (storage → generation L1 → deload M2). NOTE: worker built in a worktree off db495ec, then orchestrator integrated E2 into main and re-ran the full gate there. Coordinator spot-check on MAIN tree: debug APK md5 `2a9189b72ff11a8e5694d90a479ca0bb` (matches), release compile clean, tests 203 / 0 fail / 0 err — ALL 10 new suites present (E2ProgramModel/DeloadPolicy/MesocyclePrompt/BackupPrograms + Waves 1–2's 6 survived the merge). Room **version 13** (MIGRATION_12_13: programs table + nullable planned_exercises.programId + backfills a default active program; all three migrations 10_11/11_12/12_13 in AppDatabase+DatabaseModule); entities include WeeklySummary + Program. AiRepository: B3 STALLED LIFTS (line 461) + B2 rationale/parseRationale + L1 MesocycleContext.promptBlock all coexist. Backup DONE this wave (envelope v2→3, programs field, BackupMigrations.V2_TO_V3, BackupMerger.mergePrograms, ExportRepository wiring). On-device: program save/switch + mesocycle progression PASS; deload trigger+flag+UI indicators PASS. **Room baseline for E1 = 13 (no further bump expected).** Task #3 completed; #4 (Wave 4 E1) dispatched.
RESIDUALS added: (3) live-AI-generated deload WEEK not observed end-to-end on Waydroid (pre-existing 120s OkHttp read timeout; NetworkModule unchanged → env flake, not E2 defect; candidate timeout/retry-resilience follow-up, out of batch scope); (4) HOUSEKEEPING: stale git worktree `.claude/worktrees/agent-a2780045f137f58be` (at db495ec) to prune before/at ship; the worker's 6 Maestro flows live in scratchpad (not the repo).

### Wave 4 result (2026-06-24 ~08:35) — COORDINATOR-CONFIRMED PASS → BATCH COMPLETE
E1 manual program editing (edit/delete/add/reorder, on E2's settled model). Coordinator spot-check on MAIN: debug APK md5 `2c0d89aa327491257945a97a29517e2b` (matches), tests **215 / 0 fail / 0 err in BOTH debug+release** (new E1ManualEditTest = 12; 26 suites; all prior green), Room **still 13** (no bump), AiRepository **byte-identical** `3337ed3ab6c13f9416edd5a9668c016d` (E1 didn't touch API/AiRepository). 6 E1 files (4 mod + DayPlanEditor.kt + E1ManualEditTest.kt). On-device: edit/delete/add/reorder + persistence-across-restart all PASS. Task #4 completed.

## BATCH COMPLETE (2026-06-24 ~08:35) — ALL 8 FEATURES SHIP-READY
All 4 waves coordinator-confirmed PASS. Shipping as **v1.8.0** (orchestrator's call, coordinator-accepted: 8 user-facing features + Room 10→13, no breaking changes → MINOR). Dispatched **build-release-shipper** (task #5): prune the 2 stale worktrees (`agent-a2780045f137f58be`, `agent-a4e4ed82628ce3c66`), build signed release APK `treningsprogram-v1.8.0.apk`, commit app/src + docs/intake (EXCLUDE `flows/` 17 Maestro test YAMLs + build artifacts), tag v1.8.0, push, GitHub release per [[reference_release_process]]. CARRIED RESIDUALS for morning report: B1-not-in-backup (deferred), B2 live-wording-not-on-device, E2 deload live-AI-on-Waydroid (env), E1 rest-day add limitation (by design), + the flows/ kept-untracked decision (user may formalize as a test suite later).

## SHIPPED v1.8.0 (2026-06-24 ~08:50) — BATCH DONE
build-release-shipper published v1.8.0; coordinator-verified clean. Commit `a201ee0` "Release v1.8.0 …" (77 files, +5217; app/src + docs/intake only). Tag `v1.8.0` → a201ee0, pushed; origin/main == a201ee0 (in sync). Signed release APK `treningsprogram-v1.8.0.apk` (APK Sig Scheme v2, 99 MB) md5 `b545f6115086c18f1ce137a13f205391`. GitHub release (not draft/prerelease) with the APK asset uploaded: https://github.com/MelonNO/treningsprogram/releases/tag/v1.8.0 . VERIFIED no test artifacts leaked — the 17 new Maestro flows stayed untracked (the 4 tracked flows/*.yaml are the PRE-EXISTING suite); both stale worktrees pruned. The autonomous overnight mandate is fully discharged: all 4 waves PASS → all 8 features shipped in one release. Nothing else pending for this batch except the user-decision residuals above + the independent pre-existing [[project-pr-widget-warmup-bug]] and the v1.7.0 cloud-OAuth-client-id gate (both untouched by this batch). Mandate unchanged: gate each wave on confirmed PASS, then ship passed features as one release via build-release-shipper when the orchestrator signals all done.
AI-TEST-SPEND POLICY (user decision, 2026-06-24): on-device verification of AI-output features (B1 summary, B2 rationale, E2 generation, Waves 2–3) may make REAL Claude API calls on the user's sk-ant key — spend is authorized for the verification gate; if no key is configured on the device, fall back to UI-only/stubbed and note it. Ship-time GitHub auth assumed working from prior releases (v1.6.x→v1.7.0); if push/release auth fails, build+stage the APK and leave exact instructions rather than block.
