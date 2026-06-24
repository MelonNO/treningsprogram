# B3 — Plateau / stall detection

**Type:** New feature
**Cluster:** P1 (AI coaching) — one worker with B1 and B2.
**Outcome-only:** Describes the end result and user experience, not the implementation. The "how" is the orchestrator's.

---

## Context

The app stores per-exercise strength history (`WorkoutSetDao.getStrengthHistory` returns `(dateMs, maxWeight, bestReps)` per session) and weekly volume (`getWeeklyVolume`). The data needed to detect a stalled lift already exists; it is simply never analysed or surfaced. Because the app's own AI prescribes **double progression** (reps climb within a range before load increases), a naive "weight hasn't gone up" rule would mislabel normal progress as a plateau.

This feature detects genuine stalls and tells the user, with a concrete suggestion, and feeds the signal back into the adaptive engine.

## What the user wants (end result)

When a lift has genuinely stopped progressing, the user is alerted — e.g. "Bench has stalled for several sessions" — with a concrete, actionable suggestion (deload, rep-scheme change, or variation), and the AI takes the stall into account when it next generates the program.

## Stall definition — scientifically grounded (confirmed decision)

The user asked that the stall/progression criteria be **grounded in exercise science and cited in the brief**, not arbitrary numbers. Basis:

- **Progressive overload** is the governing principle: strength/hypertrophy adaptation requires a progressive increase in mechanical demand over time (load, reps, or total work). Absence of any such increase over repeated exposures is the definition of a stall. *(Foundational principle in strength-training literature; e.g. the ACSM progression-models position stand, Kraemer & Ratamess 2004, "Progression Models in Resistance Training for Healthy Adults," Med Sci Sports Exerc.)*
- Because the app uses **double progression**, "no improvement" must be judged on **both reps and load together** — operationally, on a single measure that captures both, such as **estimated 1RM** (load × reps via an established equation) and/or **volume-load** (sets × reps × load). A lift is stalled when neither estimated-1RM nor volume-load for that exercise has improved across repeated recent sessions of it.
- **Defined period:** the literature does not give a universal session count, but stalls are conventionally judged over a small number of consecutive exposures (commonly ~3) before intervening, so as not to react to a single off day. The exact window and the estimated-1RM equation should be stated in the implementation and surfaced so the user can see the basis. *(The intent recorded here is the principle and the use of estimated-1RM / volume-load over a defined multi-session window — not a hard-coded magic number; the implementer should pick a defensible, documented window.)*

> Outcome requirement: the detection rule must be **principled and documented** (estimated-1RM / volume-load over a defined multi-session window, accounting for double progression), not a bare "weight didn't increase" check.

## Acceptance criteria ("Done when …")

- **Done when** the app identifies an exercise as stalled only when it has shown **no improvement in estimated 1RM or volume-load across a defined number of consecutive recent sessions** of that exercise — i.e. it does **not** false-positive while reps are still climbing under double progression.
- **Done when** a detected stall is shown to the user with the exercise named and a concrete suggestion (e.g. deload, rep-scheme change, or variation).
- **Done when** a detected stall is **also fed into the next program-generation prompt** so the AI actively addresses it.
- **Done when** the documented basis for the stall rule (estimated-1RM / volume-load over a defined window) is recorded, so the criterion is defensible and reviewable.
- **Done when** exercises with too little history to judge are simply not flagged (no false alarms on new lifts), and warm-up sets are excluded per existing convention.

## Scope and constraints

- **In scope:** principled stall detection over existing strength data; a user-facing alert with a suggestion; feeding the stall into the generation prompt.
- **Out of scope (unless the user later asks):** auto-applying a deload without user awareness; a full periodization engine (that is E2's territory). Note: **E2's deload is triggered by B3's detection (decision M2)** — B3 is a confirmed prerequisite for E2's deload piece (see `SEQUENCE.md` dep #1), not a "maybe later relate."
- **Detection itself is local (no API).** The suggestion may be rule-based or AI-phrased; using the model here is acceptable per the user's "API calls are fine" decision.
- **Cross-cutting constraints:** build via `./build.sh`; no git commits/releases unless the user asks; **on-device UI verification IS required for this batch** per the per-wave gate in `SEQUENCE.md` (applies to B3's user-facing alert; pure detection logic is JVM-tested); mindful of `PreferencesManager` churn.

## Decisions baked in

- **Stall criterion grounded in cited exercise science** (progressive overload; estimated-1RM / volume-load over a defined multi-session window; double-progression-aware) — user-confirmed.

## Assumptions (user may override)

- **ASSUMPTION (question F):** a detected stall is **both** surfaced on screen **and** fed into the next generation prompt. *(User can choose "inform only" or "feed-AI only.")* **Sequence impact:** the "feed into prompt" half is what makes B3 touch `AiRepository`. If the user overrides F to **inform-only**, B3 no longer edits `AiRepository` and that seam concern disappears for B3 — but B3 **remains the prerequisite for E2's deload (M2)** regardless, so its Wave-1 position and the B3→E2 ordering edge are unchanged.
- **ASSUMPTION (placement):** where the alert surfaces (Recap & Trends, a Home alert, or elsewhere) is left to the orchestrator, consistent with B1's placement decision.

## Considerations for whoever builds it (surfaced, not decided)

- The estimated-1RM equation used here should be **consistent with C1** (same formula across the app — C1 assumes Epley) to avoid two different "1RM" numbers in the UI.
- Feeding stalls into the prompt is a `AiRepository` edit shared with B2 — same worker per the INDEX; coordinate with E2 on that file.
