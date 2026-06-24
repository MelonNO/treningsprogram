# E2 — Multiple programs + periodized mesocycles

**Type:** New feature
**Cluster:** P2 (program-control) — same worker as E1, **E2 before E1.** New cross-cluster dependency: **E2's deload logic depends on B3** (see below).
**Outcome-only:** Describes the end result and user experience, not the implementation. The "how" is the orchestrator's.

---

## Context

The app currently has **no concept of a "program" as an entity.** Plans are stored only as rows in `planned_exercises`, keyed by `weekStart` + `dayOfWeek`; there is no name, no grouping, and no notion of an "active program" or a multi-week block. The AI already adapts the plan week to week based on logged performance (`generateAdaptedProgram`). This feature adds two related capabilities on top of that: **named programs the user can save and switch between**, and **periodized multi-week mesocycle blocks** whose week-to-week progression is AI-driven and whose deload is triggered by detected stalls/fatigue. This is the **most structural** item in the batch — it introduces a new program entity and reworks how plans are keyed and selected.

## What the user wants (end result)

1. **Named, switchable programs.** The user can save programs under names and switch between them (e.g. a full-gym program vs. a travel/home-gym program), with one being the active program that drives Home and the Program tab.
2. **Periodized mesocycle blocks.** A program can run as a multi-week block where:
   - **Week-to-week progression is driven by the app's existing weekly AI adaptation** based on the user's logged performance — personalized and adaptive, not a fixed deterministic ramp (**decision L1**, accepts the per-week API call).
   - **The deload is not a fixed final week.** It **kicks in when plateaus / accumulated fatigue are detected** (**decision M2**). This intentionally **couples the deload trigger to B3 (plateau/stall detection)** — the user accepts that coupling.

## Decisions baked in (confirmed)

- **L1 — AI-driven weekly progression within a block.** Progression inside a mesocycle is the existing adaptive weekly generation (logged-performance-based), not a fixed volume/intensity ramp. Per-week API call is accepted.
- **M2 — stall/fatigue-triggered deload.** The deload week is event-driven (triggered when B3-style stall/fatigue is detected), not a fixed final week of the block. E2's deload logic therefore **depends on B3**.
- **Both capabilities are in scope** (named programs *and* periodized mesocycles) — user-confirmed earlier.

## Acceptance criteria ("Done when …")

- **Done when** the user can **save a program under a name** and **switch between multiple saved programs**, with a clear notion of which one is active (driving Home's "today" and the Program tab).
- **Done when** a program can run as a **multi-week mesocycle block**, and within that block the **week-to-week plan is produced by the app's existing AI weekly adaptation** based on logged performance (not a fixed ramp).
- **Done when** a **deload is triggered by detected stalls / accumulated fatigue** (using B3's plateau/fatigue detection) rather than being a fixed final week — and the user can tell a deload is happening.
- **Done when** switching programs, advancing weeks within a block, and entering a deload all behave coherently with the rest of the app (Home today-view, guided logging, history) and do not corrupt or orphan plan data.
- **Done when** a user with a single program / no block still has a sensible default experience (this does not force everyone into mesocycles).

## Scope and constraints

- **In scope:** a named-program entity and switcher; an active-program concept; multi-week mesocycle blocks with AI-driven weekly progression; stall/fatigue-triggered deload (via B3).
- **Out of scope (unless the user later asks):** sharing programs between users; a marketplace/templates library from third parties; fully deterministic non-AI periodization (the user chose AI-driven, L1).
- **Touches generation, program storage, the Program tab, Home's today-lookup, and likely the DB schema** (new program entity) — real surface area. Also likely relevant to the in-flight cloud-backup feature (a new program entity is user data that should probably be backed up).
- **Cross-cutting constraints:** build via `./build.sh`; no git commits/releases unless the user asks; **on-device UI verification IS required for this batch** per the per-wave gate in `SEQUENCE.md`; mindful of `PreferencesManager` churn from the in-flight backup work.

## Assumptions (user may override)

- **ASSUMPTION (question N):** within a saved/named program, the AI's weekly auto-adaptation **still runs** (consistent with L1) unless the user explicitly chooses to freeze a program. *(User may want saved programs frozen by default until they opt to regenerate.)*

## Considerations for whoever builds it (surfaced, not decided)

- **Critical dependency — B3 before E2's deload (M2).** The deload trigger reuses B3's stall/fatigue detection. **B3 must exist before, or be delivered together with, E2's deload-trigger logic.** B3 is in the P1 coaching cluster and E2 is in the P2 program-control cluster, so this is a **P1 → P2 ordering edge**. E2's program-entity/storage and named-program work can proceed in parallel with B3; only the **deload-trigger piece** must wait for B3.
- **Ordering within P2:** E2 is the foundation for E1 — E2's program entity and plan re-keying must land before E1's manual editing is built (else E1 is reworked). E2 → E1, same worker.
- **`AiRepository` seam:** L1 keeps E2 inside the generation flow, which B2/B3 also edit. Per the INDEX, the AI-touching parts of P1 and P2 must be sequenced or explicitly coordinated — not edited blind.
- **Schema seam:** E2's new program entity is a DB-schema change; if B1 also persists summaries, schema bumps must be serialized (one Room version bump at a time).
