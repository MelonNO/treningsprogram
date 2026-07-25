# Where the work stands — 2026-07-25

Snapshot taken when the session was moved off the Raspberry Pi. Read this before
resuming anything.

## Shipped

`main` is at **c00d9ac — v1.25.1**, released and verified live. Everything below
is unshipped work on top of it.

## Active: the 10-item QoL batch

Branch **`qol-batch-2026-07-25`**, cut from `c00d9ac`. Briefs live in
`docs/intake/qol-batch-2026-07-25/` — `INDEX.md` has the clusters, the hazards
(shared file seams), and every decision the user confirmed. The orchestrator's
own ledger is `.claude/agent-memory/project-lead-orchestrator/project_qol_batch_2026_07_25.md`.

Baseline before the batch: **916 tests, green in both variants.**

### Done and committed on the batch branch

| Items | Commit | What |
|-------|--------|------|
| 01, 07, 10 | `d7138c9` | mid-workout set delete (confirmed + renumbered), warm-up chip clears per set, moved-workout finish celebrates like a normal finish |
| 08 | `cace701` | rest-timer chime no longer mutes music (root cause: double sound path — silent-channel rename `rest_timer_done`→`_v2`, plus a `Ringtone` with explicit attributes and no focus request) |
| 09a/b/c | `8deca25`, `ca8a592`, `d51ee2b` | chart touch-snapping + generic range filter (+10 tests), touch-to-read on body-weight/strength/reps charts, body-weight chart follows the Progress date range |
| — | `192f68e` | this transfer snapshot (docs, flows, .gitignore, large-binary removal) |

### In flight — worker branches with WIP commits

Each was a live agent worktree at `.claude/worktrees/<name>` when the session was
killed. Their dirty trees are now captured as `WIP:` commits so nothing was lost
in the move. **These are mid-thought, not reviewed, and may not compile.**

| Branch | Items | Base | State |
|--------|-------|------|-------|
| `qol/calories` | 03 — calorie estimates | `cace701` | WIP `80e787f`. Has `CalorieEstimator.kt` + edits across log/history/recap. ⚠️ also carries a stray `HistoryStatsFragment.kt.tmp.5698.*` from an edit interrupted by the crash — diff it against `HistoryStatsFragment.kt` and delete it. |
| `qol/gen-reliability` | 05 + 06 — background survival, Monday auto-gen | `cace701` | WIP `043be67`. New `AutoGenPolicy.kt`, `AutoGenSchedule.kt`, `GenerationRunner.kt`; manifest + gradle touched. Diagnosis so far: all 5 generation entry points run on UI-scope coroutines. WorkManager is not a dependency; AlarmManager + foreground-service precedents already exist in the app. |
| `qol/history-browser` | 04 — History week-browser | `cace701` | WIP `e51e4ec`. New `HistoryBrowser.kt`, `HistoryPrFlags.kt`, two item layouts. Largest item in the batch. |
| `qol/charts-touch` | 09 | — | **Merged already** — its three commits are on the batch branch. Branch can be deleted. |

### Not started

**Item 02** — per-gym "exercises to avoid". Deliberately not dispatched: it
shares the `data/repository/AiRepository.kt` seam with items 05+06, so it must be
serialized *after* `qol/gen-reliability` lands. It is also the only item with a
schema change — **DB v19→v20 and backup v6→v7**.

### Resuming

1. Rebase or merge each worker branch onto the current batch tip.
2. On `qol/calories`, resolve the stray `.tmp` file first.
3. Land in the order the index recommends: Cluster B (05+06) → 02 → 03 → 04.
4. Ship needs **direct user authorization** — a relayed "ship when done" was
   refused per `memory/feedback_orchestrator_owns_changes.md`. Deliver
   ship-ready plus a version recommendation and wait.

## Worktrees

The four worktrees under `.claude/worktrees/` do **not** survive the clone —
`.claude/` is gitignored and worktree metadata is machine-local. This is fine:
all the work is in the branches. Recreate them only if you want the same
parallel layout:

```bash
git worktree add .claude/worktrees/qol-genrel  qol/gen-reliability
git worktree add .claude/worktrees/qol-history qol/history-browser
git worktree add .claude/worktrees/qol-calories qol/calories
```

`git worktree add` can exceed a 2-minute timeout under concurrent builds. If one
fails partway, clean up with `git branch -D` + `rm -rf` + `git worktree prune`
before retrying.

## Other branches now on GitHub

Pushed for completeness; none are active work.

- `wave1-integration` — 10 commits ahead of main, historical integration branch.
- `fix-generation-retry-hang-2026-06`, `fix-h4-generation-timeout-2026-06` — 4
  commits each, superseded by shipped releases.
- `patch-ondevice-ux1` — 1 commit.
- ~12 further branches whose work already landed on `main` (0 commits ahead) were
  **not** pushed. Recreate any of them from `main` if you ever need the name.

## Open items for the user (carried over, not blocking)

- On-device checks from the v1.25.x releases.
- The 120-minute generation ceiling — needs a product decision plus live
  iteration, see `memory/project_overnight_autonomous_chain_2026_07.md`.
- Legacy Stats→Progress "Personal Records" widget counts warm-up sets as PRs —
  known, pre-existing, unfixed. `memory/project_pr_widget_warmup_bug.md`.
