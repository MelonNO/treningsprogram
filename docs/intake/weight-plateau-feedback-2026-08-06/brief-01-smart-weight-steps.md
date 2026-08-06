# Brief 01 — Weight +/− buttons step to the next sensible, achievable weight

**Type:** Improvement (behaviour change to an existing control)
**Cluster:** A (with brief 02 — same buttons, same screen, same file)
**Source:** User item 1, plus accepted improvement A

> **Outcome-only.** This brief describes the end result and user experience. It does not prescribe
> how to build it. Design and implementation are the orchestrator's and its workers' to decide.

---

## Context

On the workout logging screen there is a weight field with a **−** and a **+** button beside it.
Today both buttons apply a **fixed 2.5 kg** change to whatever is in the field, for every exercise,
at every gym, regardless of equipment.

That single fixed number is wrong at both ends of the range:

- On a **light isolation lift** (lateral raise at 8 kg) a 2.5 kg jump is a huge relative increase.
- On a **heavy compound** the user does not want the opposite over-correction either — they
  explicitly do **not** want the button offering 0.5 kg on a bench press just because 0.5 kg plates
  happen to exist at that gym.

The app already knows a great deal about what is loadable: each gym preset records its **bar
weight**, its **dumbbell handle weight**, its **available plate sizes** (stored per pair), and
whether its **dumbbells are plate-loaded or fixed**. There is already plate-decomposition logic
driving the "X kg + Y kg per side" readout under the weight field. None of that currently
influences the +/− buttons.

## What the user wants (end result)

Pressing **+** moves the weight to the **next weight that is both sensible for the exercise in
front of them and actually achievable at the gym they are training at**. Pressing **−** does the
same in the other direction.

The step is **decided by the app**. The user explicitly does not want to configure it per exercise,
and explicitly does not want to be asked to enter their gyms' dumbbell racks or machine stacks.

**How "sensible" is defined (accepted improvement A):** the step **scales with how heavy the lift
is**, rather than coming from a hand-maintained list of exercise names. A 100 kg squat gets a large
step; an 8 kg lateral raise gets a small one. The user chose this specifically because the app's
programs contain **AI-generated exercise names that no fixed list would ever cover**.

**How "achievable" is defined:** it depends on what the app can actually know.

| Equipment | Guarantee |
|---|---|
| Barbell, and plate-loaded dumbbells | **Hard guarantee.** Every weight the buttons can produce is a weight that can genuinely be loaded with that gym's bar/handle and plate inventory. |
| Fixed dumbbells, machines, cables, anything else | **No guarantee possible** — the app has no inventory for these. A sensible default step is used instead (roughly 2 kg for dumbbells, 2.5 kg for machines). |

This split was put to the user directly as a trade-off against adding gym setup screens, and they
confirmed it.

## Acceptance criteria

- **Done when** pressing + or − on a heavy compound lift never produces a sub-kilogram change,
  even at a gym whose plate inventory includes 0.5 kg plates.
- **Done when** pressing + or − on a light isolation lift (e.g. an 8 kg lateral raise) produces a
  small change appropriate to that load, not a 2.5 kg jump.
- **Done when**, for a barbell or plate-loaded-dumbbell exercise at the active gym, **every** weight
  reachable by pressing + or − is a weight that can actually be loaded with that gym's bar/handle
  and plate pairs.
- **Done when** pressing + from a weight that is *not* loadable (e.g. the user typed 61 kg by hand)
  lands on the **next loadable weight above it**, rather than adding the step on top of 61.
- **Done when** pressing − from that same value lands on the **next loadable weight below it**.
- **Done when**, if the size-scaled step would land on something smaller than the smallest change
  the gym can actually make, the smallest achievable change is used instead (the button always does
  *something*).
- **Done when** − and + are mirror images: the same size step in each direction from the same
  starting weight.
- **Done when** − never drives the weight below zero.
- **Done when** the behaviour follows the **currently selected gym** — the same exercise gives
  different steps at a gym with different plates.
- **Done when** no new setup, configuration, or onboarding screen has been added for the user to
  fill in.
- **Done when** the per-side readout under the field agrees with whatever the buttons produce
  (see brief 02 — these must be verified together).

## Scope and constraints

**In scope**
- The **−** and **+** buttons flanking the weight field on the workout logging screen.

**Out of scope**
- The rep field's controls.
- Any change to how weights are *stored*, or to historical logged data.
- Any new gym-configuration UI. Explicitly ruled out by the user (they were offered it and
  declined).

**Hard constraints**
- The "always loadable" guarantee is **absolute** where it applies (barbell, plate-loaded
  dumbbells). The user restated this when accepting improvement A: *"it must always be loadable
  with the weights available in that gym."*
- The app decides the step. No per-exercise user override.

## Decisions baked in (confirmed by the user)

1. Step size **scales with the magnitude of the load**, not from a per-exercise name list. (1c, improvement A)
2. + **snaps to the next achievable weight**, including from odd hand-typed values. (1b)
3. **No** per-gym dumbbell-rack or machine-stack entry. Sensible defaults where the app is blind. (1a-i, Q1)
4. **−** mirrors **+** exactly. (1d)

## Decisions the user deferred — flag for whoever builds it

- **Bodyweight exercises** (pull-ups, dips), where the weight field is empty or zero: the user was
  never asked what +/− should do here. Item 4's answer (4d) shows they think of these as
  rep-driven, not weight-driven. **Needs a decision.**
- The **calculator pad has its own typed + and − operators** (type `5`, press `+`). The user's items
  1 and 2 are both about the *external* buttons. Whether the pad's typed arithmetic should also snap
  to a loadable weight was never asked. Recommend treating typed arithmetic as explicit user intent
  and leaving it alone, but **this is not confirmed.**

## Considerations for whoever builds it (surfaced, not decided)

- An exercise's equipment classification is what decides which guarantee applies. Exercises whose
  equipment is unknown or unclassified need a defined fallback.
- "Scales with the load" needs a defined behaviour at the very bottom of the range (a 2 kg
  dumbbell) so the step does not collapse to something meaningless.
- The user's stated example pair is the best sanity check available: **bench press must not offer
  0.5 kg; lateral raise may.** Both should end up as fixtures.
- Changing gyms mid-session changes the answer. Worth confirming the buttons follow the active gym
  rather than a value captured when the screen opened.

## Grounded facts (verified 2026-08-06, for orientation only)

- The buttons and their hard-coded `2.5f` live in
  `/home/migul/treningsprogram/app/src/main/java/com/migul/treningsprogram/ui/log/LogWorkoutFragment.kt`
  (lines 94–104).
- Plate decomposition already exists at
  `/home/migul/treningsprogram/app/src/main/java/com/migul/treningsprogram/ui/log/PlateMath.kt`.
- Gym inventory fields (`barWeightKg`, `dumbbellBarWeightKg`, `platesCsv`, `loadableDumbbells`) are on
  `/home/migul/treningsprogram/app/src/main/java/com/migul/treningsprogram/data/db/entity/GymPreset.kt`.
  `platesCsv` is stored **per pair**.

## Standing constraints

- Build via `./build.sh`, never `./gradlew` directly.
- No commits or releases unless the user asks.
- No on-device or automated UI tests; verify via build + unit tests. The user does the device check.
