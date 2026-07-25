# Brief 05 — Debug becomes a sub-menu under Backup & Data

**Type:** Feature (information architecture)
**Cluster:** A — Settings IA / nav (one worker).
**Outcome-only:** Describes the end result and user experience; does not prescribe implementation.

## Context
"Debug" (prompt log, rejection log, crash log, diagnostics) is currently its own **top-level** Settings row. "Backup & Data" is a separate top-level row.

## What the user wants (end result)
Debug should no longer be a top-level row; it should be reached as a row **inside the Backup & Data screen**.

## Acceptance criteria (Done when …)
- The **top-level** Settings list no longer shows a "Debug" row.
- The **Backup & Data** screen contains a "Debug" row/entry that opens the existing Debug screen.
- The Debug screen and all its sub-screens (prompt log, rejection log, crash log, diagnostics) are unchanged in content and remain reachable.
- The Profile tab stays correctly highlighted when navigating into Debug and its sub-screens.

## Scope and constraints
- **In scope:** relocation of the Debug entry point under Backup & Data.
- **Out of scope:** the contents/behavior of Debug or any of its sub-screens.

## Decisions baked in
- Debug is nested as a row inside Backup & Data (confirmed).

## Considerations for whoever builds it
- Part of the same Settings IA restructure as items 3, 4, 6, 7 — one worker.

## Standing constraints
- Build with `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.
