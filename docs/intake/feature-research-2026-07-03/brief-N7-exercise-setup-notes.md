# Brief N7 — Per-exercise setup notes ("gear memory")

**Type:** Feature (every-session utility)
**Cluster:** Logging-screen pair with B1 (same files — ONE worker); schema pair with N5 (shared migration/backup seam)

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
Session-level notes exist (`WorkoutSession.notes`), but there is nowhere to persist per-exercise setup: bench pin height, seat position, band color, "belt on top sets". A home lifter re-derives this every week. The logging screen already shows quiet per-exercise reference lines (last-time line, Beat chip), and the library has a detail screen per exercise.

## What the user wants (end result)
1. Each exercise can carry one small persistent note, written once and shown every time that exercise comes up.
2. While logging, the note appears as a quiet single line under/near the exercise name — visible without any tap, in the same muted register as the last-time line.
3. The note is editable in place from the logging screen (quick, no navigation away) and from the exercise's library detail screen.
4. Notes survive backup/restore.
5. Exercises without a note show nothing — no empty placeholder.

## Acceptance criteria
- Done when a note saved on an exercise appears on the logging screen the next time that exercise is shown, and on its library detail screen.
- Done when editing/clearing the note from either surface updates both.
- Done when a swapped-in exercise mid-session shows its own note (not the replaced exercise's).
- Done when backup → restore round-trips notes and pre-notes backups restore cleanly.
- Done when the logging screen shows no clutter regression for exercises without notes (the common case).

## Scope and constraints
- **In scope:** one plain-text note per exercise, shown/edited at the two surfaces above, persisted + backed up.
- **Out of scope:** per-session ad-hoc notes (exists), rich text/photos, AI use of notes, note history.
- **DB schema change** (small) + backup surface — coordinate with N5 under one migration/backup bump (see INDEX).
- Standing: build via `./build.sh`; no commits/releases unless asked; no on-device/automated UI tests.

## Assumptions (user may override)
- **A-S1:** one note per exercise (not per exercise-per-program); short single-paragraph text, truncated gracefully in the quiet line with full text on edit.
- **A-S2:** notes attach to the exercise identity the app already uses for history/swaps, so custom "Add anyway" exercises can carry notes too.

## Considerations for whoever builds it
- The logging screen is the app's busiest surface and B1 (warm-up ramp) edits it in the same batch — one worker takes both, B1 first (see INDEX).
