---
name: maestro-flows-wave2
description: Maestro flow locations for Wave 2 (B1 weekly coach summary, B2 program-change rationale)
metadata:
  type: reference
---

Wave 2 flows in `/home/migul/treningsprogram/flows/` (see [[maestro-flows-wave1]] for conventions, [[selectors-wave2]] for IDs). All PASS on APK md5 6be0206bba1036e150cee5bd624a155f.

- `b1_coach_summary_reach_populated.yaml` — Profile→Settings→Coach Summary row → screen; populated list shows tv_week/tv_date/tv_summary_text.
- `b1_coach_summary_empty.yaml` — empty state (layout_empty + tv_empty_subtitle "No weekly summary yet"); requires weekly_summaries cleared (pref guard keeps trigger from refilling).
- `b1_coach_summary_nonblocking.yaml` — fresh launch then drive all tabs + open summary; proves generation doesn't freeze UI.
- `b2_rationale_present.yaml` — Program tab card_rationale + tv_rationale visible (asserts seeded marker "MAESTRO_RATIONALE_MARKER").
- `b2_rationale_blank_hidden.yaml` — rationale blank → card_rationale GONE, card_week still renders, no error.

## Quirks specific to Wave 2
- Use `launchApp: { stopApp: true }` at the top of each so the back-stack doesn't restore a sub-screen (per wave1 note). The Coach Summary screen is reached via tap chain, not a tab.
- For B2 has-rationale: seed `planned_exercises.rationale` then run; restore to '' after. For B1 empty: DELETE weekly_summaries (leave the pref) then run; re-insert the row after.
- Screenshots land in repo root (/home/migul/treningsprogram/*.png) — move/clean them; don't commit.
