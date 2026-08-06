# Brief 02 — The per-side plate readout is always correct while it is on screen

**Type:** Bug
**Cluster:** A (with brief 01 — same buttons, same screen, same file)
**Source:** User item 2, plus accepted improvement B

> **Outcome-only.** This brief describes the end result and user experience. It does not prescribe
> how to build it.

---

## Context

Under the weight field on the workout logging screen there is a line showing how the weight breaks
down into actual plates — the "per side" readout (bar/handle weight plus the plates you hang on each
end). There is also a calculator-style keypad that can be opened for entering the weight.

## Current (incorrect) behaviour

With the calculator pad **open**, pressing the **+** or **−** button changes the number in the
weight field, **but the per-side plate line underneath keeps showing the breakdown for the old
weight**. It only catches up if the user closes the pad and opens it again.

## Correct behaviour

The per-side readout is **correct whenever it is visible**. If the number it describes changes, it
changes with it — immediately, with no need to close and reopen anything.

## Why this is scoped as a general fix, not a one-line patch

The user accepted **improvement B**: fix the class of problem, not the single trigger.

There is at least one **second, independent** way the readout can be wrong that the user has not
personally hit yet: the readout can be shown **before the active gym's plate profile has finished
loading**, in which case it can briefly describe the wrong gym's equipment. The user's response when
told about it: *"not observed but if it is a big fix it"*, and then **"implement"** against
improvement B.

So the acceptance bar is the **invariant**, not the reproduction:

> Whenever the per-side readout is on screen, it describes the weight currently in the field, using
> the currently selected gym's equipment.

This also matters because **brief 01 changes what the +/− buttons produce**. A readout that lags
behind those buttons would make brief 01 look broken.

## Acceptance criteria

- **Done when** pressing + or − with the calculator pad **open** updates the per-side line at the
  same time as the number, with no close-and-reopen.
- **Done when** the same holds with the pad **closed**.
- **Done when** the readout never displays a breakdown belonging to a different gym than the one
  currently selected — including in the first moments after the screen opens, before gym data has
  loaded.
- **Done when** the readout is consistent with brief 01's new button behaviour: every weight the
  buttons produce shows a breakdown that matches it.
- **Done when** there is no state reachable in normal use where the displayed breakdown does not
  add up to the displayed weight.
- **Done when** switching exercises, switching gyms, or typing a weight by hand all leave the
  readout correct.

## Scope and constraints

**In scope**
- The per-side plate readout on the workout logging screen, in every state where it is visible.

**Out of scope**
- The plate-decomposition maths itself. The user reports the numbers are *right*, just *stale* —
  this is a freshness bug, not a correctness-of-arithmetic bug.
- The layout or wording of the readout.

**Hard constraints**
- The user's original symptom (+/− with pad open) must be fixed. The generalisation is in addition
  to it, never instead of it.

## Decisions baked in (confirmed by the user)

1. Fix generally — "correct whenever visible" — rather than patching the +/− trigger alone.
   (Improvement B, accepted.)
2. The gym-profile-not-yet-loaded case is included even though the user has not observed it.

## Considerations for whoever builds it (surfaced, not decided)

- Because **brief 01 rewrites the +/− handler**, doing these two as one piece of work is
  strongly preferable to two passes over the same code.
- The readout is hidden entirely for gyms with **fixed** (non-plate-loaded) dumbbells. "Correct
  whenever visible" should be read as including "correctly hidden when it cannot be computed" —
  it must not appear with a stale or invented breakdown.
- Worth checking whether the same staleness affects the **warm-up ramp** display, which draws on the
  same plate logic. Not reported by the user; flagged only so it is consciously considered rather
  than missed.

## Grounded facts (verified 2026-08-06, for orientation only)

- All of this lives in
  `/home/migul/treningsprogram/app/src/main/java/com/migul/treningsprogram/ui/log/LogWorkoutFragment.kt`.
- The readout refresh is `updatePlateHint()` (line 456), called from two places (lines 452, 479).
- The +/− buttons call `reseedWeightKeypadIfOpen()` (line 493) — which refreshes the pad's own state
  but **does not** call `updatePlateHint()`. That is the reported symptom's direct cause.
- Naming the cause here is orientation, **not** a prescribed fix — the accepted scope is the
  invariant, which is broader than this one call site.

## Standing constraints

- Build via `./build.sh`, never `./gradlew` directly.
- No commits or releases unless the user asks.
- No on-device or automated UI tests; verify via build + unit tests. The user does the device check.
