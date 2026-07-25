---
name: project-feature-research-batch-v1-23-0
description: "Feature-research/gamification batch R1–R7 shipped as v1.23.0 (2026-07-02), release 2 of the staged overnight run"
metadata: 
  node_type: memory
  type: project
  originSessionId: 79df2a8e-3079-402a-be62-9e4844ca67b9
---

**SHIPPED v1.23.0 (2026-07-02, release commit bd35ad9 on main, R7 commit e6b4a46, tag+release+asset coordinator-API-verified live, DB unchanged v18, versionCode 62).** Release 2 of the staged run in [[project_overnight_run_2026_07_02]]. Briefs: `docs/intake/feature-research-2026-07/` (BACKLOG.md docs-only, NOT implemented).

R1 schedule-aware streak (StreakPolicy shared by live award + StatsRecomputer; `applyStreakFreshness()` on foreground) · R2 notification center (4 per-type toggles, eager alarm cancel, gated GenerationNotifier) · R3 body-weight insights (Home trend, Progress chart, weight line in BOTH gen-prompt paths, "" when no data) · R4 challenges 2.0 (26 templates/9 adaptive, Perfect Week once-per-week + recompute parity) · R5 achievement gallery (tiers/categories/progress, meta-coverage locked by test) · R6 completion celebration (XP count-up = XP-log itemization, prDetails old→new) · R7 "beat last time" (BeatTarget chip guided-mode, gold PR flash in guided+freestyle, resumed-session dedup).

Tests: **803 green both variants** (712 → +91). Live API calls: 0. Crash recovery mid-batch: session died mid-R7; orchestrator #2b audit found the partial R7 had a duplicate-method compile error + unwired `checkPrPreview` + no UI/tests — reverted the dupe, completed the rest.

**Why:** run context + judgment calls live in the orchestrator report and STATE.md checkpoint [17:46].
**How to apply:** R7 target = historical-best weight only (rep-nudge variant deliberately left out); R3 plan-quality effect is live-gen-only, user observes on next generation; on-device feel checks (R2 real notifications, R6/R7 on hardware) deferred to stage 4 / user.
