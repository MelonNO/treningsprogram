# C1 — Eyebrow section headers: one treatment across all screens

**Type:** Refinement (consistency)
**Cluster:** C (cross-cutting sweep) — run in Wave 2, after per-screen items land
**Outcome-only.**

## Context
The Auros "eyebrow" (small uppercase section label) is rendered two different ways for the same conceptual element:

- **Pattern A — beacon dot + `TextAppearance.Auros.Eyebrow`:** a 6dp `bg_eyebrow_dot` View followed by the label. Used in ~9 layouts — every tab's hero band, plus **Home's in-card section headers** (Weekly Challenges, Today's Plan, Body Weight, Muscle Recovery, Recent Workouts), the Wrapped hero, the celebration hero, and the log warm-up-ramp card.
- **Pattern B — `style="@style/Widget.Auros.Eyebrow"`, no dot:** the styled label alone. Used in ~23 layouts — **Profile** (PRs, Goals, Achievements), the **log logged-sets** header, and essentially every Settings / Stats / Progress / Recap-trends / setup screen.

The inconsistency is visible *within* single screens: Profile's hero band has a dot but its PRs/Goals/Achievements headers don't; the log warm-up card has a dot but the log logged-sets header doesn't. Same design element, two looks.

## What the user wants (end result)
Section eyebrows look identical everywhere. A user scanning any screen sees one consistent treatment for "this is a section label", so the app reads as one designed system rather than several fast batches stitched together.

## Acceptance criteria (Done when …)
- Every **in-card section eyebrow** across the app uses one identical treatment (per A-C1a: the dot-less `Widget.Auros.Eyebrow`).
- The **hero band** at the top of each tab keeps its beacon dot (that's the deliberate tab-title beacon, not an in-card label) — it stays as the one place the dot appears.
- No section header changes its text, size, colour role, or casing — only the dot's presence/absence is normalised.
- After the change, Home's in-card eyebrows match Profile's / Settings' in-card eyebrows exactly.
- Build passes; no layout regressions (spacing stays balanced where a dot is removed).

## Scope and constraints
- **In scope:** normalising the dot-vs-no-dot inconsistency on section eyebrows across all layouts.
- **Out of scope:** the eyebrow font/size/colour tokens themselves; hero-band beacons; celebration/Wrapped decorative emoji headers (those `🏆/💥/🎯` headers are gamification, stay vivid).
- Touches many layout files — run **after** Clusters A (Home) and B (Log) merge so it standardises the already-refactored files.

## Decisions baked in
- One in-card eyebrow treatment app-wide; hero bands remain the sole dot bearer.

## Assumptions (user may override)
- **A-C1a** — Standardise in-card eyebrows to the **dot-less** `Widget.Auros.Eyebrow` (the ~23-layout majority), removing the dots currently on Home's in-card headers. If the user instead wants **dots everywhere**, flip the direction — but the majority/lower-churn choice is dot-less.

## Considerations for whoever builds it
- When a dot is removed from a Home card header, re-check the header's bottom margin so the section spacing still matches the dot-less cards.
- This is a mechanical sweep; a grep for `bg_eyebrow_dot` (in-card usages) and `TextAppearance.Auros.Eyebrow` (inline) finds the Pattern-A sites; hero bands are the ones to leave alone.
- File-collision hazard with H1–H4 (`fragment_home.xml`), L1/C2 (`fragment_log_workout.xml`), W1 (`dialog_wrapped.xml`) — hence Wave 2.
