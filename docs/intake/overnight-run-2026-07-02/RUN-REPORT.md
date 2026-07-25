# RUN REPORT — Staged run 2026-07-02 → 2026-07-03

Four releases shipped and API-verified live in one run: **v1.22.0 → v1.23.0 → v1.24.0 → v1.24.1**.
Test tally: 685 baseline → **840 green** (both variants) at close. Live Anthropic API spend:
**1 of 15** (release budget) + **2 of 20** (test budget) = 3 calls total for the whole run.
DB schema unchanged (v18) across all four releases. Backup schema v4 → v5 (v1.22.0).

## Stage 1 — v1.22.0 (rest-ux batch) — commit 19d69df
Briefs `docs/intake/rest-ux-batch-2026-07/` (01/02/04/05): Home sphere removed, session rest
memory (sticky per-exercise rest adjustments), manual rest mode incl. generation math (proven
live, 1 API call, pass attempt 1), persistent exercise timer (wall-clock). 712 tests.

## Stage 2 — v1.23.0 (feature-research R1–R7) — commits …e6b4a46, release bd35ad9
Briefs `docs/intake/feature-research-2026-07/`. R1 schedule-aware streak · R2 notification
center (4 toggles) · R3 body-weight insights (AI sees weight trend in both gen paths) ·
R4 challenges 2.0 + Perfect Week · R5 achievement gallery (tiers/rarity/next-up) ·
R6 completion celebration · R7 "beat last time" (Beat chip + gold PR flash). 803 tests (+91).
**Recovery note:** a session crash killed the first orchestrator mid-R7; its uncommitted
partial work contained a compile error (duplicate method) and an unwired `checkPrPreview` —
audited, repaired, completed by orchestrator #2b. R1–R6 spot-checked clean.

## Stage 3 — v1.24.0 (16-item UX batch) — release fba7c08
Briefs `docs/intake/stage3-ux-2026-07/` (all 16 briefed, none skipped; assumptions
A-01a…A-16a — none vetoed by user). Highlights: BW reps chart (exposed a real pre-existing
bug: 0-kg sets were invisible on strength charts), calendar date-range filters (History +
Progress), Recap overhaul (overview removed, finer muscle labels, earned-this-session
achievements/PRs by timestamp attribution, Auros restyle), heatmap tap → Recap, Profile
7-day PRs + stats block removed, skeleton loaders, library 2-frame animation, keypad
tap-outside dismiss, Settings reorder, Debug → About, CSV export removed. 830 tests (+27).
Bonus: missing v1.23.0 in-app changelog entry backfilled.

## Stage 4 — full on-device test sweep (Waydroid + Maestro, explicitly authorized)
Log: `STAGE4-FINDINGS.md` (this directory). Released v1.24.0 APK, md5-verified, clean install.
**PASS:** onboarding + live generation (2 calls), guided log → celebration → recap chain,
fine muscle labels, heatmap tap accuracy, both range pickers, BW reps chart, Settings 6/7/8,
R2 toggles, manual rest mode, backup export→import round-trip (merge idempotent), achievements
17/200 (old orphan bug does not reproduce).
**Findings:** F3 HIGH — History list permanently stuck on skeletons (no workaround; fixed in
v1.24.1) · F2 cosmetic — "0 kg × 6" (fixed in v1.24.1) · F1 — keypad dismiss "failure" was a
TEST ARTIFACT (tester used the reps field's system IME, which brief-16 scopes out; the custom
weight pad's code is correct; device-unverified) · item-4 observation — resolved by USER
DECISION 2026-07-03: first-ever lifts are baselines, NOT PRs; strict behavior ratified.
**Harness limits (documented in memory):** Waydroid cannot advance the clock and release APKs
forbid run-as/DB seeding → R7 second-session flash, timer persistence across kill, and session
rest memory are untestable in the emulator.
**Interruption:** worker #1 killed by session limit (19:38→22:10); worker #2 resumed off the
durable findings log; device left clean.

## Patch — v1.24.1 — commit/tag 3b05561, release 348318068
**F3 root cause:** the History pipeline was the app's only two-layer shared flow chain
(`stateIn` → combine → `stateIn`); on-device the doubled chain never delivered its first DB
emission and the null "loading" marker made the state unrecoverable. Fix: collapsed to a
single sharing layer, filter logic extracted to pure `domain/HistorySearch`; shipped dex
verified. Also: empty-state copy distinguishes search-miss, search hint no longer promises
exercise-name search (it was always date-only). **F2:** Recap shows "BW × reps" at 0 kg.
**F1:** no change (code correct). 840 tests (+10: HistorySearchTest ×7, chain contract ×3).

## Open items handed to the user (2026-07-03)
1. **On-device checklist (needs a phone / v1.24.1):** History list renders immediately (F3
   final proof) · weight-pad tap-outside dismiss (F1, never actually device-tested) · R7 Beat
   chip + gold PR flash + no-re-flash-on-resume on a second session · exercise timer survives
   backgrounding/kill · session rest memory sticks · real notifications fire per toggles.
2. **Observe over time:** R3 generation quality with weigh-in data (Prompt Log shows the sent
   prompt) · whether a body-weight chart should exist on the Progress sub-tab (trend currently
   lives on Home; needs ≥2 weigh-ins to judge; flag if wanted → new item).

## Process incidents (for the record)
- Two session-limit interruptions (orchestrator #2 overnight; stage-4 worker #1) + one Pi/session
  crash — all recovered via STATE.md checkpoints + ground-truth verification (git/API).
- Agents twice claimed memory updates that never happened (orchestrator #2b, worker #2) —
  coordinator wrote memory from verified facts instead. Both ship reports otherwise verified
  accurate claim-by-claim.
- Orchestrators #2b/#3 stopped mid-ship waiting on build notifications that never come for
  untracked children — coordinator watched the build processes and nudged with ground truth.
