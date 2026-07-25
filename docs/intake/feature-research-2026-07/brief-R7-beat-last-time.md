# Brief R7 — "Beat last time": explicit progression target + live PR flash while logging

**Type:** Feature (training optimality + motivation)
**Cluster:** Standalone worker — MUST build after tonight's batch-1 (rest-ux) lands; heavy file overlap on the logging screen.

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
The logging screen already shows a muted "Last time · 3 sets · 8 × 60 kg" reference line (`LastSessionFormat`), and PRs (heaviest weight beaten, `GamificationRepository.isWeightPr` over `getPreviousMaxWeight`) are detected — but only silently at workout completion. So the two motivating facts of progressive overload — *"here is the number to beat"* and *"you just beat it"* — are either implicit or delayed to the end dialog. The AI's coaching notes even prescribe double progression ("+reps to top of range, then +load"), yet the UI never states today's concrete target.

## What the user wants (end result)
1. **A "Beat" target chip** on each exercise while logging: the concrete number that would be progress — the exercise's historical best working weight (the same baseline PR detection uses), phrased as a target, e.g. "Beat: 60 kg". Where the last session hit the top of the AI's rep range at that weight, the target may instead nudge reps/load per the prescription — but at minimum the historical-best weight is always shown. No history → no chip (a first session sets baselines, never targets).
2. **Instant PR feedback:** the moment a logged set's weight exceeds the historical best, a small immediate celebration on the spot (chip flares into "PR! 62.5 kg", brief vivid flash in the Auros language) — no waiting for the completion dialog. The completion flow's official PR award stays exactly as-is; this is preview feedback, never a second award.
3. Consistency: what flashes as a PR mid-workout is exactly what the completion result later credits (same rule: strictly greater than a real prior best, warm-ups excluded, first-ever performance never a PR).

## Acceptance criteria
- Done when an exercise with history shows its Beat chip with the correct historical-best working weight, and a fresh exercise shows none.
- Done when logging a working set above the historical best triggers the inline PR moment once (not again for further sets unless they beat the new number), and warm-up sets never trigger it.
- Done when the mid-workout PR indication and the completion dialog's PR list agree for the same session, including the multi-exercise case.
- Done when swapping the exercise mid-session updates the chip to the swapped exercise's own history.
- Done when the chip coexists cleanly with the existing last-time line, spec chips, and (post-batch-1) rest UI — no clutter regression on small screens.
- PR-preview logic unit-tested off-device against the completion detection for agreement.

## Scope and constraints
- **In scope:** the target chip, the inline PR moment, agreement with existing detection.
- **Out of scope:** changing PR definition or XP; rep-PR / e1RM-PR types (backlog); auto-filling suggested weights (already handled by existing prefill logic); the completion dialog (R6's).

## Assumptions (user may override)
- **A-P1:** target = historical best **weight** (matching the app's PR definition), not e1RM or volume.
- **A-P2:** the inline moment is small and local (chip flare / brief flash), reserving the big celebration for completion (R6).

## Considerations for whoever builds it
- The logging screen is the app's busiest surface and batch-1 edits it tonight — build strictly after batch-1, rebase deliberately.
- Historical-best lookup at exercise-display time must not jank the UI (the screen already does async last-sets fetches — same pattern applies).
