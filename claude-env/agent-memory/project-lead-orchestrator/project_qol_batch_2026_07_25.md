---
name: qol-batch-2026-07-25
description: 10-item QoL batch (branch qol-batch-2026-07-25) — ledger, worker layout, recovery notes after session-limit kill
metadata:
  type: project
---

10-item QoL/gen/History batch, briefs at `docs/intake/qol-batch-2026-07-25/` (INDEX.md has clusters + accepted decisions). Branch `qol-batch-2026-07-25` off c00d9ac (=v1.25.1=main). Baseline: 916 tests x2 variants green.

**Why:** user-confirmed batch, intake done 2026-07-25; orchestrator owns plan/review; ship BLOCKED on direct user auth (relayed "ship when done" refused per [[feedback_relayed_consent]] — deliver ship-ready + version rec instead; coordinator ships per precedent).

**How to apply:** if resumed after another kill — check `git log qol-batch-2026-07-25`, worktree dirty state, and this ledger before re-dispatching anything.

Ledger (2026-07-25 ~13:30):
- 01/07/10 (Cluster A, log screen) + 08 (rest timer): DONE by orchestrator, commits d7138c9 + cace701, tests SetRenumberingTest(8)+AutoAttributeMoveTest(10) green. 08 root cause: double sound path (silent-channel rename rest_timer_done→_v2 + Ringtone w/ explicit attrs, no focus).
- 05+06 worker (worktree qol-genrel, branch qol/gen-reliability @cace701): resumed via SendMessage after kill; diagnosis so far: all 5 gen entry points are UI-scope coroutines. WorkManager NOT a dep; AlarmManager+FGS precedents exist.
- 04 worker (qol-history): resumed; had untracked HistoryBrowser/HistoryPrFlags + tests.
- 09 worker (qol-charts): resumed; had substantial dirty chart work (ChartTouch/ChartScrub etc.).
- 03 worker (qol-calories, based on batch tip WITH Unit A): launched fresh 2nd try (1st Agent call died on classifier outage). Mandate: derive-on-fly, NO persistence.
- 02: NOT dispatched — must serialize after 05+06 lands (AiRepository seam). Only item with DB bump (v19→v20) + backup v6→v7.

Recovery lessons that worked:
- SendMessage to a dead agent's raw ID resumes it from transcript with full context — prefer over relaunch after harness kills.
- Stale worker branches with no commits but dirty trees: `git -C <wt> checkout -B <branch> <newbase>` carries dirty work onto the new base (verify no file overlap with new-base commits first).
- `git worktree add` can exceed 2-min default timeout under concurrent builds — use long timeout; clean partial state with `git branch -D` + `rm -rf` + `git worktree prune` before retry.
