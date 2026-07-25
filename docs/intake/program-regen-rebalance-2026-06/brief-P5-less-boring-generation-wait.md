# P5 — Make the generation wait less boring with changing, informative text (real status always visible)

**Type:** Feature / UX
**Cluster:** Generation-wait UX (with **P3**). Both wrap the generation lifecycle.
**Outcome-only:** describes the desired end result, not the implementation.

## Context
While a plan generates, the app shows a **single real-status line** (e.g. "Generating your plan…", "Attempt 2 of 3", "Reviewing plan for quality…") sourced from the generation flow's per-attempt progress callback. The user finds the wait boring and wants the on-screen text to change and inform, without losing the real status.

## What the user wants (end result)
- During the wait, the displayed text should **change over time** and be **informative** so the wait is less boring.
- It must be a **combination**: friendly/informative content **and** the **real generation status**.
- The **real status must stay visible at all times** — either **combined with** the friendly text or shown as a **separate line**.
- On **all screens** where the user waits for a generation (Setup wizard, Settings, the Program-tab day regen, and the P1/P2 rebalances).
- **Content/tone is open** — "everything is acceptable."

## Current vs correct behavior
- **Current:** a static single status line during the wait.
- **Correct:** changing, informative text during the wait, with the real generation status always visible (combined or separate), on every generation-wait screen.

## Acceptance criteria (observable)
- **Done when**, on every generation-wait screen, the displayed text **changes over time** (not a single static line) and is informative/non-boring.
- **Done when** the **real generation status remains visible at all times** (combined with the friendly text, or as a separate line).
- **Done when** this applies to **all** generation entry points (setup, settings, Program-tab regen, P1/P2 rebalances).

## Scope and constraints
- **In scope:** the presentational wait UI on all generation-wait screens; reusing the existing per-attempt progress status as the "real status" element.
- **Out of scope:** changing generation logic, timing, or the progress callback's meaning — this is purely presentational.
- **Standard cross-cutting constraints:** build via `./build.sh`; no commits/releases unless asked; no on-device/UI tests unless asked.

## Decisions confirmed by the user (2026-06-28, via coordinator)
- A **combination** of friendly content and real status; the **real status must stay visible** (combined or separate); **all** waiting screens; **any** content/tone acceptable.

## Decisions deferred / assumptions (user may override)
- **[P5-A1] The rotating copy** (training tips / app facts / encouragement) is the builder's to write, since the user said any content is acceptable. The user may supply specific copy.
- **[P5-A2] Rotation cadence and number of messages** are left to the builder.
