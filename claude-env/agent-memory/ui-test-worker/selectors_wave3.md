---
name: selectors-wave3
description: Selectors, nav, DB schema, and the deload-trigger gotchas for Wave 3 feature E2 (multiple programs + periodized mesocycles + triggered deload)
metadata:
  type: reference
---

Wave 3 = feature E2 (multiple programs / mesocycle blocks / stall-triggered deload). Verified on APK md5 2a9189b72ff11a8e5694d90a479ca0bb (schema bumped to **user_version=13**). See [[selectors-wave1]] [[selectors-wave2]] [[db-seeding-recipe]].

## DB schema v13 (new)
- New table `programs(id, name, createdAtMs, isActive, mesocycleWeeks, blockStartWeek, isDeloadActive, isFrozen)`. Exactly ONE row has isActive=1 (enforced by ProgramDao.setActive transaction).
- `planned_exercises` gains nullable `programId`. Plan queries are program-scoped via `programDao.observeActive().flatMapLatest{ getForWeekInProgram(active.id, weekStart) }`. v12→v13 migration backfills all existing rows to the default program ("My Program") and creates it active.
- The migration runs on first launch of the v13 APK; poll `PRAGMA user_version` until it reads 13 before reading the programs table.
- `Program.weekInBlock(mesocycleWeeks, blockStartWeek, currentMonday)` = ((currentMonday-blockStartWeek)/weekMs)+1, clamped 1..mesocycleWeeks. blockStartWeek is set to thisMonday() when you first enable a block.

## Program tab (fragment_program.xml) — switcher card ids
- Card `card_program_switcher` (GONE if 0 programs). Spinner `spinner_program` (Android Spinner; CLOSED shows the active label, TAP opens a dropdown of all program labels). Buttons `btn_save_program` ("Save as new") + `btn_program_options` ("Options"). Deload chip `tv_program_deload_chip` (text "🌿 Deload", amber) visible iff active program isDeloadActive.
- Spinner label format: `"<name>"` + (mesocycleWeeks>0 ? "  •  <N>-wk block") + (isFrozen ? "  •  frozen"). So the dropdown row for a block program is e.g. "Travel Program  •  6-wk block", NOT bare name. Match with regex `.*Travel Program.*` when tapping dropdown rows.
- "Save as new program" dialog: EditText has NO stable id (android:id/edit not present). After tapping btn_save_program and asserting title "Save as new program", just `inputText` directly (the dialog EditText is focused) then tap "Save".
- "Options" dialog (btn_program_options) is a MaterialAlertDialog list: items = "Rename", "Regenerate program now", "Make a mesocycle block"/"Turn off mesocycle block", "Freeze (...)"/"Unfreeze (...)", and "Delete this program" (only when >1 program). Mesocycle length picker offers 4/5/6/8-week block.

## Home (fragment_home.xml) — deload banner
- `card_deload` (amber MaterialCardView, strokeColor #FFB347, default visibility GONE) shown iff active program isDeloadActive. Children: `tv_deload_title` ("🌿 Deload week in effect") + `tv_deload_body` ("Some lifts have plateaued, so this week is a lighter deload..."). It is BELOW the fold (after level card, before card_recovery) — `scrollUntilVisible {id: card_deload, direction: DOWN}` first.
- Home "today" plan = `tv_today_plan` (bullet list "• name  SxR @ Wkg"), ALSO below the fold (after card_recovery + Weekly Challenges). Scroll to it. Home is program-scoped: switching the active program changes today's bullets.

## Verifying program SWITCH without merge
Give each program a DISTINCT plan first (rename one exercise per program per day via DB UPDATE), then switching the spinner must swap which marker shows and the other must be GONE. Confirmed: save copies rows (each program ends with its own 29 rows, 58 total — NOT merged); switch flips isActive and the active row count stays separate.
