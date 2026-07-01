# P4 — Injury handling: empty input = zero modification; severity-scaled

**Type:** Refinement / behavior bug (invented prehab on empty input)
**Cluster:** Generation prompt (same file as all briefs) + the validator's injury check (V1). Localized change.
**Covers doc sections:** §3.6 (empty injury = no-op; MILD softened).

> **Outcome-only.** Describes the target behavior, not the implementation.

## Context
Injury free-text + severity are inputs to both the generator and the validator's injury gate. Treating injury as a "required input" risks the model **inventing prehab/rehab when there is no injury**, and the current MILD tier can over-add rehab every session.

## What the user wants (end result)
- **No injury text → no injury-driven changes at all:** no rehab/prehab added "just in case," no exercise-selection changes attributable to injury.
- **MILD → light touch:** a note plus **at most one optional prehab slot per week** (not a rehab slot every session); **no selection change** unless the free-text names a clear aggravator.
- **MODERATE / SEVERE → unchanged:** mandatory per-session selection changes as today (substitute or exclude the aggravating category; the physically-incoherent-cue guard stays).

## Current (incorrect) vs correct behavior
- **Current:** injury treated as required input; empty/MILD can lead to invented or per-session rehab.
- **Correct:** empty = genuine no-op; MILD = note + ≤1 optional prehab/week + no forced selection change absent a named aggravator; MODERATE/SEVERE mandatory as today.

## Acceptance criteria — Done when…
- With **no injury entered**, generated plans contain **no rehab/prehab slots** added defensively and **no selection changes** attributable to injury.
- With a **MILD** injury, the plan adds **at most one optional prehab item for the week** plus a note, and does **not** force a per-session selection swap unless the text names a clear aggravator.
- With **MODERATE/SEVERE**, the existing mandatory substitution/exclusion behavior is preserved, and no single-leg movement carries physically-incoherent "both feet down" cues.

## Scope and constraints
- Must stay consistent with the validator's injury-quality rule (V1) so the two don't disagree (e.g. the validator must not demand rehab when there is no injury).
- Keep §2 sustain: injury free-text + severity remain inputs; selection (not just notes) still changes at MODERATE/SEVERE.

## Decisions baked in (user-confirmed)
- In scope per "everything in both documents."

## Assumptions applied (user may override)
- **[ASSUMPTION]** MILD = at most one optional prehab slot per *week* (per §3.6), not per session.

## Considerations for whoever builds it
- The validator's injury gate (V1) and this generator behavior must be changed together so an empty-injury plan is never rejected for "missing rehab," and a MILD plan isn't rejected for not modifying selection.
