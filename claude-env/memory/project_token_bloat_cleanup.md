---
name: project_token_bloat_cleanup
description: Per-request token/disk bloat source on this project (stale agent worktrees + committed binaries) and the standing fix
metadata: 
  node_type: memory
  type: project
  originSessionId: 8dcfc28d-b2d0-46ec-a7f8-e1ce3a5a9385
---

User flagged high token-per-request (orchestrator ~600k, each subagent ~400k) and asked to "clean the project" (2026-06-28).

Root causes found + fixed:
- `.claude/worktrees/` held **7 stale agent worktrees = full duplicate source trees** (+ up to 1.3G build output each). `.claude/` is gitignored so they were invisible to `git` but fully walked by `find`/`ls`/`du`/Glob — every directory walk/glob fanned out ~7×. Removed all worktrees + deleted their 10 checkpoint branches (user chose full discard). `.claude/` 4.6G → 444K.
- Large binaries were **tracked in git** → copied into every worktree + main tree + bloated `.git` (219M): `free-exercise-db-main.zip` (96M, redundant — already extracted under `app/src/main/assets/exercise_db/`), `treningsprogram-v1.3.4/1.3.5.apk`, `Change docs/Archive/sample-set.zip`+GIFs. `git rm`'d (staged, left uncommitted per [[feedback_no_unrequested_commits]]).
- `.gitignore` now excludes `*.apk`, the exercise-db zip, sample-set, and `scratchpad/`.

**Standing guidance:** worktrees from `isolation: worktree` agent spawns + locked crash-recovery checkpoints accumulate and recur — periodically `git worktree remove --force` the stale ones. Removing a worktree DIR keeps its branch ref (recoverable) unless you also `git branch -D`. `app/build` (~1.2G, gitignored/regenerable) left intact to avoid forcing full rebuilds.

**Pipeline scaled + fleet trimmed (2026-06-28, the deeper lever — see [[feedback_orchestrator_owns_changes]] + [[feedback_agent_usage_guideline]]):** orchestrator now DEFAULTS to doing work itself (spawns workers only for parallelism/isolation/independent-verification); pipeline scaled to risk (small fixes = 1 orchestrator pass, no intake/ui-test); `build-release-shipper` REMOVED (orchestrator builds + ships). Edits had to be made in BOTH the coordinator's memory AND the orchestrator's OWN agent-memory (`.claude/agent-memory/project-lead-orchestrator/feedback_ship_handoff.md` still said "never ship, hand to shipper" — caught on a "are you sure" recheck). **HONEST LIMIT:** this cuts agent COUNT for small/peripheral work, not per-agent cost; the recent expensive class (AiRepository / AI-generation fixes) still routes to the FULL pipeline by design, so it stays pricey unless the per-agent levers are also done (split the 1910-line `AiRepository.kt`; scoped offset/limit reads instead of whole-file). Memory is soft guidance, not a hard gate — true enforcement needs settings.json deny-rules / PreToolUse hooks.
