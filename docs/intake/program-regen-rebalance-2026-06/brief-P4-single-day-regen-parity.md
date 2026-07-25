# P4 — Single-day regeneration must behave like the weekly generator (real weights, variety, verification)

**Type:** Bug + feature (merges the user's original Items 3 and 5 — same underlying change)
**Cluster:** Single-day regeneration seam (`AiRepository.generateSingleDayProgram`). FOUNDATION for the week-rebalance items (P1, P2), which must reuse the corrected weight/parity behavior established here.
**Outcome-only:** describes the desired end result, not the implementation. Cause of the two symptoms is **largely diagnosed below — confirm before acting**; the exact fix belongs to the orchestrator.

## Context
On the Program tab, the **"Swap [day]'s workout"** dialog regenerates a **single** day via `AiRepository.generateSingleDayProgram`. This single-day path is **not** at parity with the full weekly generator (`generateAdaptedProgram`): the weekly path fetches workout history, sets real history-driven weights, runs an up-to-3-attempt retry loop with the strict per-day time-budget gate, and runs a `validateProgram` peer-review ("verification") pass. The single-day path does none of these — it is a single attempt, with no history, no real weights, and no verification.

The user reported two distinct symptoms on the single-day regen, with pasted prompt+response evidence.

## Current (incorrect) vs correct behavior
**Current (incorrect):**
- **(a) Same workout:** regenerating a day returns essentially the **same** exercises each time (e.g. a Chest day re-rolls to the same canonical chest list — Barbell Bench Press, DB Incline Bench, DB Flye, …).
- **(b) All bodyweight:** **every** exercise on the regenerated day comes back with its weight set to **0 / bodyweight** (`"targetWeightKg":0` for all exercises), even for loaded movements.

**Correct:** single-day regeneration behaves **exactly like a weekly generation, scoped to one day**:
- Fetches workout history and sets **real, history-driven target weights** (progressing across sessions the way the weekly path does); genuine bodyweight movements still legitimately show BW.
- **Respects the user-selected muscle focus** — if the user picks Chest, the day must be chest; variety is expected **within** that chosen focus, but the chosen focus itself is fixed.
- Runs the **full machinery**: the up-to-3-attempt **retry loop**, the **strict per-day time-budget gate** (±10 min of the session length), **and** the `validateProgram` **peer-review/verification pass**.
- **Fully replaces** the day, including any sets already logged on that day.

## Diagnose first (leads grounded in the code — confirm, don't assume)
- **(b) all-BW** most likely cause: the single-day prompt is given **no workout history and no current weights**, and its return-shape example **hardcodes `"targetWeightKg":0`**; the model echoes 0 for every exercise and the app stores exactly what it returns. (The weekly prompt includes history and a non-zero weight example, which is why the bug is single-day-only.)
- **(a) same workout** most likely cause: the single-day path **excludes the current day from its context** and passes **no history and no variation signal**, so a fixed muscle focus yields the same deterministic canonical list on every re-roll.
- Evidence (from the user's paste; full prompt+response available from the coordinator on request): the regenerated Saturday returned the canonical chest movements with `"targetWeightKg":0` on **every** exercise; the single-day prompt template itself hardcodes `"targetWeightKg":0` and supplies no history/weights.

## Acceptance criteria (observable)
- **Done when** a regenerated day shows **real, history-informed target weights** — it never sets every exercise to 0/BW merely because it is a single-day regen; legitimate bodyweight moves still show BW.
- **Done when** the regenerated day **respects the user-selected muscle focus** (e.g. Chest → chest), with sensible variety **within** that focus.
- **Done when** the single-day regen runs the **same retry loop, strict per-day time-budget gate, and `validateProgram` peer-review pass** as the weekly generator.
- **Done when** the single-day regen **fully replaces** the day, including any previously logged sets on it.
- **Done when** both reported symptoms (same-workout, all-BW) no longer occur.

## Scope and constraints
- **In scope:** bringing `generateSingleDayProgram` to parity with the weekly generator, scoped to one day (history → real weights, retry loop, strict time-budget gate, verification pass).
- **Hard constraint (carried forward from G1/H3, do not reopen):** keep the per-day time-budget gate **strict**; do not loosen it or add a salvage path beyond whatever the weekly path already does.
- **Standard cross-cutting constraints:** build via `./build.sh`; no commits/releases unless asked; no on-device/UI tests unless asked.

## Decisions confirmed by the user (2026-06-28, via coordinator)
- Weights: **full weekly parity** — history-driven, progressed (not blank, not all-BW).
- Variety: governed by the **user-selected focus** — chosen focus is fixed; variety lives within it.
- Replace: the regen **fully replaces** the day, **including any logged sets** on it (deliberately different from the preserve-logged behavior used elsewhere — this is the explicit single-day regen the user invokes).

## Considerations for whoever builds it
- The corrected real-weight behavior here is **reused by the P1/P2 week-rebalance items** — land this first.
- "Behave exactly like week gen" was confirmed to include the **retry loop and the strict time-budget gate**, not only "real weights + verification." If the gate proves structurally hard to satisfy for a single day, that is a known class issue (see prior G1/H3) — do not silently loosen it; surface it.
