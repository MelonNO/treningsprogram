# B06 — Tap a recovering muscle → highlight the exercise(s) that caused it

**Type:** Feature (builds on the v1.9.0 recovery rework)
**Cluster:** Profile / Home widgets (with B04)
**Outcome-only:** describes the desired end result, not the implementation.

## Context
On the Home screen, the muscle-recovery panel shows currently-recovering muscles. Tapping a recovering muscle row already opens the **last session** that trained that muscle. The user now wants that destination to make it obvious **which exercise(s)** in that session drove the muscle's fatigue.

## What the user wants (end result)
When the user taps a recovering muscle on Home and the last session that trained it opens, the session view should **scroll to and visually highlight all** exercises in that session that hit that muscle (e.g. tapping "Quads" highlights the squat and any other quad-hitting exercises in that session). The highlight lives **in the session view**; nothing about the muscle row on Home itself needs to change (no culprit-exercise name added to the Home row).

## Acceptance criteria
- Done when tapping a recovering muscle still opens that muscle's last session (unchanged), and within that session view the exercise(s) that trained the tapped muscle are clearly highlighted.
- Done when **all** exercises in that session that hit the muscle are highlighted (not just one).
- Done when the session view scrolls so the highlighted exercise(s) are visible.
- Done when sessions opened by other means (not via a recovery-muscle tap) are unaffected — the highlight only applies to the muscle the user tapped.

## Scope and constraints
- In scope: highlighting the muscle-relevant exercise(s) in the session view when arriving from a recovery-muscle tap, plus scrolling them into view.
- Out of scope: changing the recovery panel's contents/ordering, the muscle taxonomy, or adding a culprit name to the Home row.

## Considerations for whoever builds it
- "Which exercises hit a muscle" relies on the app's existing muscle attribution from the v1.9.0 recovery rework — reuse that classification rather than inventing a new one.
- Standard cross-cutting constraints apply (build via `./build.sh`; no commits/releases or UI tests unless asked).
