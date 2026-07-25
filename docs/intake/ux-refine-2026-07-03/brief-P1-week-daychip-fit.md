# P1 — Program week strip: day chips must fit compact screens

**Type:** Refinement (small-screen)
**Cluster:** — (standalone, Wave 1)
**Outcome-only.**

## Context
The Program tab's week strip lays out **seven day chips** (Mon–Sun) in a horizontal `LinearLayout` (`layout_week_days`), one `item_day_chip.xml` per day, each with `layout_weight="1"`. But inside each weighted column the day badge (`tv_day_abbr`) is a **fixed 40dp × 40dp** square, plus 4dp padding each side and a type/progress label beneath.

Seven fixed 40dp badges = a 280dp hard floor before padding; with per-chip padding it's ~336dp. Inside the week card (screen − 32dp scroll padding − 32dp card padding), a 360dp device leaves ~296dp / 7 ≈ 42dp per column (just barely fits), but a **320dp device leaves ~256dp / 7 ≈ 36.5dp per column — narrower than the 40dp badge**, so the badges get squeezed/clipped and the row crowds.

## What the user wants (end result)
The seven-day week strip fits cleanly on compact phones. The day badges size themselves to the available width instead of forcing a fixed 40dp that overflows on small screens, so all seven days read comfortably without clipping or crowding.

## Acceptance criteria (Done when …)
- On a 320dp-wide screen, all seven day chips (badge + type label + progress) render without clipping or overlap.
- On normal/large screens the strip looks the same as today (no shrinkage regression).
- The selected-day highlight, day-type label, per-day progress text, and tap-to-select behaviour are all preserved.
- Build passes.

## Scope and constraints
- **In scope:** the sizing/fit of the seven day chips on the Program week strip.
- **Out of scope:** the day-detail section, the swipe-to-previous-week behaviour, the week card's other content.

## Decisions baked in
- The week strip adapts to width; it does not force a fixed badge size that overflows.

## Assumptions (user may override)
- **A-P1a** — Badges shrink/adapt to fit (responsive sizing) rather than the row scrolling horizontally — keeping all seven days visible at once is preferred over a scroll.

## Considerations for whoever builds it
- The 40dp square is set on `tv_day_abbr` in `item_day_chip.xml`; making it size from the weighted column (or reducing on compact widths) is the lever.
- Keep the badge legible — verify the abbreviation text (11sp) still fits after any shrink.
