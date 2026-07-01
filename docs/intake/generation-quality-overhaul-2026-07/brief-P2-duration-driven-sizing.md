# P2 — Duration-driven program sizing across the full 20–120 min range

**Type:** Bug (broken at both ends of the duration range) + refactor
**Cluster:** Generation prompt (same file as all briefs). This is the sizing substrate P1/P3/V1 sit on — see INDEX for order.
**Covers doc sections:** §3.1 (delete the broken time-budget constants; derive per request), §3.2 (single source of truth for the time estimate + realistic per-rep time + self-check), §3.5 (coverage scales with days × duration).

> **Outcome-only.** Describes the target behavior, not the implementation.

## Context
The generator hard-codes contradictory, duration-independent time constants left from an earlier template: a fixed "~19–20 working sets," a fixed "~46–47 min" day target, a "120 s rest maximum," and a per-rep work time of 3 s. These make a session effectively bounded at ~45–50 min regardless of what the user asked for. The v1.10.x fixes tuned this machinery specifically around ~50-min hypertrophy days; it was never made to scale.

The user requires the **full 20–120 minute range** to work. Today the fixed target is wrong at **both ends**: a 20-min session gets over-filled/padded, and a 120-min session is arithmetically impossible (rejected every regeneration).

Note (diagnose first): the app already has an **authoritative deterministic time check** (the same estimator shown on the Program screen) and has already stopped the model from evaluating time itself. So §3.2's "single source of truth" is largely in place — the remaining gap is that the **prompt's stated formula and the deterministic estimator can drift** (e.g. the 3 s/rep constant) and that the fixed minute/set targets fight the real duration input.

## What the user wants (end result)
Volume, rest, exercise count, and per-day time are **derived per request** from goal × experience × days/week × the **actual selected session duration** — with no fixed set count, no fixed minute target, and no blanket rest ceiling driving the size. The result must be correct across the whole 20–120 min range:
- **Short sessions (~20 min)** produce a lean plan that fits the target **without junk padding**.
- **Long sessions (~120 min)** actually **reach** the target without being capped out by a set-count or rest ceiling.
- **Coverage scales with days × duration:** on few/short days, prioritize multi-pattern compounds over exhaustive movement-pattern coverage (don't cram).
- The minute the generator sizes toward equals what the app's deterministic check computes (kept in lockstep), and the per-rep work time is **realistic** (controlled tempo ≈ 4–5 s, not 3 s) so estimates aren't biased low.

## Current (incorrect) vs correct behavior
- **Current:** Fixed ~19–20 sets / ~46–47 min / ≤120 s rest / 3 s-per-rep, independent of the chosen duration → over-fills short sessions and cannot reach long ones.
- **Correct:** All four quantities scale to the actual request; short = lean, long = genuinely full; one time formula, matched between prompt and the deterministic gate; realistic per-rep time.

## Diagnose first
- Identify which constants are genuinely load-bearing vs stale template leftovers before deleting — the ±10-min gate + salvage were hand-fit to ~50-min hypertrophy, so removing the constants has real regression risk at that point.
- Confirm the prompt formula vs the deterministic estimator: changing the per-rep constant shifts every computed day length (and the Program-screen duration display), which changes what passes the ±10 window — this must be a deliberate, coordinated change, not a silent one.

## Acceptance criteria — Done when…
- A **20-min** session generates a plan that estimates within its ±10 window with no filler/junk volume.
- A **120-min** session generates a plan that actually reaches ~120 min (±10) and is **not blocked** by a fixed set-count or a 120 s rest ceiling.
- Intermediate durations (e.g. 45, 60, 90 min) each land in their own ±10 window.
- No fixed "~46–47 min" or "~19–20 sets" target remains as a duration-independent instruction; volume/rest/count/time visibly change with the duration input.
- The minute value the generator targets matches the app's deterministic estimate (no drift); per-rep work time reflects controlled tempo, not 3 s.
- Holds for **all four goals** (see P3).

## Scope and constraints
- Keep the ±10-min window as the accepted tolerance (unless the user changes it).
- Do NOT reintroduce any fixed minute or fixed set-count target.
- Safety caps stay hard (hinge rep caps — §4/§5); only the *time-budget* set/rest/minute constants are derived, never the safety overrides.
- Per-session and per-muscle sanity limits still apply, but must themselves scale sensibly with duration rather than being fixed numbers that break long sessions.

## Decisions baked in (user-confirmed)
- Full 20–120 min range must be supported and correct at both ends.

## Assumptions applied (user may override)
- **[ASSUMPTION]** The ±10-min accepted tolerance is retained unchanged; only the *sizing* logic feeding it becomes duration-derived.

## Considerations for whoever builds it
- This unwinds tuning that currently keeps ~50-min hypertrophy days above the floor — the most likely regression point; verify that the previously-fixed under-fill behavior still holds after the constants are derived.
- §6 metadata (P5) makes the self-check trivial by letting the plan declare its own time components; sequence P2/P5 with that in mind.
- Changing the estimator affects the Program-screen duration display — keep them consistent.
