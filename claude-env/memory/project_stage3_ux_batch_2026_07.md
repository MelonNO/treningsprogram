---
name: project-stage3-ux-batch-2026-07
description: "16-item stage-3 UX batch shipped as v1.24.0 (2026-07-02), release 3 of the staged run"
metadata: 
  node_type: memory
  type: project
  originSessionId: 79df2a8e-3079-402a-be62-9e4844ca67b9
---

**SHIPPED v1.24.0 (2026-07-02, release commit fba7c08 on main, tag+release id 348169484+asset coordinator-API-verified live, versionCode 63, DB unchanged v18).** Release 3 of [[project_overnight_run_2026_07_02]]. Briefs: `docs/intake/stage3-ux-2026-07/` (INDEX + 16 briefs, assumptions A-01a…A-16a — none vetoed by user). 16/16 items CONFIRMED by orchestrator #3 via diff review (40 files, +1710/−613, all under app/); **830 tests green both variants** (803 → +27). 0 live API calls (run budget still 1/15).

Highlights: BW reps chart (new `getSessionRepsHistory` DAO — old strength query silently excluded 0-kg sets), calendar date-range filters on History+Progress (`DateRangeFilter`, default All, non-persistent), Recap overhaul (overview removed → finer muscles via recovery-panel taxonomy → "earned this session" achievements/PRs via timestamp attribution → Auros visual pass), heatmap cell tap → Recap drill (`HeatmapDrill`), Profile PRs rolling-7-days (`RecentPrs`) + stats block removed, skeleton shimmer loaders (150 ms anti-flicker), two-frame library animation, keypad tap-outside dismiss (`TouchObservingLinearLayout`, pass-through, ±2.5 buttons exempt), App Settings on top, Debug moved under About, CSV export removed (JSON backup intact).

**Why:** key judgment calls — achievement→session linkage is timestamp-based (session row ids don't survive backup restore; `unlockedAtMs` does), omit-when-unsure so never wrong-positive; editing a session's date orphans its attribution (known limit). Orchestrator also backfilled the missing v1.23.0 in-app changelog entry (release-2 gap).
**PATCHED by v1.24.1 (2026-07-03, commit/tag 3b05561, verified live, 840 tests):** stage-4 sweep found item 2's skeleton pipeline REGRESSED History — the app's only two-layer `stateIn`→combine→`stateIn` chain never delivered its first DB emission on-device → list stuck on skeletons, unrecoverable. Fixed by collapsing to a single sharing layer + pure `domain/HistorySearch` + 10 regression tests. Also: Recap shows "BW × reps" at 0 kg; search hint corrected (date-only). Item 16's keypad code is CORRECT — the emulator "failure" tested the reps system-IME, which the brief scopes out. Lesson: avoid double-layer shared flow chains.
**User-ratified (2026-07-03):** first-ever lifts are baselines, NOT PRs — "PRs · Last 7 Days" stays empty until a genuine improvement; do not propose showing baselines there again.
**How to apply:** on-device checks deferred to stage 4/user (keypad feel, heatmap tap accuracy, range-picker UX, recap restyle on long sessions, skeleton timing). "Bodyweight exercise" = has ≥1 completed all-0-kg session.
