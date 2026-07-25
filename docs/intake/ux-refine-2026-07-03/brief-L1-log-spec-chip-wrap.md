# L1 — Log screen: target spec chips must not clip on small screens

**Type:** Refinement (small-screen)
**Cluster:** B (Log) — one worker with C2; do L1 first
**Outcome-only.**

## Context
On the workout logging screen (`fragment_log_workout.xml`, exercise info card), the three target chips — sets, reps, and weight (`chip_target_sets`, `chip_target_reps`, `chip_target_weight`) — sit in a **plain horizontal `LinearLayout` that does not wrap**. This chip row lives in the left column, which shares the card width with an 88dp exercise thumbnail on the right.

On a compact device (≈320dp width), the left column is roughly `screen − 32dp card padding − 88dp thumbnail − 12dp gap ≈ 188dp`. Three chips such as "3 sets", "8–10 reps", and "60 kg" plus their inter-chip margins can exceed that, and because the row neither wraps nor weights, the last chip (weight) is pushed off / clipped. The team already hit this: the "beat last time" chip was moved to its own row with a code comment "so small screens never crowd the spec-chip row" — but the three base chips themselves were left in the non-wrapping row.

## What the user wants (end result)
The target chips always remain fully readable, on any screen width. When they don't fit on one line they wrap to a second line rather than clipping the weight chip off the edge.

## Acceptance criteria (Done when …)
- On a 320dp-wide screen with a long target string (e.g. "8–12 reps" + "100 kg"), all three chips are fully visible — none is clipped or pushed off-screen.
- On normal/large screens the chips still render on one line as today (no visual regression).
- The chip styling (spec-chip background, cyan/on-surface text, sizes) is unchanged.
- The "beat last time" chip on its own row is unaffected.
- Build passes.

## Scope and constraints
- **In scope:** the wrapping/flow behaviour of the three target spec chips in the exercise info card.
- **Out of scope:** chip content, the beat chip, the thumbnail, any other row.
- No new dependency (use existing wrapping-capable containers already in the project's Material dependency, e.g. a ChipGroup/flow — implementer's choice).

## Decisions baked in
- Chips wrap; they never clip.

## Assumptions (user may override)
- **A-L1a** — Chips wrap to a second line on narrow screens. (Alternative: shrink/ellipsize — but wrapping preserves all information, which is preferred.)

## Considerations for whoever builds it
- These are `TextView`s styled as chips, not `Chip` widgets; a flow container or allowing the row to wrap is enough — no dependency change required.
- Verify the thumbnail's fixed 88dp width against the smallest supported width; the wrap fix should hold even at 320dp.
