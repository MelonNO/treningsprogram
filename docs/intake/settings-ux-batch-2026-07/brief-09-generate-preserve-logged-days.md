# Brief 09 — "Generate now" from Settings must not overwrite already-logged days

**Type:** Bug
**Cluster:** B — generation launched from Settings (one worker, with item 8).
**Outcome-only:** Describes the end result and user experience; does not prescribe implementation.

## Context
Confirmed in code: the Settings generate path is a **full-fresh regen that replaces every day of the week, including already-logged ones** (the code comment states it outright). The Program tab already has a **"Regenerate (keep logged days)"** action that preserves logged days and rebalances the rest of the week around them — but the two Settings entry points do not use it.

## What the user wants (end result)
> "The 'generate now' feature from the training profile generates new workouts on the already-logged days; it should NOT do this."

Generation triggered from Settings should **preserve days that already have logged activity** and only rebuild/rebalance the non-logged remainder of the week — the same behavior as the Program tab's "Regenerate (keep logged days)."

## Current vs correct behavior
- **Current:** "Generate now" from Training Profile (after saving settings) **and** the AI & Program generate both wipe and replace already-logged days.
- **Correct:** both preserve already-logged days and rebalance the rest of the week around them.

## Acceptance criteria (Done when …)
- Triggering generation from **Training Profile "Generate now"** preserves any day that already has logged activity, and only rebuilds/rebalances the non-logged remainder of the week.
- The **AI & Program** generate entry point does the same.
- Already-logged workouts are **unchanged** after generation (their sets, weights, and history are intact).
- The rest of the week is **rebalanced** around the preserved days.

## Scope and constraints
- **In scope:** the two Settings generate entry points (Training Profile "Generate now"; AI & Program generate).
- **Out of scope:** the automatic start-of-week generation (it only generates when the week is empty, so it does not overwrite logged days — but if the builder finds it can, surface it).
- **Reuse the existing keep-logged-days + rebalance mechanism** — do not invent a second preservation path.

## Decisions baked in
- Apply to **both** Settings entry points.
- Match the existing "Regenerate (keep logged days)" preserve-and-rebalance behavior.

## Considerations for whoever builds it
- Settings-side change (the generate action + how the resulting plan is saved). Coordinate with item 8 (same cluster/worker) since both concern generation launched from Settings.

## Standing constraints
- Build with `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.
