---
name: maestro-flows-wave1
description: Maestro flow locations, naming, and reusable patterns/quirks for Wave 1 (C1/C4/E3/B3) on Waydroid
metadata:
  type: reference
---

Maestro flows live in `/home/migul/treningsprogram/flows/` (alongside existing smoke.yaml, tab_navigation.yaml, full_workout_flow.yaml, rest_timer_notification.yaml). Header is `appId: com.migul.treningsprogram` then `---`. Selectors are `id:` (resource-id, no package prefix) or `text:` (supports regex). See [[selectors-wave1]] for screen IDs.

Wave 1 flows authored (all PASS on APK md5 65d98b5c...):
- `c4_recovery_view.yaml` — Home Muscle Recovery card states.
- `b3_stall_alert.yaml` — Progress tab Plateau-detected card.
- `c1_e1rm_trend_pr.yaml` — Bench Press (stalled) e1RM trend + 1-PR history.
- `c1_squat_multi_pr.yaml` — Squat (progressing) trend + 3-PR history (selects session via dropdown).
- `e3_library_browse_filter.yaml` — library count 873→127→20 with muscle+equipment filters.
- `e3_detail_view.yaml` — full detail (Target/Equipment/numbered instructions).
- `e3_detail_missing_instructions.yaml` — graceful "No instructions available." (Iron Cross).

## Reusable patterns / quirks learned
- **Below-the-fold content**: RecapTrendsFragment PR history (`layout_trend_pr_history`) and the ExerciseDetail instructions are below the fold. Use `scrollUntilVisible: { element: { id: ... }, direction: DOWN }` before asserting. A plain `assertVisible: id` on a below-fold element FAILS even though it exists.
- **Search → tap list row**: after `inputText` in `et_search`, the soft keyboard stays up AND the search field's own text matches your `tapOn: text:`. Always `hideKeyboard` then scope the tap to the list: `tapOn: { text: "...", childOf: { id: rv_exercises } }`. Otherwise Maestro taps the search field, not the row.
- **inputText works on Waydroid here** (search box) — earlier memory warned text entry is unreliable; in this session `inputText` into `et_search` produced correct text every time. Steppers still preferred for numeric fields, but search text was fine.
- **Negative assertions scope**: `assertNotVisible: text` scans the WHOLE screen. On the Progress tab "Squat" appears in a Personal Records list, so a bare `assertNotVisible: Squat` false-fails. Scope it: `assertNotVisible: { text: Squat, childOf: { id: card_stalled } }`.
- **Tab reset / flake**: tapping a bottom-nav id sometimes lands on a sub-screen left from a prior flow (back-stack restored on Waydroid), so the TabLayout text ("Recap"/"Progress") isn't present and `tapOn` errors. Robust opener: `launchApp: { stopApp: true }` then tap `homeFragment` first, then the target tab, then `assertVisible` the sub-tab text before tapping it.
- Running the whole `flows/` folder in one `maestro test flows/` pass is slow (~each flow relaunches app); a 7-flow run exceeds 2 min. Run individually or give a long timeout / background.
- Maestro saves `takeScreenshot: name` PNGs to the **repo working dir** (`/home/migul/treningsprogram/<name>.png`), not the flows dir. These are untracked artifacts — clean them up or don't commit.
