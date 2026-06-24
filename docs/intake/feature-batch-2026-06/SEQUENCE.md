# Implementation Sequence Plan — Feature Batch (2026-06)

**Prepared for:** Project-lead orchestrator (enforced by the coordinator, one wave at a time)
**Scope:** Prescribes the **exact order and the hard gates** for implementing the 8 features (B1, B2, B3, C1, C4, E1, E2, E3). This is the **enforceable ordering contract** — not advice. It is **sequence-level only**: it dictates *order and dependencies*, never *how* to build each item (the "how" remains the orchestrator's and its workers').
**Derived from:** the dependency/parallelization analysis in `INDEX.md`. Read the briefs for each item's outcomes and acceptance criteria.

---

## Status / authority note

These are intake artifacts. **Nothing here has been dispatched to the orchestrator** — that is a separate, later instruction the user gives directly. All product decisions were confirmed by the user directly (see `INDEX.md`).

---

## The non-negotiable dependencies (the gates this plan enforces)

These four constraints determine the entire sequence. Every wave boundary below exists to honor one or more of them.

1. **B3 → E2-deload (M2 coupling).** E2's deload is stall/fatigue-triggered and reuses B3's detection. **B3 must be complete before E2's deload-trigger logic is built.** (E2's *non-deload* parts are not gated by B3.)
2. **E2-storage → E1 (program-model foundation).** E2 introduces the new "program" entity and re-keys how plans are stored/selected. **E1's manual editing must be built on that settled model**, never before it.
3. **`AiRepository` seam — serialize, never concurrent.** B2 (rationale field), B3 (stall signal into prompt), and E2 (L1 keeps generation adaptive/program-aware) **all edit the generation prompt/response in `AiRepository`**. These edits **must be serialized** — at most one unit editing `AiRepository` at a time.
4. **DB schema / Room version — one bump at a time.** B1 (if summaries persist), B2 (if the rationale persists with the plan), and E2 (program entity) each may add a table / bump the Room version. **Schema bumps must be sequenced**, never authored concurrently — applied in wave order (Wave 2's B2/B1 bumps, then Wave 3's E2 bump).

---

## Verification gate between every wave (MANDATORY, EVIDENCE-BACKED, PER-WAVE)

This is a **hard gate**, not a soft "should be green." A wave is **not complete** until the orchestrator produces a **Wave Verification Report** that proves the wave is **fully tested**, and the **coordinator reviews and confirms an unambiguous PASS**. **No confirmed PASS → the next wave is not dispatched.** This repeats per wave, all the way through Wave 4 (Wave 1 fully tested + confirmed → then Wave 2 dispatched → fully tested + confirmed → then Wave 3 → … → Wave 4).

### Definition of "fully tested" for a wave (ALL four required)

1. **Build green** — the wave builds via **`./build.sh`** (not `./gradlew` directly), covering **both debug and release compile**. Report the commands run and their success.
2. **Unit/integration tests pass** — `./build.sh test` passes, **including the wave's own new tests**. Report **test counts** (run / passed / failed / skipped) and name the new tests added for this wave's units.
3. **On-device UI verification via `ui-test-worker`** — for **every UI-affecting feature in the wave**, real on-device evidence (Maestro flow result, UI hierarchy dump, and/or screenshots) demonstrating the feature's **acceptance criteria** (the brief's "Done when …" statements) are met. The orchestrator spawns `ui-test-worker` per its normal workflow. **A non-UI / analytics-only unit with no UI surface may instead state "no UI surface — JVM-tested only" with a one-line justification** — but any unit that renders or changes UI **must** have on-device evidence.
4. **Shared seams left coherent** — `AiRepository` and the Room schema/migrations are in a **consistent, coherent state** at wave end (no half-applied prompt/response change, no dangling/disordered migration), ready for the next wave's seam-touching work.

### The Wave Verification Report (the orchestrator produces this per wave)

