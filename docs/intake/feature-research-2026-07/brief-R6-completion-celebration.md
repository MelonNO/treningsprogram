# Brief R6 — Make finishing a workout feel like a win (celebration upgrade)

**Type:** Feature (gamification presentation) — under the user's "complete creative freedom" grant (Q4)
**Cluster:** G-presentation (with R5) — same worker, after R5.

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
The payoff moment is currently a plain `MaterialAlertDialogBuilder` dialog (`LogWorkoutFragment.showResultDialog`): title "Workout Complete!", a streak line, a summary line, plain-text lists of PRs / achievements / challenges, buttons "Awesome!" and "View full analysis". The *follow-through* is already nicely animated (Program-tab day-chip bounce + week-bar fill, then Home XP-bar fill with a level-up overlay and challenge snackbars) — it's the dialog itself, the first thing the user sees after their hardest tap of the day, that is the flattest surface in an otherwise vivid gamification layer. `WorkoutResult` already carries everything needed: itemized XP, PR names, new achievements, challenges, streak, level progress.

## What the user wants (end result)
The workout-complete moment becomes a celebration worthy of the Auros design:

1. A **celebratory result surface** (styled dialog or full-screen moment — builder's choice) replacing the stock dialog: hero "Workout complete", an **XP count-up** to the earned total with the itemized breakdown (base / sets / PRs / challenges — same numbers as the XP log), session summary, and the streak state with its flame.
2. **PRs shown with their numbers**, not just names — "Bench Press — 62.5 kg, up from 60 kg" — each as a proud highlight card. (The old→new weights are knowable at detection time; surface them.)
3. **Achievement unlocks as tier-styled cards** using R5's rarity styling — a Legendary unlock must feel visibly bigger than a Common one.
4. **Challenges completed** (and a Perfect Week, when R4 grants one) get their own celebratory rows.
5. A tasteful **celebration effect** in the Auros language (confetti/aurora burst — vivid but brief), scaled up slightly for level-ups, PRs, and Perfect Weeks.
6. The two existing exits stay: continue (→ the existing Program/Home animation chain, untouched) and "View full analysis" (→ Recap).

## Acceptance criteria
- Done when completing an ordinary workout shows the new surface with correct counted-up XP matching the XP log's itemization, and both exits behave exactly as today (Program chip/bar animation and Home XP animation still play; level-up overlay still works).
- Done when a PR workout shows each PR with new-vs-previous weight.
- Done when achievement unlocks render with their R5 tier styling.
- Done when a no-frills session (no PRs/achievements/challenges) still feels pleasant and compact — the surface scales down gracefully.
- Done when the celebration effect plays without jank on-device-class hardware, never blocks the buttons, and never fires twice for one completion.
- Done when process-death/rotation during the moment doesn't crash or double-award (result flow already survives via the shared VM — keep it that way).

## Scope and constraints
- **In scope:** the result surface, its content and effects.
- **Out of scope:** any change to XP/PR/achievement *amounts or detection*; the downstream Program/Home animation chain; the Recap screen.

## Decisions baked in
- Creative freedom granted (Q4); vivid celebration is an established user preference.

## Assumptions (user may override)
- **A-X1:** old→new PR weights are displayed going forward (detection-time data); no historical backfill of past dialogs.
- **A-X2:** the celebration stays a modal in-app moment (no sounds; no share button — share cards are backlog).

## Considerations for whoever builds it
- Depends on R5's tier model for unlock-card styling — same worker, R5 first.
- `LogWorkoutFragment` is heavily edited by tonight's batch-1 (rest-timer items) — this batch ships second; rebase on the settled file.
