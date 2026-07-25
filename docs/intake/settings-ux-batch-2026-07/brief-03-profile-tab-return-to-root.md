# Brief 03 — Profile button returns to the Profile tab's main menu from any sub-setting

**Type:** Bug / navigation behavior
**Cluster:** A — Settings IA / nav (one worker). Shares the `MainActivity` nav wiring + `nav_graph` with the rest of the cluster.
**Outcome-only:** Describes the end result and user experience; does not prescribe implementation.

## Context
The bottom-nav **Profile** tab hosts a Profile root screen, from which the user reaches the Settings list and then its sub-screens (Training Profile, AI & Program, Exercise Library, Backup & Data, App Settings, About, Coach Summary, Prompt Log, etc.). The user reports that when they are inside a sub-setting, pressing the Profile button does not cleanly return them to the Profile tab's main menu in one tap (they described needing to press it again to get out).

## What the user wants (end result)
> "If the user is in a sub-settings menu already and they then again press the profile button they get back to the 'main menu' in the profile tab."

A **single** tap on the Profile bottom-nav button, from any Settings sub-screen, returns to the **Profile tab's root/main menu**.

## Current vs correct behavior
- **Current:** from a settings sub-screen, tapping the Profile button does not reliably return to the Profile tab's main menu in one tap.
- **Correct:** one tap on the Profile button always lands on the Profile tab's root/main menu, from anywhere in the settings hierarchy.

## Diagnose first
- Confirm on-device exactly what a single Profile tap does today from a sub-screen (no-op / one-of-two-taps / partial). The fix must guarantee a single tap reaches the Profile root.

## Acceptance criteria (Done when …)
- From **any** Settings sub-screen (Training Profile, AI & Program and its Coach Summary sub-row, Exercise Library, Backup & Data and its Debug sub-rows, App Settings, About, Prompt Log, etc.), a **single** tap on the Profile bottom-nav button navigates to the Profile tab's root/main menu.
- No requirement to tap twice.
- The Profile tab stays correctly highlighted throughout.

## Scope and constraints
- **In scope:** Profile-tab reselection behavior.
- **Out of scope:** the other bottom-nav tabs' reselection behavior.
- **Note:** the target is the **Profile tab root**, not the Settings list — confirmed by the user (the Profile root sits above the Settings list in the hierarchy).

## Decisions baked in
- Destination on Profile-button tap from a sub-setting = the Profile tab's root/main menu (one tap).

## Considerations for whoever builds it
- The final row order (item 7), the new App Settings destination (item 4), and the relocated Debug/Coach Summary rows (items 5/6) all land in this same cluster — build them together so nav mappings stay consistent.

## Standing constraints
- Build with `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.
