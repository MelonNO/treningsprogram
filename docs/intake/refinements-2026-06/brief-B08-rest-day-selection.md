# B08 — Choose specific rest day(s) when generating (two modes)

**Type:** Feature
**Cluster:** AI generation seam (with B09, B10) — coordinate; sequence after/with B10
**Outcome-only:** describes the desired end result, not the implementation.

## Context
Today the user specifies training as a **number of days per week**; the AI decides which weekdays are training vs rest. The user wants the option to pin **specific** rest days, while still being able to fall back to the old count-only behavior.

## What the user wants (end result)
Two modes, switched by a **"you choose days" checkbox**:

- **Default mode (checkbox OFF):** the user selects the **specific weekdays that are rest days**. The AI plans training only on the remaining weekdays. The "days per week" number is **derived** from the selection (e.g. 2 rest days selected ⇒ 5 training days) rather than entered separately.
- **Alternative mode (checkbox ON — "you choose days"):** the original behavior — the user just picks the **number** of training days, and the AI chooses which days are rest.

This setting is available in **both** the initial setup flow and in Settings (so it persists and applies to future generations).

## Acceptance criteria
- Done when, with "you choose days" OFF (default), the user can select specific rest weekdays and the generated program schedules training only on the non-rest days.
- Done when the training-days-per-week count is derived from the rest-day selection in default mode (no contradictory separate number to maintain).
- Done when, with "you choose days" ON, the user picks a number of training days and the AI assigns which days are rest (the prior behavior).
- Done when the choice (mode + selected rest days or count) is configurable in both setup and Settings and persists for future generations.
- Done when a freshly generated program in default mode actually respects the chosen rest days (no training scheduled on a rest day).

## Scope and constraints
- In scope: the rest-day-vs-count input, the toggle between the two modes, persistence in setup + Settings, and feeding the chosen rest days into program generation.
- Out of scope: changing the exercise-selection logic beyond honoring which days are training vs rest.
- Hard constraint: in default mode the generator must not schedule training on a selected rest day.

## Decisions baked in
- Two modes via a "you choose days" checkbox; default is specific-rest-day selection; alternative is count-only.
- Days/week is derived from the rest-day selection in default mode.
- Configurable in both setup and Settings.

## Considerations for whoever builds it
- This shares the generation prompt and flow with B09 and B10 — coordinate so the rest-day input and the mid-week regenerate logic compose cleanly, and so the hardened response handling from B10 is in place.
- Consider how an existing user's current "days per week" setting maps onto the new default mode (e.g. what rest days are pre-selected) — surface this as a migration decision rather than guessing silently.
- Standard cross-cutting constraints apply (build via `./build.sh`; no commits/releases or UI tests unless asked).
