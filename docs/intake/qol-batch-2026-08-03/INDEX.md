# Intake Index — QoL batch 2026-08-03

Prepared for: **Project-lead orchestrator**
Source: user request, 6 items (5 brought to intake + 1 added during clarification), 2026-08-03
Status: **CONFIRMED** — user answered all clarifying rounds and confirmed the full restated understanding with "yes" (relayed verbatim via coordinator, 2026-08-03). Creating these documents does not itself dispatch work; dispatching is a separate user instruction.

Baseline: v1.29.0 on `main`.

## Items

| ID | Title | Type | Brief | Status |
|----|-------|------|-------|--------|
| 01 | Dumbbell recognition in per-side plate readout (general fix) | Bug | `brief-01-dumbbell-plate-readout.md` | Confirmed |
| 02 | Remove Recap skeleton loader | Bug | `brief-02-remove-recap-skeleton.md` | Confirmed |
| 03 | Tappable calorie estimate → actual-numbers explanation | Feature | `brief-03-calorie-estimate-explanation.md` | Confirmed |
| 04 | Database-mismatch flagging + debug list with re-match/unflag/copy | Feature | `brief-04-db-mismatch-flagging.md` | Confirmed |
| 05 | Show final-set effort in the "Last time" line | Feature | `brief-05-last-set-effort.md` | Confirmed |
| 06 | Exercise-info sheet title = program name; DB name above DB content | Feature | `brief-06-info-sheet-title.md` | Confirmed |

## Merge / cluster and parallelization guidance

- **Cluster A = items 04 + 06 (one worker, build together).** Both change the exercise-info sheet (`ui/log/ExerciseInfoBottomSheet.kt`). 04 is the batch's largest item (new persistence, debug-menu list, search re-match); 06 is a small title/label change in the same file. Doing them separately guarantees a merge collision.
- **Cluster B = items 02 + 03 (one worker, 02 before 03).** Both edit the Recap screen (`ui/history/HistoryRecapFragment.kt`): 02 removes the skeleton, 03 makes the calorie chip tappable. Land 02 first so 03 builds on a clean fragment.
- **Item 01 independent** — `ui/log/PlateMath.kt` classification + its unit tests.
- **Item 05 independent** — `ui/log/LastSessionFormat.kt` (+ the log-screen query already carries `rpeLabel`).
- **Cross-group hazards:** minor adjacency only. Item 04's flag entry points live *inside* the sheet, so `LogWorkoutFragment`/`ProgramFragment` should need little or no change — but if a worker does touch `LogWorkoutFragment`, note item 05 is nearby (different file in practice: `LastSessionFormat` is a pure object). Item 04's re-match override sits upstream of what item 06 displays — same worker anyway (Cluster A).
- **Suggested order:** all four groups (A, B, 01, 05) can run in parallel. If sequencing, start Cluster A first (largest), then B, then 01 and 05.

| Group | Items | Surface |
|-------|-------|---------|
| A | 04 + 06 | ExerciseInfoBottomSheet + debug menu + new persistence |
| B | 02 → 03 | HistoryRecapFragment |
| C | 01 | PlateMath |
| D | 05 | LastSessionFormat |

## Confirmed decisions

1. Item 01 is a **general** fix: all dumbbell-by-nature exercises get the readout, not just Zottman curls.
2. Item 02: **remove** the skeleton (same treatment as History/Program in v1.28.0), not repair it.
3. Item 03: explanation shows the **session's actual numbers** plugged into the calculation, not a generic method description.
4. Item 04: flag option available **everywhere** the exercise-info sheet opens; flagging only records (nothing else changes at flag time); debug list supports **unflag**, **search-based re-match**, and **one-tap copy**; a re-match takes real effect app-wide and **auto-clears the flag**.
5. Item 05: only the **final set's** effort from the previous session, even when sets differ.
6. Item 06: top title = the program's (AI-generated) exercise name; the database entry's own name appears as a label right above the database content.

## Decisions made under delegation (user has standing "you choose" on method/UI shape — veto-able)

- D1 (03): presentation of the calorie explanation (dialog/bottom sheet) and its layout — builder's choice, content per brief.
- D2 (05): exact wording/placement of the effort suffix in the "Last time" line.
- D3 (06): hide the database-name label when it is identical to the program name (avoid showing the same string twice).
- D4 (04): copy format of the debug list (plain text, one mapping per line) and the shape of the search UI.

## Assumptions applied (labelled — user may veto)

- A1 (04): flags and re-match overrides are persistent user data and should survive backup/export-import like other persistent data (backup-format version bump if needed).
- A2 (05): sessions logged before per-set effort existed simply show no effort suffix — no backfill.

## Cross-cutting constraints

- Build via `./build.sh` (never `./gradlew` directly).
- No commits or releases unless the user asks.
- No on-device / automated UI tests unless the user asks; verify via build + unit tests.
