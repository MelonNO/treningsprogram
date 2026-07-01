# Brief 06 — Icon overhaul: Lucide monochrome + colorful pack (full sweep)

**Type:** Feature
**Cluster:** Independent, but **sequence LAST** — conflicts with every layout-touching item (2, 8, 9). See INDEX hazard note.
**Outcome-only:** This brief describes the end result and user experience. It deliberately does not prescribe implementation.

## Context
The app's iconography is currently a mix: custom monochrome line-vector icons for the bottom navigation (home/program/history/profile), an Android built-in gear icon in one place, and **colorful emoji** used as glyphs across roughly ten screens (muscle badges, the workout-result dialog, achievements, level/XP, etc.). The user wants a coherent, more polished icon system.

## What the user wants (end result)
A consistent, **app-wide** icon system with a deliberate two-track split:
- **Navigation and functional icons** (bottom nav, toolbar/top-menu, in-screen action buttons, settings rows, etc.) → the **Lucide** monochrome line-icon set.
- **Celebratory / muscle / achievement / gamification glyphs** (muscle badges, workout-result celebration, achievements, level/XP indicators, etc.) → **colorful**. Preferably upgraded from plain emoji to a **nicer colored icon pack** — a coloured set is welcome but not mandatory; keeping emoji is acceptable where it already reads well.

This is a **full sweep** of the app, not a partial pass.

## Acceptance criteria (Done when …)
- Navigation and functional icons across the app use **Lucide** (monochrome, consistent weight/style), replacing the current ad-hoc monochrome vectors and any Android built-in icons.
- Celebratory/muscle/achievement/gamification glyphs are **colorful** and visually cohesive — either a chosen colored icon pack or intentionally-kept emoji, applied consistently (no half-migrated look).
- The split follows the confirmed principle: functional/navigational → monochrome Lucide; gamifying/celebratory → colorful.
- Icons render correctly in both light and dark themes and at the sizes they're used.
- No screen is left with an orphaned old-style icon that breaks the new visual language (the sweep is complete).

## Scope and constraints
- **In scope:** every icon surface in the app — bottom nav, top menu/toolbar, action buttons, list-row icons, dialogs, muscle badges, result/celebration dialog, achievements, level/XP glyphs.
- The two-track split (Lucide monochrome vs colorful) is a **hard product decision** — do not make everything monochrome.
- Licensing/attribution for whichever icon packs are used must be acceptable for distribution (Lucide is permissively licensed; confirm the colored pack's license before adopting).

## Decisions baked in
- Functional/navigation → **Lucide** monochrome.
- Celebratory/muscle/achievement/gamification → **colorful** (nicer colored pack preferred over emoji, not required).
- **Full-app sweep.**

## Assumptions (user may override)
- **[A-2]** "Functional/navigation" = bottom-nav, top-menu/toolbar, and in-screen action icons; "colorful/gamification" = muscle badges, workout-result dialog, achievements, level/XP glyphs. The exact per-icon classification is the builder's judgment within this principle — surface any genuinely ambiguous icon for the user rather than guessing.
- **[A-6a]** A single colored icon pack is chosen for cohesion (rather than mixing several). If the builder finds no suitable free colored pack, keeping the current emoji for the colorful track is an acceptable fallback.

## Considerations for whoever builds it
- **Merge hazard:** items 2 (dialog), 8 and 9 (log-workout layout) also edit layout files. Do this sweep **after** those land, or rebase onto them, to avoid conflicts on shared layouts.
- Watch for icons that carry meaning by color (e.g. a red "missed" vs green "done" indicator) — preserve that semantic color when migrating.
- Keep the bottom-nav selected/unselected states working with the new icons.

## Standing constraints
- Build with `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.
