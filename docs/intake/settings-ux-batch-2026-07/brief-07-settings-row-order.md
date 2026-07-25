# Brief 07 — Reorder the top-level Settings rows

**Type:** Feature (information architecture)
**Cluster:** A — Settings IA / nav (one worker). Integrates with items 4 (new row), 5 and 6 (removed top-level rows).
**Outcome-only:** Describes the end result and user experience; does not prescribe implementation.

## Context
The current top-level Settings order is: Training Profile, Exercise Library, Coach Summary, AI & Program, Backup & Data, Debug, About. Items 5 and 6 remove Debug and Coach Summary from the top level; item 4 adds an "App Settings" row.

## What the user wants (end result)
The user asked for this sequence: **training profile, ai and program, exercise library, backup and data, about.** Combined with items 4/5/6, the final top-level list is:

**Training Profile → AI & Program → Exercise Library → Backup & Data → App Settings → About**

## Acceptance criteria (Done when …)
- The top-level Settings list shows exactly, in this order:
  1. Training Profile
  2. AI & Program
  3. Exercise Library
  4. Backup & Data
  5. App Settings
  6. About
- No "Coach Summary" or "Debug" rows appear at the top level (they now live under AI & Program and Backup & Data respectively).

## Scope and constraints
- **In scope:** the top-level row order and membership.
- **Out of scope:** the sub-screens' internal contents.

## Decisions baked in
- Order as listed above; "App Settings" sits between Backup & Data and About (intake's placement, per the user's delegation on item 4).

## Considerations for whoever builds it
- This is inseparable from items 4/5/6 — same worker, single coherent edit of the Settings list.

## Standing constraints
- Build with `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.
