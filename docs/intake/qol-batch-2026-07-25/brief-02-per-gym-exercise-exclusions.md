# Brief 02 — Per-gym "exercises to avoid" + exclude Chest-Supported Dumbbell Row at the Home Gym

Type: Feature (user-accepted improvement) delivering a concrete exclusion
Cluster: standalone, but shares `AiRepository.kt` with Cluster B (05+06) — serialize against it or same worker

> Outcome-only: this brief describes the end result and user experience. The implementation approach belongs to the orchestrator/worker.

## Context

Gyms are modeled as presets (equipment list + free-text limitation notes + plate profile), one selected globally; both equipment and notes already reach the AI generation prompt. There is **no per-gym exercise exclusion** today — the only levers are a single *global* "disliked exercises" free-text and manual post-generation deletion. "Chest-Supported Dumbbell Row" is not a catalog entry the user can remove; it enters plans purely as free text emitted by AI generation. The user's home bench is not compatible with this exercise (prone-on-bench, bilateral dumbbell row).

## What the user wants (end result)

1. Each gym's settings gain an **"exercises to avoid at this gym"** list the user can edit themselves (alongside the existing equipment list and notes). When that gym is the one a generation is for, listed exercises never end up in the plan.
2. Concretely and immediately: with the **Home Gym** selected, generated/regenerated plans **never contain Chest-Supported Dumbbell Row** (or obvious name variants of that same movement, e.g. "Dumbbell Chest-Supported Row", "Chest Supported Row", "Dumbbell Incline Bench Chest Supported Row" / incline-row phrasings of the prone dumbbell row).
3. On the user's **existing install**, the Home Gym preset ends up with this exclusion in place without the user having to type it in.
4. Other gyms (Commercial, Hotel, any custom) are unaffected — the exercise remains available there.

## Acceptance criteria

- Done when a user can add and remove per-gym avoid entries in the gym preset editor, and they persist.
- Done when NO generation path for a gym with exclusions produces an excluded exercise in the saved plan: weekly generation (auto and manual), single-day swap/regenerate (including when the swap dialog's equipment-preset picker selects a gym — the exclusion follows whichever gym that generation targets), and week rebalance.
- Done when the seeded Home Gym preset carries the Chest-Supported Dumbbell Row exclusion on both fresh installs and existing installs after update.
- Done when generations for gyms without exclusions are behaviorally unchanged.
- Done when the new field survives backup → restore.

## Scope and constraints

- Scope is **just this movement** for the pre-filled exclusion — the user explicitly declined blanket-blocking other prone-on-bench exercises (chest-supported curls, seal rows stay allowed unless the user adds them).
- Already-saved plans are not retroactively edited; the guarantee applies to generations from now on.

## Decisions baked in

- **Per-gym exclusion field** chosen over a one-off hard-code (user: "go for improvement").
- **Strictness — METHOD-DELEGATED decision (user said "you choose"), veto-able:** hard guarantee. Being firmly instructed is not enough; if the AI includes an excluded exercise anyway, the saved plan still must not contain it. The observable contract: an excluded exercise never appears in a plan saved for that gym.

## Assumptions (user may override)

- Name matching is case/punctuation-insensitive and covers the obvious phrasing variants of this movement, not just one exact string.

## Considerations for whoever builds it (surfaced, not decided)

- Existing installs: the seeded preset may have been renamed/edited — pre-fill where the seeded Home Gym preset is identifiable; if genuinely ambiguous, surface that rather than guessing (flag in the worker report).
- How the hard guarantee is met when the AI disobeys (regenerate, substitute, strip) is the builder's call — but the result the user sees must still be a sensible, complete plan, and generation must never hang or loop unboundedly because of it (project precedent: never-loop).
- The exclusion list should read as a hard "never include" in the prompt — distinct from the existing anti-churn "recently used" list, which is advisory.
- Standing constraints: build via `./build.sh`; no commits/releases unless asked; no on-device UI tests; frugal live-API verification.
