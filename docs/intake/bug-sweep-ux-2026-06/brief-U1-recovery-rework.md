# U1 — Recovery view rework (secondary muscles, finer taxonomy, recovering-only display, tap→last session)

**Type:** Feature change (concrete user-tested items #1–#4)
**Phase:** 1 (feature-level recovery rework — see placement note)
**Cluster:** SW-C (analytics & display) — see INDEX.
**Track:** Concrete user-tested items.
**Outcome-only:** Describes desired behavior + acceptance criteria, not implementation.

> Source: real-device testing. Bundles the four recovery-view items the user gave (#1 secondary muscles, #2 hide rested/ready, #3 finer taxonomy, #4 tap→last session) because they all rework the same C4 surface and recovery model — splitting them across briefs would mean multiple workers editing the same model/screen blind.

---

## Context

The v1.8.0 recovery view (C4) lives primarily as a panel on **Home** (`MuscleRecovery` domain model + the Home recovery panel). Today it:
- tracks **7 broad muscle groups** (Chest, Back, Legs, Shoulders, Arms, Core, Glutes);
- computes recovery purely from **time since the group was last trained with working sets** — full credit to the group, **no secondary-muscle attribution and no per-exercise weighting**;
- shows recovery state for groups including recovered/ready/untrained ones.

The user wants this reworked into a more accurate, more focused recovery display.

## Desired behavior (end result)

1. **(#1 secondary muscles, weighted per exercise)** Recovery is computed from each exercise's effect on the muscles it works — **primary and secondary** — where **different exercises contribute different amounts to different muscles** (e.g. a press fully taxes the primary mover but only partially taxes the assisting muscles). A muscle's recovery reflects the **weighted** load it actually received, not full credit to every involved muscle.
2. **(#3 finer taxonomy)** Muscle groups are shown in **more detail** than the current 7 broad groups — a finer breakdown such as **upper/lower back, front/side/rear delts, biceps and triceps split out of "Arms", and quads/hamstrings/glutes/calves split out of "Legs"** (the exact final subdivision is for the builder to land sensibly; the user approved this direction).
3. **(#2 recovering-only display)** The recovery screen shows **only muscles that are currently recovering** — rested/ready and never-trained muscles are **hidden**. For each shown (recovering) muscle, the display indicates **how much it still needs to recover** (remaining recovery / progress toward ready).
4. **(#4 tap → last session)** Tapping a recovering muscle **navigates to the last session in which that muscle was used** (that session's detail/recap). It does **not** start or generate a new workout.

## Acceptance criteria ("Done when …")

- **Done when** a logged exercise updates the recovery of **all the muscles it works, primary and secondary, in weighted proportion** — different exercises visibly affect different muscles to different degrees.
- **Done when** the recovery view presents the **finer muscle subdivisions** (approved direction above) rather than only the 7 broad groups.
- **Done when** the recovery view **shows only currently-recovering muscles** and **hides rested/ready/untrained** ones, and each shown muscle communicates **how much recovery remains**.
- **Done when** tapping a recovering muscle opens the **most recent session that trained that muscle** (session detail/recap), with a sensible state if somehow none exists.
- **Done when** the reworked view behaves correctly for first-run/empty data (e.g. nothing recovering → a sensible empty state, not a broken panel).

## Scope & constraints

- **In scope:** the per-exercise weighted primary+secondary muscle model, the finer taxonomy, the recovering-only filtered display with remaining-recovery indication, and the tap→last-session navigation.
- **Out of scope (unless the user later asks):** changing the underlying recovery-window science/bands (the cited 48h/7-day model stays as the basis); a full standalone recovery screen if the panel placement suffices (placement is the orchestrator's call, consistent with C4 being on Home).
- **Decisions deferred to the builder (flagged):** the exact final muscle subdivision list and the exact per-exercise primary/secondary weighting values — the user approved the *direction* and the *weighted-per-exercise principle*; the specific taxonomy and weights are for the builder to land defensibly (ideally grounded like the existing cited recovery bands).
- **Coordination:** this **supersedes the recovery portions** of the diagnose-first sweep **S5** (and the Home recovery panel touched by **S1**) — those sweeps should verify the *reworked* behavior, not the old behavior. If the weighted model feeds the AI prompt (the prior C4 "nudge" idea), that rides the `AiRepository` seam (coordinate with S3/F3).
- **Cross-cutting:** build via `./build.sh`; no commits/releases unless asked; on-device verification required (SEQUENCE.md).

## Placement note

Placed in **Phase 1** because #1–#4 are **substantial feature/model work** (a new weighted muscle model + taxonomy), not cosmetic polish — better to land the reworked model before Phase-2 polish touches the same surface, and so S1/S5 verify the new behavior. The *visual polish* of the reworked view can still be refined in Phase 2 if needed.
