# C2 — Log/timer controls: replace emoji glyphs with the Auros icon set

**Type:** Refinement (consistency)
**Cluster:** B (Log) — one worker with L1; do C2 after L1
**Outcome-only.**

## Context
Several **functional controls** on the logging screen and rest timer are rendered as raw emoji/unicode text glyphs rather than the app's bundled vector icons:

- `fragment_log_workout.xml`: pause button `⏸` (`btn_pause_workout`), timer-recall button `⏱` (`btn_timer_recall`), image-expand hint `⛶` (`tv_image_expand_hint`).
- The rest-timer bottom sheet uses similar glyph controls.

Emoji glyphs render inconsistently across devices and font stacks (colour vs monochrome, differing metrics, baseline shifts), and they clash with the Auros vector iconography used elsewhere (e.g. `ic_arrow_up_right`, nav icons). The result is a subtly off, non-branded look on the app's most-used screen.

## What the user wants (end result)
The interactive controls on the log screen and rest timer use the same crisp, palette-tinted vector icons as the rest of the app, so the logging surface looks consistent and intentional on every device.

## Acceptance criteria (Done when …)
- The pause, timer-recall, and image-expand controls on the log screen present as vector icons tinted to the Auros palette, not emoji glyphs.
- Each control keeps its exact current behaviour, hit target (≥44dp effective), and position.
- The rest-timer control glyphs that are interactive are likewise vector icons (skip/adjust actions), consistent with the log screen.
- No calculator-keypad change: the weight keypad's `+ − ✓ ⌫ C` stay as-is (they read as calculator semantics, not brand iconography) — see A-C2a.
- Icons render correctly in the dark Auros theme; build passes.

## Scope and constraints
- **In scope:** functional/interactive glyph controls on the log screen and rest-timer sheet.
- **Out of scope:** the weight keypad math glyphs; decorative emoji inside celebration/Wrapped (gamification, stays vivid); section-header eyebrows (C1 owns those).
- **No new dependencies** — reuse existing vector drawables or add plain local vector XML; no icon-pack library.

## Decisions baked in
- Interactive controls use the vector icon set; decorative/celebration emoji are untouched.

## Assumptions (user may override)
- **A-C2a** — Scope limited to log-screen + rest-timer interactive controls (pause, timer-recall, image-expand, timer skip/adjust). Keypad math glyphs stay. If the user wants a broader emoji-glyph sweep app-wide, that's a larger follow-up, not this item.

## Considerations for whoever builds it
- Suitable vector icons may already exist in the project; if a needed glyph has no vector equivalent, add a minimal local vector drawable (no library).
- Keep `MaterialButton` icon insets/min-width tidy so the small 36dp control buttons don't grow.
- Same-file hazard with L1 (`fragment_log_workout.xml`) — that's why B is one worker.
