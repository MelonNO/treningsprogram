# P5 — Auditable per-exercise / per-day / per-week metadata

**Type:** Feature (new output contract) — supports P2's self-check and V1's declared-number checks
**Cluster:** Generation prompt output schema + the plan parse model + validator input (same file as all briefs). Do after content settles so the schema is stable — see INDEX.
**Covers doc sections:** §6 (required output metadata), supports §3.2 (single source of truth / auditable estimate).

> **Outcome-only.** Describes the target behavior, not the implementation.

## Context
Today the plan the model returns carries only a handful of fields per exercise (name, sets, target reps, target weight, notes, rest). The validator therefore has to re-derive volume and duration from **exercise names** with a fragile name-parser. §6 asks the plan to **declare its own numbers** so the validator can sum directly and the time self-check (P2/§3.2) becomes trivial.

**Known risk (must be weighed):** this app has repeatedly hit **output truncation / timeout** when responses grew large. Adding metadata increases output size. The user put "everything in both documents" in scope, so this is included — but the size/latency cost is real and must not break generation.

## What the user wants (end result)
The generated plan declares, per §6:
- **Per exercise:** role, movement pattern, primary/secondary muscles, counts-as-hard-sets, work seconds, rest seconds, setup seconds, estimated minutes, injury modification.
- **Per day:** day estimate minutes, within-duration-window (bool).
- **Per week:** weekly volume summary (sets/muscle), movement-pattern summary, duration summary, **block state (continue/new)**, constraint notes.

So the validator and the deterministic estimator can read declared numbers instead of parsing names, and P2's single-source-of-truth self-check is direct.

## Acceptance criteria — Done when…
- The generated plan carries the §6 metadata fields, and the app parses them without failing on the richer shape.
- The validator / estimator can consume the **declared** numbers (no reliance on name-parsing for volume/time where a declared value exists).
- **Generation does not regress into truncation or timeout** as a result. If full metadata would cause truncation/timeout, the metadata breadth is reduced (see assumption) rather than breaking generation.

## Scope and constraints
- The output must still be valid, complete JSON that parses — richer metadata must not push responses past the point where they truncate.
- `blockState` here is the same continue/new signal P1/§3.4 relies on — keep them consistent (one source of truth for block state).

## Decisions baked in (user-confirmed)
- In scope per "everything in both documents."

## Assumptions applied (user may override)
- **[ASSUMPTION — flag to user if hit]** If declaring the *full* §6 metadata set causes truncation/timeout, prefer a **minimal subset** (the time components — work/rest/setup/estimated minutes — plus day estimate + within-window + block state) over breaking generation, and surface the trade-off. The user asked for everything, so this reduction is a fallback, not a default.

## Considerations for whoever builds it
- Sequence after P1–P3 so the schema is stable (block state, roles, muscles all settle first).
- This is the item most likely to force an escalation (output-budget vs completeness) — measure real output size against the model's limits before committing to the full set.
