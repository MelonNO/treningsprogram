---
name: wave2-feature-batch
description: Wave 2 (B2 rationale + B1 weekly coach summary) — design plan, Room version path, seams, dispatched 2026-06-24
metadata:
  type: project
---

Wave 2 of feature-batch-2026-06 (see [[wave1-feature-batch]], [[feature-batch-2026-06]], `docs/intake/feature-batch-2026-06/SEQUENCE.md`). Dispatched as ONE worker doing B2 first then B1 (SEQUENCE mandates serialize on shared seams). User ASLEEP, full autonomous authorization incl. shipping passed features + REAL Claude API calls on device authorized for B1/B2 on-device verify.

**Starting state (verified at dispatch):** Room version **10**. AiRepository post-B3 = prompt-only/additive (STALLED LIFTS block in buildSessionHistory; response side `parseProgram`/`extractJson` untouched). Generation response model = `ProgramJson(days)`; `GenerationResult(exercises, attemptCount, rejectionReasons)`.

**B2 design (rationale, assumption D = persists with plan, visible until next regen):**
- Add top-level `rationale` to generation OUTPUT JSON in `buildPrompt()`; parse into ProgramJson; thread via `GenerationResult.rationale`.
- Persist as a COLUMN on `planned_exercises` (PlannedExercise.rationale), stamped on every row of the week. Room **MIGRATION_10_11** = ALTER TABLE planned_exercises ADD COLUMN rationale TEXT NOT NULL DEFAULT ''. (Chose column-on-plan over new table: per-week not per-exercise, survives savePlan delete+insert, no join.)
- 3 call sites that savePlan must stamp rationale: `MainActivity.checkAndAutoGenerateWeeklyPlan` (~256), `ui/settings/SettingsViewModel` (~339), `ui/setup/SetupWizardViewModel` (~105).
- Single-day regen (`saveDayPlan`/`generateSingleDayProgram`) must PRESERVE week rationale (carry forward existing row's value) — do NOT clobber.
- Surface on Program tab (ProgramFragment/VM, fragment_program.xml), neutral/empty when blank (covers pre-feature plans).

**B1 design (weekly coach summary, automatic/weekly, assumption C = persisted scrollable history):**
- New entity `WeeklySummary`(weekKey=isoWeekKey, createdAtMs, summaryText) + DAO. Room **MIGRATION_11_12** = CREATE TABLE (SEPARATE bump, applied after B2's, one at a time).
- AiRepository gets a SEPARATE `generateWeeklySummary()` call reusing history-context (buildSessionHistory/getStrengthHistory/getWeeklyVolume) — NOT entangled with generateAdaptedProgram response shape.
- Weekly trigger mirrors `checkAndAutoGenerateWeeklyPlan` (guard `prefsManager.lastWeeklySummaryWeek` keyed by isoWeekKey), invoked from MainActivity ~line 134, background/non-blocking; skip if no apiKey / onboarding incomplete / already-this-week / too little data.
- Scrollable history screen (placement = orchestrator's call, assumption A) reached from obvious entry point (Settings row pattern like E3's Exercise Library, or Home card).

**FINAL Room version after Wave 2 MUST be 12** — this is the recorded baseline Wave 3's E2 program-entity bump stacks on (dep #4). Both migrations must be in AppDatabase companion AND the DatabaseModule (di/) migration list, in order.

**Migration-list gotcha:** AppDatabase companion holds MIGRATION_x_y objects but the migration LIST is wired in DatabaseModule (di/) — new migrations must be added in BOTH places or Room throws at runtime.

**Wave 2 gate (all 4, evidence-backed, same standard Wave 1 met):** debug+release build green (md5); `./build.sh test` counts from result XMLs (not assertion) naming new B1/B2 tests; on-device ui-test-worker evidence for BOTH B2 (rationale renders on Program tab, reflects real plan) and B1 (weekly summary renders, real data) with MD5/stale-APK discipline; AiRepository coherent + Room migrations coherent + record Room version (12) for E2.

**Cloud-backup parity (verified by lead at code level 2026-06-24):**
- B2 rationale = CAPTURED automatically. `BackupEnvelope.plannedExercises: List<PlannedExercise>` (whole entity via Gson); ExportRepository exports `plannedExerciseDao.getAllOnce()`, restores via `BackupMerger.mergePlannedExercises` on whole entities. New `rationale` column rides through export+merge with NO change. BackupSerializationTest(6)+BackupMergeTest(20) still green.
- B1 weekly_summaries = NOT in backup set (deliberate, low-risk gap). `BackupEnvelope` (data/backup/BackupModels.kt, `CURRENT_BACKUP_VERSION=2`) enumerates tables EXPLICITLY — no weekly_summaries field. Adding cleanly needs: envelope field + mergeWeeklySummaries (BackupMerger) + export/import wiring (ExportRepository) + version bump 2→3 + BackupMigrations.STEPS V2_TO_V3 + test updates. Deferred per brief "report don't force"; safe to add later (regenerated weekly, Gson ignores unknown keys, additive CREATE TABLE). Lead's call whether to schedule.

**Independent verification done (lead, 2026-06-24):** debug build green md5=6be0206bba1036e150cee5bd624a155f; compileReleaseSources green; `./build.sh test` = 176 tests / 0 fail / 0 err / 0 skip (re-read from result XMLs). New tests: B2RationaleParseTest(5), B1WeeklySummaryTriggerTest(6). Code review: 3 savePlan sites stamp `it.copy(rationale=...)`; saveDayPlan preserves week rationale via getForWeekOnce (reads OTHER day's row pre-delete); B3 stall block intact; scope clean (no unrelated edits). On-device gate dispatched to ui-test-worker.
