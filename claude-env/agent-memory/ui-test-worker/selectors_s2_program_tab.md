---
name: selectors-s2-program-tab
description: Program-tab S2 selectors/nav — switcher card, named-program dialogs (Save/Rename), deload regen behavior, rapid-edit race verification recipe
metadata:
  type: reference
---

S2 = Program tab (bug-sweep). Verified on APK md5 e058a93edb40910602fadc515580821a (wave1-integration branch). See [[selectors-wave3]] [[selectors-e1-manual-edit]] [[harness-waydroid-quirks]] [[db-seeding-recipe]].

## Program switcher card (fragment_program.xml)
- `card_program_switcher` (top card). `spinner_program` shows active program label; TAP opens dropdown of all programs. `btn_save_program` = "Save as new", `btn_program_options` = "Options". `tv_program_deload_chip` = "🌿 Deload" amber chip (top-right), VISIBLE iff active program isDeloadActive.
- Options dialog (MaterialAlertDialog list): Rename / Regenerate program now / Make a mesocycle block / Freeze (stop weekly AI adaptation) / **Delete this program** — "Delete this program" appears ONLY when >1 program (confirmed: with 1 program left it is absent → can't-delete-the-last enforced). Delete-active → another program auto-promoted to isActive=1, deleted program's planned_exercises rows removed, no crash.
- "Save as new program" dialog: title "Save as new program", hint "Program name", buttons Cancel/**Save**. Save copies the current week's plan into the new program (each program ends with its OWN 29 rows; 58 total, NOT merged) and makes it active.
- "Rename program" dialog: title "Rename program", name prefilled, Cancel/Save.

## CRITICAL IME quirk for these dialogs (Waydroid) — see [[harness-waydroid-quirks]]
- DO NOT use Maestro `hideKeyboard` in the Save/Rename dialogs — it dismisses the dialog (back-press) BEFORE you can tap Save. Just `inputText` (the dialog EditText is auto-focused) then `tapOn: text: Save`. Maestro resolves Save's position even after the soft-keyboard pushes the dialog upward.
- If driving via raw `adb input`: the soft keyboard REPOSITIONS the dialog UP (Save button moves from y~600 to y~370 on the 1920x1044 window). Re-screenshot to find the moved Save button; a fixed-coord tap at the original y hits a keyboard key instead. Maestro avoids this.

## Rapid-edit RACE verification (the headline) — recipe
- Drive reorder/delete in fast succession (Maestro `repeat`, or parallel `adb input tap ... &`+`wait` on a card's ↓/↑ at the action row). PROVE integrity with sqlite3, NOT the UI (Maestro only indexes ON-SCREEN list instances, so a 6-card day never has all 6 tv_exercise_name visible at once — don't assert index 5).
- Integrity query: `SELECT COUNT(*),COUNT(DISTINCT orderInDay),COUNT(DISTINCT exerciseName),MIN(orderInDay),MAX(orderInDay) FROM planned_exercises WHERE dayOfWeek=? AND programId=?` — pass = count==distinctOrder==distinctName and min=0/max=N-1 (contiguous). Fix HELD under: reorder storm, parallel up+down storm, and delete-interleaved-with-reorder. NO loss/dupe, orderInDay always contiguous.
- Action-row button coords on the 1920x1044 window for the TOP card: Edit~(1737,783), ↑~(1778,783), ↓~(1808,783), Delete~(1852,783). 2nd+ cards are below the fold; scroll first.

## Deload regen behavior (S2)
- `nextDeloadStateForRegen(currentlyDeloading=true, replacingCurrentWeek=true) → true` KEEPS the deload on a same-week "Regenerate program now" (the original bug was it dropping). The flag is written ONLY on generation .onSuccess (`setActiveDeload`); on .onFailure nothing changes → flag stays whatever it was. So a same-week regen NEVER drops the deload (confirmed: chip + isDeloadActive=1 persisted across an in-flight regen and after it returned). Seed deload deterministically: `UPDATE programs SET isDeloadActive=1 WHERE isActive=1` + wal_checkpoint (live trigger is flaky, see [[deload-trigger-notes]]).
- In-progress UI: `layout_regen_progress` (amber indeterminate bar + tv_regen_status "Generating your plan…") shown while `isDayGenerating`; Start Workout / Swap buttons disabled. Cleared on terminal. On failure → friendly error Snackbar (LENGTH_LONG) + spinner cleared (no silent hang). NB: live regen on Waydroid frequently takes 150s+ per generate POST and often ends in onFailure (no savePlan) even after a 200 — completing end-to-end is flaky.
