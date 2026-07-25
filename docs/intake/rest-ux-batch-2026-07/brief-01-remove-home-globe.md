# Brief 01 — Remove the "globe" art from the Home tab header

**Type:** Feature (cosmetic removal)
**Cluster:** Standalone (trivial)

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
The Home tab's hero/header band shows a faint decorative particle-sphere illustration ("the globe") right-aligned behind/next to the header text (`ill_particle_sphere` in `app/src/main/res/layout/fragment_home.xml`, added with the v1.20.0 visual pass). It appears only on the Home tab.

## What the user wants (end result)
The globe is gone. The Home header shows its existing text content with nothing in the image's place — no replacement art.

## Acceptance criteria
- Done when the Home tab header no longer shows the particle-sphere image, in both fresh installs and upgrades.
- Done when the header text/layout still renders cleanly (no leftover empty gap that looks broken, no clipped or shifted text).
- Done when no other screen is visually affected (the asset is only referenced on Home today; if the drawable becomes fully unused, removing the dead asset is acceptable housekeeping).

## Scope and constraints
- **In scope:** the Home header only.
- **Out of scope:** any other visual change to the Home tab or the app's Auros theme.

## Decisions baked in
- Remove entirely; nothing replaces it (user confirmed implicitly — no objection raised to this reading).

## Considerations for whoever builds it
- The hero band's height/padding was designed with the image present; verify the band still looks intentional without it.
