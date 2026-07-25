# Brief 06 — Move Coach Summary into "AI & Program"

**Type:** Feature (information architecture)
**Cluster:** A — Settings IA / nav (one worker).
**Outcome-only:** Describes the end result and user experience; does not prescribe implementation.

## Context
"Coach Summary" (the weekly AI coaching readout) is currently its own **top-level** Settings row.

## What the user wants (end result)
Coach Summary should no longer be a top-level row; it should be reached as a row **inside the "AI & Program" screen**.

## Acceptance criteria (Done when …)
- The **top-level** Settings list no longer shows a "Coach Summary" row.
- The **AI & Program** screen contains a "Coach Summary" row/entry that opens the existing weekly coach-summary history.
- The Coach Summary screen content is unchanged.
- The Profile tab stays correctly highlighted when navigating into it.

## Scope and constraints
- **In scope:** relocation of the Coach Summary entry point under AI & Program.
- **Out of scope:** the coach-summary content or generation.

## Decisions baked in
- Coach Summary is nested as a row inside AI & Program (confirmed).

## Considerations for whoever builds it
- Part of the same Settings IA restructure as items 3, 4, 5, 7 — one worker.

## Standing constraints
- Build with `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.
