# R1 — Exercise-name → muscle-group recognition (pattern-level fix + tempo/interval false-cardio)

**Type:** Bug (with one embedded, already-decided product choice: leave pure balance/mobility moves un-grouped).
**Cluster:** Standalone — single unit, single worker, single mechanism. Do not split.
**Outcome-only:** This brief describes the desired end result and how to tell it is met. It does NOT prescribe the implementation (keyword reordering vs explicit rules vs a name→group table is the orchestrator's call).

## Context

The app derives an exercise's muscle group from its **name** (the user logs and swaps in exercises that are not in the seeded default list, so there is no structured muscle field to rely on). That name-based classification drives muscle attribution across **Stats, muscle recovery, Recap, per-muscle volume**, and — via the same Cardio determination — the **workout time estimate**. Muscle group is resolved and stored at **set-write time**.

The classification today matches keyword substrings in a fixed precedence order. That precedence and a few missing keywords cause two failure modes:
- **(a) Pattern misfires:** a name containing an earlier-checked keyword is captured by the wrong group — e.g. a *chest-supported row* is read as Chest (the word "chest"), a *rear-delt fly* / *reverse fly* is read as Chest (the word "fly"), a name containing "incline bench" is read as Chest even when it's a row or a rear-delt move.
- **(b) Fall-through to blank:** specialised names match nothing and are dropped from muscle stats — e.g. *Arnold Press*, *Tibialis Raise*, *Incline Walk*.
- **(c) False cardio:** a strength move whose name contains "tempo" or "interval" is read as Cardio (not exercised by the 24 listed names, but confirmed in the mechanism), which both mis-attributes it and inflates the estimated workout time.

Root cause is **confirmed** (verified in the classification path), so no separate "diagnose first" step is required — but the *fix approach* is deliberately left open for the orchestrator.

## What the user wants (end result)

Each of the 24 listed exercises (and the broader name *patterns* they represent) is recognised as the correct muscle group, so that everywhere muscle attribution appears it reflects the real target muscle. Specifically:

1. **Movement/target patterns classify correctly, not just these literal names.** Fixing the underlying patterns (so future similarly-named exercises also work) is in scope — the 24 names are concrete examples, not the whole spec.
2. **Pure balance / proprioception / mobility moves are recognised as non-muscle work** — un-grouped ("Training"), excluded from muscle volume and recovery, and displayed gracefully (never a broken or empty state). No new muscle category is introduced.
3. **Loaded lower-leg strengthening counts** — tibialis raises and single-leg calf raises are tracked under Legs.
4. **A strength exercise named with "tempo"/"interval" is classified by its movement, not Cardio** — and its estimated workout time is no longer inflated by being mistaken for a cardio duration.
5. **Nothing currently correct regresses** — see acceptance guards.

The agreed group for every listed name and the pattern guards are in the companion fixture: `fixture-expected-classification.md`.

## Current vs correct behaviour (the 9 that change today)

| Exercise | Today (wrong) | Correct |
|----------|---------------|---------|
| Incline Walk | blank | Cardio |
| Chest-Supported Dumbbell Row (Incline Bench) | Chest | Back |
| Dumbbell Seated Arnold Press | blank | Shoulders |
| Dumbbell Bent-Over Rear Delt Fly | Chest | Shoulders |
| DB Chest-Supported Face Pull Alt — Prone DB Rear Delt Fly | Chest | Shoulders |
| DB Face Pull Substitute — DB Prone Y-Raise (on Incline Bench) | Chest | Shoulders |
| Tibialis Raise (Seated, Heels on Floor, Toes Lift) | blank | Legs |
| Dumbbell Reverse Fly (Lying Face-Down on Flat Bench) | Chest | Shoulders |
| DB Chest-Supported Rear Delt Row (Wide Elbows) | Chest | Shoulders |

The other 15 listed names already behave correctly today (11 strength/cardio names, and 4 pure-balance names that are already un-grouped) and must stay that way.

## Acceptance criteria (Done when …)

- **Done when** every one of the 24 names in `fixture-expected-classification.md` is recognised as its agreed group across muscle attribution (Stats, muscle recovery, Recap, per-muscle volume) — newly logged sets for these names carry the agreed group.
- **Done when** the four pure balance/mobility moves (Hand-Supported Single-Leg Balance Hold, Standing Ankle Balance Hold, Standing Single-Leg Ankle Balance Hold, Ankle Alphabet / Foot Circles) are treated as non-muscle work: excluded from muscle volume and recovery, and shown gracefully (neutral "Training"-style treatment, no broken/empty state).
- **Done when** Tibialis Raise and both single-leg calf raises are tracked under Legs.
- **Done when** a strength exercise whose name contains "tempo" or "interval" (e.g. "Tempo Squat", "Interval Lunge", "Tempo Bench Press") is classified by its movement, NOT Cardio, and its estimated workout time is not inflated as if it were a cardio duration.
- **Done when** the pattern-level guards hold (no regressions):
  - plain "fly" / "chest fly" / "incline fly" / "cable fly" still → **Chest**; only "rear delt" / "reverse fly" / "rear-delt fly" / "bent-over fly" → **Shoulders**.
  - a "…-supported row" reads as a **Back** row, while genuine chest presses ("Bench Press", "Incline Bench Press", "Squeeze Press") stay **Chest** (the row fix must not pull presses to Back).
  - genuine cardio (walk / run / jog / bike / treadmill / high knees / jump rope) stays **Cardio**.
  - the 9 already-correct strength/cardio names and the 4 already-un-grouped balance names are unchanged.
- **Done when** the muscle attribution shown for a given exercise is **consistent across every surface** that displays it (badge label, stored group, recovery view, per-muscle volume) — no surface disagrees with another for the same name.

## Scope and constraints

**In scope**
- The name-based muscle-group recognition mechanism and the patterns above.
- The tempo/interval false-cardio correction (and the resulting time-estimate correctness).

**Out of scope**
- Adding any new muscle category / colour / recovery sub-group.
- Changing the AI program-generation prompt or its category vocabulary (avoid touching the AI-generation seam).
- Re-deciding any of the 11 already-correct classifications.

**Constraints**
- Build with `./build.sh` (not `./gradlew`).
- No commits or releases unless explicitly asked.
- No on-device / automated UI tests unless explicitly asked.

## Decisions baked in (settled — coordinator best-judgment under user delegation)

- Rear-delt **row** ("Chest-Supported Rear Delt Row, Wide Elbows") → **Shoulders** (target-muscle naming), while a plain chest-supported **row** → **Back**.
- Pure balance/mobility moves → **un-grouped**, excluded from volume/recovery; **no new category**.
- Tibialis raise and single-leg calf raises → **Legs**.
- Incline Walk → **Cardio**.

## Decisions deferred (flag for whoever builds it)

- **Historical backfill.** Because muscle group is stored on each set at write time, this fix corrects only **newly logged** sets unless a backfill is performed. Whether to also re-derive the group for already-logged sets (so past Stats/Recap/volume read correctly for these names) is **undecided** and was not part of the settled Q-A–Q-D decisions. Confirm the intended behaviour before assuming forward-only.

## Considerations for whoever builds it (surfaced, not decided)

- Muscle attribution flows through more than one path (the broad group used for storage/stats/recap/volume, the fine-grained taxonomy used by the recovery view, and the badge/display label). For the "consistent across every surface" criterion to hold, all of these must agree on the listed names and patterns — verify the recovery view and badges, not just stored stats.
- The Cardio determination is shared with the time estimator, so the tempo/interval correction should be validated to fix both the muscle attribution and the time estimate together.
