# E3 — Exercise library browser with instructions

**Type:** New feature
**Cluster:** P3 (parallel analytics) — independent worker.
**Outcome-only:** Describes the end result and user experience, not the implementation. The "how" is the orchestrator's.

---

## Context

The app already ships a bundled, on-device exercise catalog (`data/ExerciseCatalog.kt`) where each entry carries `instructions`, `primaryMuscles`, `secondaryMuscles`, `equipment`, `level`, `category`, and `images`. `WgerRepository` additionally fetches images live for anything not in the catalog. Despite all this content being on-device, there is **no way for the user to browse it** — they can only encounter exercises via the AI plan or the in-session "add exercise" picker. This feature is therefore **mostly a presentation layer over data the app already has** — no new data source, no API dependency for the core content, no schema change.

## What the user wants (end result)

The user can browse and search a library of exercises, and tap any exercise to see its details — target muscles, equipment, and how-to **instructions** (and an image where available). A reference they can consult to learn movements.

## Acceptance criteria ("Done when …")

- **Done when** the user can open an exercise library and **browse/search** exercises, filterable in a sensible way (e.g. by muscle group and/or equipment).
- **Done when** tapping an exercise opens a **detail view** showing its target muscle(s), equipment, and how-to **instructions**, with an image where one is available.
- **Done when** the library reflects the **full bundled catalog** content (not only the ~26 seeded defaults), since that content already ships on-device.
- **Done when** an exercise lacking an image or instructions degrades gracefully (no broken/empty detail screen).
- **Done when** the library is reachable from a sensible entry point in the app.

## Scope and constraints

- **In scope:** a browsable/searchable exercise library with a detail view (muscles, equipment, instructions, image), over the bundled catalog.
- **Out of scope (unless the user later asks):** video demonstrations; user-authored custom instructions; editing catalog content.
- **Core content is local** (bundled catalog); live image fetch via the existing `WgerRepository` is acceptable but not required for the core experience.
- **Cross-cutting constraints:** build via `./build.sh`; no git commits/releases unless the user asks; **on-device UI verification IS required for this batch** per the per-wave gate in `SEQUENCE.md`.

## Assumptions (user may override)

- **ASSUMPTION (interactivity, question O):** the browser is **browse-only reference** in the first version. An action such as "add to today's plan" or "swap into plan" is noted as an **optional nice-to-have**, not required. *(User may want it actionable from day one — that would create a light touch-point with the program/plan code.)*
- **ASSUMPTION (scope, question P):** the library exposes the **full bundled catalog** (already on-device with instructions/images), not just the ~26 default exercises. *(User may prefer to scope it to defaults + user-added.)*

## Considerations for whoever builds it (surfaced, not decided)

- This is the most self-contained item in the batch: a new screen over bundled content. If kept **browse-only** (the assumption), it touches no shared seam and is fully parallel-safe.
- If the user later wants it **actionable** (add/swap into the plan), that introduces a touch-point with program storage / the Program tab — at which point it should coordinate with the P2 (program-control) worker.
