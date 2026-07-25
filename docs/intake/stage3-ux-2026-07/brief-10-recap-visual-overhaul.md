# Brief 10 — Make the Recap tab visually interesting

**Type:** Feature (visual redesign of one screen)
**Cluster:** H1 (Recap overhaul: 3 → 9 → 14 → 10) — one worker, this LAST (it styles what 3/9/14 leave in place).

> Outcome-only brief. No confirmation round was possible (user asleep); assumptions are labelled.

## Context
After item 3 removes the overview, the Recap sub-tab is a stack of plain white-text cards built programmatically (`HistoryRecapFragment`: header, vs-last-time deltas, heaviest-weight, muscles, effort, adherence, duration, rest & pacing) — informative but visually flat compared to the rest of the Auros app (hero bands, eyebrow headers, vivid accents, gradient CTAs).

## What the user wants (end result)
The Recap reads as a **designed session summary**, not a debug printout — visually engaging in the established Auros language. Direction (builder has creative latitude within Auros patterns):
- A hero-style session header (date/day, focus muscle, and the key numbers — duration, sets, volume — as styled stat chips rather than plain lines).
- Clear section rhythm (eyebrow headers, consistent card styling) and vivid accent use where it means something: PR rows celebratory, positive deltas green/negative muted, muscle bars colored, effort visualized rather than printed.
- The new "earned this session" content (item 14) styled as highlights.

## Acceptance criteria
- Done when every section that exists after items 3/9/14 is restyled consistently with Auros (no plain-alert leftovers), with information parity — nothing informative is lost, only presented better.
- Done when the screen remains readable and performant on long sessions (many exercises), including the existing scroll-to-highlight behavior from recovery-panel taps.
- Done when empty/degenerate cases (no plan, no pacing data, no PRs) still render gracefully.
- Done when nothing outside the Recap sub-tab changes.

## Scope and constraints
- **In scope:** presentation of the per-session recap.
- **Out of scope:** data/derivations (`SessionRecap` semantics), the session picker mechanics, other tabs.

## Assumptions (user may veto)
- **A-10a:** "visually interesting" = the Auros design language already established app-wide (hero band, eyebrows, stat chips, vivid accents) — not a new visual direction.
- **A-10b:** no new charts are introduced here (the overview graphs were just removed by explicit request); visual interest comes from layout, color, and hierarchy.
