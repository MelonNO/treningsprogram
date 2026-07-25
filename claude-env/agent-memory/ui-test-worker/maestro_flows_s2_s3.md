---
name: maestro-flows-s2-s3
description: Maestro flow locations for S2 (Program tab) + S3 (AI gen) bug-sweep coverage, plus the live-AI flakiness finding
metadata:
  type: reference
---

S2/S3 bug-sweep flows in `/home/migul/treningsprogram/flows/`. Verified on APK md5 e058a93edb40910602fadc515580821a (wave1-integration). See [[selectors-s2-program-tab]] [[selectors-wave2]] [[deload-trigger-notes]] [[harness-waydroid-quirks]].

## S2 flows authored
- `s2_rapid_edit_race.yaml` — rapid reorder storm on Mon (6-ex day); headline race-fix. PASS. DB-integrity proven separately via sqlite3 (Maestro can't see all 6 list rows at once).
- `s2_field_edit_persist.yaml` — two rapid field edits same day (RDL sets 4→5, Sumo weight 32→36) both land. PASS. (do NOT hideKeyboard in edit dialog; scroll card-1 into view before its btn_edit_exercise index 1.)
- `s2_field_edit_rotation.yaml` — edited values survive setOrientation LANDSCAPE_LEFT↔PORTRAIT. PASS.
- `s2_named_programs_create.yaml` — Save-as-new copies plan + activates new program. PASS (no hideKeyboard).
- `s2_program_switch.yaml` — switch active program swaps plan markers, no merge. PASS (seed distinct per-program Wed card-0 markers first).
- `s2_deload_indicators.yaml` — seed isDeloadActive=1, assert Program tv_program_deload_chip + Home card_deload. PASS.
- `s2_empty_first_run.yaml` — delete planned_exercises → "No Program Yet" empty card, week/day sections hidden, switcher still responsive. PASS.

## Reused existing flows
- `b1_coach_summary_reach_populated.yaml`, `b1_coach_summary_empty.yaml` (delete weekly_summaries leave pref), `b2_rationale_present.yaml` (seed rationale marker), `b2_rationale_blank_hidden.yaml`. All PASS on this APK.

## KEY S3 live-AI finding (this harness)
- 4 live "Regenerate program now" runs (3 in a deload week, 1 non-deload) NEVER completed to a saved program. Every one ended in `.onFailure` with the EXISTING plan preserved (no empty/blank plan saved) and a clear Snackbar ("No JSON found in AI response" captured; "Program rejected after all attempts" is the other expected message). The in-progress `layout_regen_progress` overlay ("Generating your plan…") shows during the call and clears on terminal — NO silent hang, NO crash, app responsive after each.
- Generate POSTs take 80–153s each (one returned 200 at 152986ms, past the 120s readTimeout, without erroring). The non-deload run got furthest: 3 sequential 200s (generate 86s + validate 4.5s + retry-generate 141s) then still onFailure. Deload runs only ever made 1 POST before failing.
- Net: the S3 SUCCESS criterion (a fresh generation completing to a saved multi-day plan) is effectively BLOCKED on Waydroid here — generation reliably returns 200s but the parse/validate/duration-gate rejects, so nothing saves. The failure-handling path is solid. The existing 29-row/6-day plan IS a valid AI-generated multi-day program (just not freshly re-generated this run).
