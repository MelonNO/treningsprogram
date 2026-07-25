---
name: maestro-flows-e1
description: Where the E1 manual-edit Maestro flows live, what each covers, and the per-flow seeded pre-state chain
metadata:
  type: reference
---

E1 manual-program-editing flows live in `/home/migul/treningsprogram/flows/`. See [[selectors-e1-manual-edit]] [[db-seeding-recipe]].

## Flows (run in this order — each assumes the prior left its state)
- `e1_edit_exercise.yaml` — EDIT (criterion 1). Edits Bench Press sets/reps/weight/notes; asserts card + chips update.
- `e1_delete_exercise.yaml` — DELETE (criterion 2). Removes Squat (middle); asserts gone + remaining shrink. Scrolls to Barbell Row/Delete button first (below fold).
- `e1_add_exercise.yaml` — ADD (criterion 3). Autocomplete "Plan"→"Plank", reps "60s"; asserts Plank/Bodyweight card appears. MUST scroll to the "Bodyweight" CHIP (not just the Plank name) — scrollUntilVisible stops at 100% of the name, leaving chips below the fold.
- `e1_reorder_exercise.yaml` — REORDER + edge-disable (criterion 4). Moves top card DOWN via `btn_move_down index:0` (top card is always visible — more reliable than tapping a below-fold move-up). Asserts new order + top ↑ disabled + bottom ↓ disabled. For the bottom-edge assert use `assertVisible {id: btn_move_down, enabled: false}` WITHOUT index (index counting with the enabled filter is unreliable on scrolled lists).
- `e1_persistence_home_restart.yaml` — PERSISTENCE (criterion 5). Checks Home `tv_today_plan` reflects edits, then `stopApp`+`launchApp` and re-checks Program tab survived.

## Deterministic seeding (do this BEFORE the flows)
The real plan has 60+char AI exercise names — bad for text matching. SEED today's day with short names. Today's dayOfWeek = device wall-clock weekday (Wed=3 on 2026-06-24). weekStart must = thisMonday() (Mon 00:00 UTC). Seed e.g.:
`DELETE FROM planned_exercises WHERE dayOfWeek=<today> AND programId=<active>;` then INSERT 3 rows (Bench Press 3x8-10@60, Squat 5x5@100, Barbell Row 4x8@70) with orderInDay 0,1,2 and the full NOT-NULL column set (rationale='' , matchSource='', etc. — copy the column list from [[db-seeding-recipe]] schema; planned_exercises is a different table than workout_sets, no rpeLabel here).
Backup first (`cp databases/...db files/e1_backup.db`), restore after (rm -wal/-shm too). Confirmed restore returns to 29 total planned rows.

## Maestro scroll/index gotchas hit here (all flow-side, NOT app bugs)
- Cards are tall; the 2nd/3rd card action rows sit below the fold. Always `scrollUntilVisible` the exact element you assert (a chip, not just the card name).
- `index:` on a shared id only counts elements Maestro currently sees; after a scroll the indices renumber. Prefer scrolling the unique target into view, or assert by `enabled:` filter alone when exactly one matches.
- Tapping a move-up on a below-fold card with `index:1` fails "Element not found" — use the always-visible top card's move-down instead, or scroll first.
