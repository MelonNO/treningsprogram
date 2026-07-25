# Brief R4 — Weekly challenges 2.0 + the Perfect Week bonus

**Type:** Feature (gamification mechanics) — under the user's "complete creative freedom" grant (Q4)
**Cluster:** G-mechanics (with R1) — same worker, after R1.

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
Weekly challenges (`DailyChallengeManager`) pick 3 per ISO week from a pool of only **12 static templates** — after a few weeks the same "Log a chest exercise (+50)" rotations feel stale, and none of them reference the user's actual training (no "beat last week", no plan awareness). Completion is checked per-session at workout completion; bonus XP feeds the same pipeline as base XP. Separately, the app knows the week's plan and its progress (Program tab week bar: logged/total days) but completing **every planned day of the week earns nothing special** — the single strongest adherence moment in the app is unrewarded. XP events are itemized in the XP log; a backup **merge** recomputes stats from sessions+sets (`StatsRecomputer`).

## What the user wants (end result)
Challenges that stay fresh and reflect the user's own training, plus a headline weekly reward:

1. **Bigger, smarter challenge pool** (~25–30 templates), still 3/week, deterministic per-week rotation, now including **personalized/adaptive** challenges computed from the user's data, e.g.: beat last week's total working sets; set a PR on a named lift from *your* recent plan; complete all planned sessions before the weekend; log a session ≥ your average volume; train a muscle that's fully recovered today. Static classics remain in the mix. A challenge that's impossible for this user this week (no plan, no history) is never drawn.
2. **Perfect Week bonus:** when the user completes **every planned training day of the ISO week**, a bonus XP award (distinct, celebratory, itemized in the XP log as "Perfect Week") lands automatically with the final workout's rewards, and the moment is visibly celebrated (fits the R6 celebration surface if present, otherwise the existing result flow).
3. Challenge presentation keeps its Home card and in-workout progress line, now also showing **progress toward the adaptive targets** (e.g. "31/45 sets") rather than only done/not-done where a target is countable.

## Acceptance criteria
- Done when the weekly draw comes from the enlarged pool, is stable within a week, differs across weeks, and never draws an unsatisfiable challenge for the current user state.
- Done when at least ~a third of the pool is data-driven (references the user's plan, history, last week, PRs, or recovery), with targets computed at draw time and stated concretely in the challenge text.
- Done when completing all planned days of a week grants the Perfect Week XP exactly once per week, itemized in the XP log, and a week with a missed planned day grants nothing.
- Done when countable challenges show live numeric progress on Home and in the workout screen's existing challenge line.
- Done when challenge XP, Perfect Week XP, levels, and achievements stay consistent — and the **backup-merge stats recompute still reconciles** (whatever new XP sources exist must be reproducible from backed-up data, or the recompute must account for them; a merged backup must not silently drop or double-count them).
- Existing challenge keys/state migrate gracefully mid-week (no crash, no double-award; a one-time re-roll on upgrade is acceptable).
- Draw/completion/Perfect-Week logic unit-tested off-device.

## Scope and constraints
- **In scope:** pool + draw + completion logic, Perfect Week award, progress presentation on existing surfaces.
- **Out of scope:** new tabs/screens; multi-week quest chains and seasonal events (backlog); changing base/set/PR XP amounts; challenge count per week (stays 3).

## Decisions baked in
- Creative freedom granted (Q4); challenges and adherence rewards are squarely inside it.

## Assumptions (user may override)
- **A-C1:** Perfect Week bonus = **+150 XP** (between the biggest challenge (150) and a plain workout's typical total).
- **A-C2:** weeks with zero planned days (no program) simply have no Perfect Week available — never auto-granted.
- **A-C3:** adaptive targets are set to be *challenging but reachable* (e.g. last week's sets + 0–10%), not punishing.

## Considerations for whoever builds it
- R1 (streak) edits the same repository/recompute seam — one worker, R1 first.
- The 12 existing challenge IDs are referenced in the live in-workout progress preview (`LogWorkoutViewModel.challengeProgress`) — keep that surface in sync with the new pool.
