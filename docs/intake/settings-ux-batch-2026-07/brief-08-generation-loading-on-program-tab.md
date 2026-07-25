# Brief 08 — Generation loading animation present on the Program tab

**Type:** Feature
**Cluster:** B — generation launched from Settings (one worker, with item 9).
**Outcome-only:** Describes the end result and user experience; does not prescribe implementation.

## Context
When the user starts a full-program generation from **Training Profile** ("Generate now" after saving) or from **AI & Program**, the in-progress state and status are shown only on the Settings screen. The Program tab has **no** full-generation loading indicator — it only shows progress for single-day regen. So a user who starts a generation from Settings and then flips to the Program tab sees no sign that a program is being built.

## What the user wants (end result)
While a full-program generation triggered from a Settings screen is running, the **Program tab shows a loading/generating animation** if/when the user navigates there. The app should **not** auto-switch the user to the Program tab, and the Settings screen should **keep** showing its own status (additive — both surfaces reflect the same in-progress generation).

## Acceptance criteria (Done when …)
- When a full-program generation started from Training Profile "Generate now" **or** from AI & Program is in progress, navigating to the Program tab shows a generating/loading animation there.
- The app does **not** automatically switch to the Program tab when generation starts.
- The Settings screen that launched the generation **continues** to show its own status/progress (the handoff is additive, not exclusive).
- When generation finishes (success or failure), the Program tab loading animation clears; on success the tab shows the resulting plan, on failure it shows the prior state (no phantom spinner).

## Scope and constraints
- **In scope:** surfacing the existing full-generation in-progress state on the Program tab.
- **Out of scope:** changing generation logic/quality; the single-day regen progress (already exists) is untouched.

## Decisions baked in
- No auto-switch to the Program tab.
- Settings status stays; Program-tab animation is additive.

## Assumptions (user may override)
- **[A8-1]** The Program-tab animation reflects the **same** generation the Settings status reflects (one shared in-progress signal), so the two surfaces always agree.

## Considerations for whoever builds it
- The full-generation in-progress signal currently lives on the Settings-side view model; the Program tab needs to observe an equivalent app-scoped signal to show/hide its animation.
- **Program-tab surface hazard:** this adds a loading view to the Program tab (`fragment_program.xml` / `ProgramFragment.kt`), which items 4 (dialog edit), 10, and 11 also touch — see the INDEX cross-group hazard note; coordinate ownership/sequencing.

## Standing constraints
- Build with `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.
