# Brief 04 — New "App Settings" screen (day-reset + auto-rebalance)

**Type:** Feature (information architecture)
**Cluster:** A — Settings IA / nav (one worker). **Item 1 rides inside this brief** (the day-reset label change), and **item 2** sets the moved-in toggle's new default.
**Outcome-only:** Describes the end result and user experience; does not prescribe implementation.

## Context
Two app-wide controls currently live in scattered places:
- the **day-reset** (day-boundary) hour picker — today inside **Training Profile**;
- the **auto-rebalance week** toggle — today buried inside the **Program tab's program-options dialog**.

The user wants a dedicated settings screen that gathers exactly these two.

## What the user wants (end result)
A new settings screen holding **exactly two** controls — the day-reset picker and the auto-rebalance toggle — reached from a new top-level Settings row. Nothing else moves in. The user delegated the name and placement to intake.

## Acceptance criteria (Done when …)
- A new top-level Settings row named **"App Settings"** exists, positioned **between "Backup & Data" and "About"** (see item 7 for the full order).
- Opening it shows a screen containing **exactly two** controls: the **day-reset (day-boundary) hour picker** and the **auto-rebalance week toggle** — and nothing else.
- The day-reset control **no longer appears in Training Profile**.
- The auto-rebalance toggle **no longer appears in the Program tab's program-options dialog**.
- Both controls remain fully functional from their new home: changing the day-reset re-applies the boundary app-wide (instant-apply, no regenerate); toggling auto-rebalance takes effect.
- The day-reset control shows 24-hour labels (item 1) in its new home.
- The Profile tab stays correctly highlighted on this screen, and the Profile-button-returns-to-root behavior (item 3) works from it.

## Scope and constraints
- **In scope:** creating the screen + row, and **relocating** exactly the two named controls.
- **Out of scope:** adding any third control; changing what either control does (aside from the day-reset's label format in item 1 and the auto-rebalance default in item 2).

## Decisions baked in
- Name: **"App Settings"** (intake's pick, per the user's explicit delegation — "name it what you think is best").
- Contents: **exactly** day-reset + auto-rebalance ("only two").
- Row placement: after Backup & Data, before About.

## Assumptions (user may override)
- **[A4-1]** The day-reset control keeps its existing **instant-apply** behavior (changing it never triggers a program regeneration).
- **[A4-2]** The auto-rebalance toggle keeps its current semantics; only its **location** changes here (its **default** is handled by item 2).

## Considerations for whoever builds it
- Item 1 (24-hour labels) applies to the day-reset control in this new home.
- Item 2 (default ON) applies to the toggle moved here.
- **Program-tab surface hazard:** removing the toggle from the program-options dialog edits `ProgramFragment.kt`, which items 8, 10, and 11 also touch — see the INDEX's cross-group hazard note.

## Standing constraints
- Build with `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.