For each wave, the orchestrator produces a report containing, with **evidence**:
- the build result (commands + outcome, debug + release);
- the test result (counts + the wave's new tests);
- per UI-affecting unit: the `ui-test-worker` evidence (flow result + hierarchy/screenshots) mapped to that unit's acceptance criteria — or the justified "no UI surface" note for analytics-only units;
- the state of any shared seam touched (`AiRepository`, Room schema/migrations);
- an **explicit, unambiguous `PASS`** for the wave as a whole.

### Coordinator enforcement (hard rules)

- The **coordinator will NOT dispatch the next wave** until it has **received and reviewed** the current wave's Wave Verification Report **and** seen the explicit confirmed `PASS`.
- **No confirmation → no next wave.** A missing, ambiguous, partial, or failed gate is **not waved through.** A failed or partial gate means the wave is **reworked and re-verified** (a fresh Wave Verification Report) before the next wave can start.
- This applies **per wave**, in order, through Wave 4.

> **Standing-constraint note for implementers:** the batch's general rule is "no on-device/automated UI tests and no git commits/releases unless the user explicitly asks." This plan's requirement of `ui-test-worker` on-device verification per wave **is** that explicit ask, scoped to verifying these features — so it does not conflict with the general rule. The general rule still holds for **git commits/releases**: nothing is committed/tagged/released unless the user separately asks. ("Coherent seam state" above means a consistent working tree, not a requirement to commit.)

---

## Waves

### Wave 1 — Safe parallel analytics + B3 (the prerequisite)
**Runs fully in parallel — independent workers, no shared seams between them.**

| Unit | Why it's here | Parallel-safe? |
|---|---|---|
| **C1** — estimated-1RM / PR trends | Read-only over existing strength data; lives on `RecapTrendsFragment`, which no other item touches. | Yes |
| **C4 (view only)** — recovery/freshness panel | Read-only local computation; **placed on Home** (locked) to keep it off the Program tab and fully independent. **Only the view is in Wave 1.** | Yes |
| **E3** — exercise library browser | New screen over the already-bundled `ExerciseCatalog`; browse-only, no shared seam. | Yes |
| **B3** — plateau/stall detection | **Pulled early because E2's deload depends on it** (dep #1). Detection itself is local; its *prompt* touch is the `AiRepository` seam — see serialization note. | Detection: yes. Prompt edit: serialized (see below). |

**Within-wave serialization:** C1, **C4's view**, and E3 run concurrently with no coordination — all three are read-only/local with no shared seam. **B3 is the only Wave-1 unit that touches `AiRepository`** (when it feeds the stall signal into the prompt), so in Wave 1 **B3 owns the `AiRepository` seam alone** — no other unit edits that file in this wave.

**C4 is split (important):** only **C4's recovery VIEW** is in Wave 1 (parallel-safe, Home-placed). **C4's AI-nudge piece** (feeding recovery state into the generation prompt, assumption J) **touches `AiRepository`** and is therefore **NOT in Wave 1** — it is sequenced onto the `AiRepository` track (delivered with the AI-coaching `AiRepository` work or with E2's generation work, whichever owns that seam at the time). If the user overrides assumption J to inform-only, the nudge piece disappears and C4 is purely the Wave-1 view. *(See C4's brief, "Two pieces.")*

**Notes / placement decisions that keep Wave 1 clean:**
- **C4 view → Home (locked), not Program tab.** Placing it on the Program tab would touch `ProgramFragment` and collide with the later P2 program-control work. Home keeps it independent. *(If the user overrides to the Program tab, C4's view moves to after Wave 3 — flag to user.)*
- **C1's estimated-1RM formula must match B3's** (both use estimated 1RM). Same wave — agree the formula (Epley per C1's assumption) once, use it in both.

**Gate to leave Wave 1 (full Wave Verification Report + confirmed PASS required):** debug+release build green; unit tests pass with counts incl. new tests; **on-device `ui-test-worker` evidence for C1, C4's view, and E3** (all three render UI) mapped to their acceptance criteria — B3's detection is analytics; verify B3's user-facing alert on-device, else justified JVM-only for pure detection logic; `AiRepository` coherent after B3's prompt edit; B3 fully complete (hard prerequisite for Wave 3's E2-deload). Coordinator confirms PASS before Wave 2.

---

### Wave 2 — Remaining AI coaching (B1, B2)
**One worker (the P1 coaching cluster continues). Internally serialized on the shared seams.**

| Unit | Seam it touches | Ordering |
|---|---|---|
| **B2** — program-change rationale | `AiRepository` generation **response** (adds `rationale` field). **Possible small Room bump** if the rationale persists with the plan (assumption D). | Edits `AiRepository` — must not overlap any other `AiRepository` edit. Any schema bump sequenced per dep #4. |
| **B1** — weekly coach summary | New persisted summary store (if persisted per assumption C) → **DB schema/Room bump**. Reuses history-context (read-only). | If it bumps the schema, that bump is sequenced vs. B2's and E2's (dep #4). |

**Why a separate wave from B3:** B2, B3, and E2 all edit `AiRepository`; serializing them across waves (B3 in Wave 1, B2 in Wave 2, E2 in Wave 3) is the simplest way to guarantee dep #3 ("never concurrent on `AiRepository`"). B1 has no `AiRepository` write conflict but shares the P1 worker and may bump the schema, so it sits here too.

**Within-wave serialization:** do **B2's `AiRepository` edit first** (single coherent change to the generation response), then **B1**. Any Room bumps in this wave (B2's rationale store if persisted, and/or B1's summary store) are applied **one version at a time**, and the resulting Room version is recorded so Wave 3's E2 schema work stacks on top of it (dep #4).

**Gate to leave Wave 2 (full Wave Verification Report + confirmed PASS required):** debug+release build green; unit tests pass with counts incl. new tests; **on-device `ui-test-worker` evidence for B1 and B2** (both surface UI — the weekly summary and the program-change rationale) mapped to their acceptance criteria; `AiRepository` coherent after B2's response edit; the Room version after B1 recorded as the known baseline E2 builds on. Coordinator confirms PASS before Wave 3.

---

### Wave 3 — E2 (programs + mesocycles), internally ordered
**One worker (P2 program-control). E2 is the critical path and the heaviest item; it is split internally by dependency.**

E2 is implemented in this **internal order**:

1. **E2-storage** — the new "program" entity, named-program save/switch, active-program concept, and the re-keying of plan storage. *(Not gated by B3. This is the foundation E1 needs.)* **DB schema/Room bump here is sequenced after Wave 2's B1 bump (dep #4).**
2. **E2-generation (L1)** — make weekly adaptive generation program/mesocycle-aware. **This edits `AiRepository`** → it must follow B3 (Wave 1) and B2 (Wave 2) on that seam, never concurrent (dep #3). By Wave 3, P1's `AiRepository` work is complete, so E2 now owns that seam alone.
3. **E2-deload (M2)** — the stall/fatigue-triggered deload, **reusing B3's detection.** **Hard gate: B3 (Wave 1) must be complete** — it is, by construction. *(dep #1.)*

**Why E2 is its own wave and not parallel with Waves 1–2:** its `AiRepository` work (step 2) and its schema bump (step 1) both collide with P1's seams if run concurrently. Sequencing E2 after the P1 `AiRepository` edits and after the B1 schema bump removes both collisions.

**Gate to leave Wave 3 (full Wave Verification Report + confirmed PASS required):** debug+release build green; unit tests pass with counts incl. new tests; **on-device `ui-test-worker` evidence for E2** (program save/switch, mesocycle block progression, and a triggered deload) mapped to its acceptance criteria; `AiRepository` coherent after E2's L1 generation work; Room schema/migrations coherent after E2's program-entity bump; the program/storage model **settled in the working tree** (E1 builds directly on it). Coordinator confirms PASS before Wave 4.

---

### Wave 4 — E1 (manual program editing)
**One worker (P2 continues). Single unit.**

- **E1** — edit/delete/add/reorder planned exercises. **Built strictly on E2's settled program/storage model (dep #2).** Operates over existing `planned_exercises` storage as re-keyed by E2; no API; no further schema bump expected. Manual edits last until the next regeneration (accepted).

**Gate to leave Wave 4 (full Wave Verification Report + confirmed PASS required):** debug+release build green; unit tests pass with counts incl. new tests; **on-device `ui-test-worker` evidence for E1** (edit/delete/add/reorder a planned exercise, persistence across the relevant screens) mapped to its acceptance criteria; seams coherent. Coordinator confirms PASS — batch complete.

---

## At-a-glance

| Wave | Units | Parallelism | Gates honored |
|---|---|---|---|
| **1** | C1, **C4 view**, E3, **B3** | C1/C4-view/E3 fully parallel; B3 alone owns `AiRepository` this wave (C4's AI-nudge is NOT here — it is on the serialized seam) | B3 done before E2-deload (dep #1); B3 is the only seam edit |
| **2** | B2, B1 | One worker; B2 `AiRepository` edit first, then B1 | `AiRepository` serialized (dep #3); B2/B1 schema bumps sequenced one at a time (dep #4) |
| **3** | E2 (storage → generation(L1) → deload(M2)) | Single critical-path unit, internally ordered | E2-deload needs B3 (dep #1); E2-gen serialized on `AiRepository` (dep #3); E2 schema after B1 (dep #4) |
| **4** | E1 | Single unit | E1 built on E2's model (dep #2) |

**Critical path:** B3 (W1) → B2 (W2) → E2 (W3) → E1 (W4). The P3 analytics track (C1, C4, E3) rides Wave 1 in parallel and is off the critical path.

**Each wave must pass the full evidence-backed verification gate — a Wave Verification Report with an explicit confirmed PASS — before the next begins.** The coordinator dispatches one wave at a time in this exact order, and **never dispatches the next wave without that confirmed PASS in hand.**

---

## What this plan does NOT decide (still the orchestrator's)

- How each feature is implemented internally (data structures, classes, prompt wording, UI layout).
- Whether to split a wave's parallel units across multiple workers or run them with one — only that they *may* run in parallel where stated.
- The exact verification *mechanics* beyond the gate definition above (the four required elements — build, tests, on-device `ui-test-worker` evidence, coherent seams — and the Wave Verification Report + confirmed PASS are mandatory; how the orchestrator runs and formats them is its call).

Order and dependencies are fixed by this document; everything else remains the orchestrator's call.
