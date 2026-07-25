# H1 — Home information hierarchy: surface the day's action first

**Type:** Refinement (density / hierarchy)
**Cluster:** A (Home) — one worker, do this FIRST in the cluster
**Outcome-only:** describes the end result, not the implementation.

## Context
The Home tab (`fragment_home.xml`, `HomeFragment.kt`) has accumulated cards across five feature releases. After the hero band ("OVERVIEW / Today"), the always-visible content stacks in this order:

1. XP / level card
2. (deload banner — conditional)
3. Weekly Challenges card
4. **Today's plan card — contains the primary "Start Workout" button**
5. Goal nudge / Wrapped-ready / rest-day recovery (conditional)
6. Body weight card
7. Muscle recovery card
8. Recent workouts card

The single most important thing a returning daily user opens Home to do — **start today's session** — is the 3rd substantial card, sitting below the XP card and the Weekly Challenges card. On compact screens the "Start Workout" button is at risk of landing below the fold, so the primary action is not what the user sees first.

## What the user wants (end result)
Home leads with the day's action. When there is a workout to do today, the "Start Workout" card is the first thing below the hero band (or immediately after a slim XP status strip), reliably above the fold. Status/reward surfaces (XP, challenges) and reference surfaces (body weight, recent workouts, recovery) sit below the action. The screen reads top-to-bottom as: *who I am → what I do today → my progress → my reference data.*

## Acceptance criteria (Done when …)
- On a fresh launch into Home with a planned session today, the "Start Workout" card is visible without scrolling on a typical phone (≈640dp tall content area), i.e. it sits above Weekly Challenges and the body-weight/recovery/recent cards.
- The today card's four states are all preserved and correct in the new position: **Start Workout** (planned day), **Resume Workout** (active session), **View Recap** (already logged today), **Log Freestyle Session** (rest day).
- The today-related conditional nudge cards (goal nudge, Wrapped-ready, rest-day recovery) remain grouped adjacent to the today card, not stranded at the bottom.
- The XP card remains present and visually vivid; it is not removed or muted.
- No card is deleted; this is a re-order plus (per A-H1b) at most a visual tightening of the XP card into a status strip.
- All existing tap targets and navigation still work (XP card → XP log, recovery rows → Recap, Wrapped-ready → Wrapped, completed → Recap tab).
- Build passes via `./build.sh`; existing Home unit tests remain green.

## Scope and constraints
- **In scope:** the vertical order of Home cards; optionally slimming the XP card to a status strip.
- **Out of scope:** changing what any card contains (H2 handles the body-weight card), changing recovery visibility (H3), any new card, any behaviour change to Start Workout itself.
- Gamification stays vivid.

## Decisions baked in
- The day's action outranks status/reward cards in vertical priority.

## Assumptions (user may override)
- **A-H1a** — Proposed order after the hero: XP status strip → **today's plan / Start Workout** → today-related nudges → Weekly Challenges → muscle recovery → body weight → recent workouts.
- **A-H1b** — XP card stays prominent and vivid; only its position relative to the day's action changes (it may become a slimmer one-row strip so the action clears the fold).

## Considerations for whoever builds it
- If the XP card is slimmed, keep the level badge, XP bar, streak, and the tap-to-XP-log arrow — those are all live bindings in `HomeFragment`.
- The conditional cards use `visibility` gating in the collect blocks; re-ordering is XML-order plus keeping the same view IDs so bindings don't break.
- Watch the completed-today state: the today card becomes "View Recap" and the rest-day recovery card shares the same state stream — keep them coherent after the move.
