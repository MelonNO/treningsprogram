---
name: project-pr-widget-warmup-bug
description: "Known pre-existing bug (found 2026-06-24 during Wave 1 testing, NOT fixed) — legacy Stats→Progress \"Personal Records\" widget counts warm-up sets as PRs."
metadata: 
  node_type: memory
  type: project
  originSessionId: 5f2de650-b54a-45d5-85e1-985b25150185
---

The **legacy Stats → Progress "Personal Records" widget includes warm-up sets** when computing PRs (e.g. a seeded 200 kg *warm-up* shows up as a Bench PR). This contradicts the established warm-ups-excluded convention.

Discovered during Wave 1 on-device verification of the new C1 e1RM/PR timeline (see [[project-feature-batch-2026-06]]). C1's NEW timeline correctly **excludes** warm-ups (verified: ~116 kg e1RM, not ~200). The bug is in the **old** Progress "Personal Records" query, which C1 does not touch.

**Not a Wave 1 regression** — the Wave 1 diff was confirmed not to touch that query. It is a separate, independent, pre-existing defect.

**Status:** NOT fixed. User has not been asked to route it yet (flagged by the orchestrator for the morning report). If the user wants it fixed, it's a standalone fix to the legacy Progress PR query (apply the same warm-up exclusion C1 uses, likely via the shared `domain/Epley.kt`-based path / the warm-up flag on `WorkoutSet`). Akin in spirit to [[project-achievements-orphan-rows]] (another known-unfixed defect).
