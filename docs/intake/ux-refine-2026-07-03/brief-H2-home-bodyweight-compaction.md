# H2 — Home body-weight card: compact the daily footprint

**Type:** Refinement (density)
**Cluster:** A (Home) — after H1
**Outcome-only.**

## Context
The Home body-weight card (`fragment_home.xml` card #4, wired in `HomeFragment.renderBodyWeightEntries` / body-weight collect block) always shows a full weigh-in input row — a "Weight (kg)" text field plus a "Log" button — plus up to five recent entries, every single day. A user typically weighs in at most a few times a week, so on most days this is a prominent data-entry card occupying vertical space for an action that isn't being taken. It competes with higher-value cards for the fold.

## What the user wants (end result)
The body-weight card earns its space. On a normal day it presents as a compact glance — current weight and the smoothed trend line (when at least two weigh-ins exist) — and the weigh-in input is revealed on demand (e.g. a tap/expand or a small "＋ Log weight" affordance) rather than sitting open permanently. Logging a weight stays quick; the card just stops shouting when nothing is being entered.

## Acceptance criteria (Done when …)
- On a day with existing weigh-ins, the body-weight card shows the current weight + trend line without an always-open input field taking a full row.
- The weigh-in input is reachable in one obvious tap and, once revealed, logging behaves exactly as today (enter a number → Log → entry appears, field clears).
- The existing trend line behaviour is preserved: hidden until ≥2 weigh-ins exist, shown otherwise.
- The zero-weigh-in state stays graceful (a clear invitation to log the first weight, no crash, no empty trend).
- Recent entries remain viewable (they may move behind the same expand affordance) and the long-press-to-delete gesture still works (see H4 for its safety fix).
- Build passes; body-weight unit tests remain green.

## Scope and constraints
- **In scope:** the visual footprint and progressive-disclosure of the body-weight card only.
- **Out of scope:** the body-weight data model, the Progress-tab body-weight chart, the trend maths (`WeightTrend`), AI use of body weight.
- No schema change.

## Decisions baked in
- The card's daily default is a glance, not an open form.

## Assumptions (user may override)
- **A-H2a** — Collapse to a compact glance with on-demand input, rather than removing the card or keeping the input permanently open. If the user prefers the input always-visible, this item is dropped.

## Considerations for whoever builds it
- Keep `et_home_bodyweight` / `btn_home_add_weight` IDs (or re-wire cleanly) — `HomeFragment` reads them directly.
- The five-entry list is built in `renderBodyWeightEntries`; if it moves behind an expand, preserve the long-press delete handler (H4 makes it safe).
