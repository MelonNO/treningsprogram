# C4 — Muscle-group recovery / freshness view

**Type:** New feature
**Cluster:** P3 (parallel analytics) — independent worker for the **view**; the **AI-nudge** piece is sequenced on the `AiRepository` track (see "Two pieces" below).
**Outcome-only:** Describes the end result and user experience, not the implementation. The "how" is the orchestrator's.

---

## Context

Every `WorkoutSet` carries its `muscleGroup`, and sessions carry `dateMs` (newer sets also carry `loggedAtMs`). The app therefore already knows when each muscle group was last trained — it just never shows it. Users have no at-a-glance sense of what is recovered and ready versus what was hammered yesterday. This feature adds a **freshness view** with **fixed recovery-window coloring** (the model the user chose).

## What the user wants (end result)

The user can see, at a glance, the recovery state of each major muscle group — colored as **recovering**, **ready/fresh**, or **overdue** — based on how long ago that muscle was last trained. This helps the user decide what to train today, and the recovery state also informs the AI when it plans.

## Recovery model — scientifically grounded (confirmed decision)

The user chose **fixed recovery-window coloring** and asked that the windows be **grounded in the muscle-recovery / protein-synthesis literature and cited**, not arbitrary numbers. Basis:

- **Muscle protein synthesis (MPS)** is elevated after a resistance-training bout and returns toward baseline over roughly **24–72 hours**, with the elevation larger and longer in **less-trained** individuals and shorter in well-trained ones. *(e.g. MacDougall et al. 1995, "The time course for elevated muscle protein synthesis following heavy resistance exercise," Can J Appl Physiol — MPS elevated at 24 h, declining by ~36 h, near baseline by ~48–72 h; Damas et al. 2015 review on MPS time-course and the repeated-bout effect.)*
- **Training-frequency / recovery guidance:** roughly **48 hours** between training the same muscle group is the conventional minimum for recovery, which underpins the common "train a muscle ~2×/week" recommendation the app's own AI already uses. *(ACSM resistance-training guidance; Schoenfeld, Ogborn & Krieger 2016 meta-analysis on training frequency.)*
- **Larger vs. smaller muscles / heavier vs. lighter sessions** can recover at different rates; the literature supports differentiating where warranted rather than a single flat number. The implementer should pick **defensible bands** (e.g. broadly: recently trained → still recovering for ~the first ~24–48 h; recovered/ready around ~48–72 h; "overdue" when substantially longer than a normal training cadence) and **document the basis** so it is reviewable, rather than inventing numbers.

> Outcome requirement: the recovery bands must be **principled and documented** (grounded in MPS time-course / training-frequency literature, with larger-vs-smaller-muscle differentiation where warranted), surfaced as three states: **recovering / ready / overdue**.

## Acceptance criteria ("Done when …")

- **Done when** the user can see each major muscle group's recovery state, colored as **recovering**, **ready/fresh**, or **overdue**, based on time since that muscle was last trained.
- **Done when** the recovery bands are grounded in cited recovery/MPS literature (not arbitrary), and the basis is documented.
- **Done when** the recovery state also **informs the AI** when it generates the program (e.g. the freshest muscles can be favored for the next session).
- **Done when** a muscle never trained, or a fresh install with no history, shows a sensible state rather than an error.

## Two pieces (important for sequencing)

C4 has two parts that touch different seams, and the sequence plan treats them separately:
- **(a) The recovery VIEW** — the per-muscle freshness panel. **Local computation, no shared seam, parallel-safe.** This is the Wave-1 part. **Placed on Home** (locked — see assumption note) to avoid touching the Program tab.
- **(b) The AI NUDGE** — feeding recovery state into the generation prompt (assumption J). **This edits `AiRepository`**, which is the serialized seam shared with B2/B3/E2. So part (b) must **not** run in Wave 1 concurrently with B3's `AiRepository` edit; it is sequenced onto the `AiRepository` track (delivered with the AI-coaching work or with E2's generation work, whichever owns that seam at the time). If the user overrides assumption J to **inform-only**, part (b) disappears entirely and C4 is purely the parallel-safe view.

## Scope and constraints

- **In scope:** a per-muscle-group freshness view with three colored states from cited recovery windows; feeding recovery state into AI planning (the latter sequenced per "Two pieces" above).
- **Out of scope (unless the user later asks):** individualized recovery models from sleep/HRV/wearables (the Garmin idea was dropped); fatigue tracking beyond "time since last trained."
- **Local computation** for the view; feeding state into the prompt is an `AiRepository` touch.
- **Cross-cutting constraints:** build via `./build.sh`; no git commits/releases unless the user asks; **on-device UI verification IS required for this batch** per the per-wave gate in `SEQUENCE.md`; mindful of `PreferencesManager` churn.

## Decisions baked in

- **Fixed recovery-window coloring** (three states), windows **grounded in cited science** — user-confirmed.

## Assumptions (user may override)

- **ASSUMPTION (question J):** the recovery view **both informs the user and nudges the AI** on what to train. *(User may choose inform-only — which removes the `AiRepository` touch, part (b) above.)*
- **ASSUMPTION (placement — LOCKED to Home):** the freshness panel is placed on **Home**, not the Program tab. This is locked (not left open) so C4's view stays parallel-safe in Wave 1 and never collides with the P2 program-control work on the Program tab. *(User may override to put it on the Program tab — if so, C4's view moves out of Wave 1 to after Wave 3, since it would then touch `ProgramFragment`.)*

## Considerations for whoever builds it (surfaced, not decided)

- **Placement is locked to Home** (see assumption above) precisely to keep the view collision-free in Wave 1. Only if the user overrides to the Program tab does the P2↔C4 seam reappear (and the view reschedules after Wave 3).
- **The AI-nudge (part b) is the only `AiRepository` touch** and is sequenced on that serialized seam, not run in parallel in Wave 1. See "Two pieces" above and `SEQUENCE.md`.
