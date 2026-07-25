---
name: maestro-flows-wave3
description: Maestro flow locations for Wave 3 (E2 multiple programs / mesocycle / triggered deload) and per-flow seeding pre-state
metadata:
  type: reference
---

Wave 3 (E2) flows authored under the run's scratch dir (NOT committed to repo flows/); copy into /home/migul/treningsprogram/flows/ if keeping. Verified on APK md5 2a9189b... See [[selectors-wave3]] [[deload-trigger-notes]] [[maestro-flows-wave1]].

- `e2a_save_program.yaml` — Program tab switcher; "Save as new program" → name "Travel Program" → it becomes active (no stable EditText id; inputText directly into the focused dialog field).
- `e2a_switch_program.yaml` — needs two programs with DISTINCT Monday markers (DB UPDATE MYPROG_MARKER / TRAVEL_MARKER). Open spinner dropdown, pick "My Program", assert Monday shows the other marker and the prior is GONE.
- `e2a_home_reflects_switch.yaml` — distinct Wednesday markers per program; Home today (scroll to tv_today_plan) reflects active program; switch on Program tab; Home now shows the other.
- `e2b_mesocycle.yaml` — Options → "Make a mesocycle block" → "6-week block" → spinner label shows ".*6-wk block.*". Confirm DB: mesocycleWeeks=6, blockStartWeek=thisMonday.
- `e2c_trigger_only.yaml` — Options → "Regenerate program now"; assert layout_regen_progress appears (generation STARTED). Don't assert the deload chip in the same flow (live AI is slow/flaky on Waydroid).
- `e2c_deload_indicators.yaml` — after `UPDATE programs SET isDeloadActive=1 WHERE isActive=1`: Home card_deload banner ("Deload week in effect", scroll down) + Program tv_program_deload_chip ("Deload"). This is the deterministic indicator check.
- `e2d_robustness.yaml` — rapid spinner switch (regex `.*My Program.*` / `.*Travel Program.*`, index:0), options open/dismiss storm (back to dismiss), Home↔Program tab churn. No crash; switcher persists.

## Quirks
- Spinner dropdown rows carry the full label incl. "  •  6-wk block" suffix — match program rows by regex, not exact name. Tapping bare "Travel Program" when it's the active CLOSED spinner just dismisses (no-op).
- `launchApp: {stopApp: true}` at top of each (back-stack restore on Waydroid).
- Screenshots land in repo root (/home/migul/treningsprogram/e2*.png) — clean them up; they are untracked.
- ALWAYS snapshot DB before seeding (run-as cp to files/), restore after. The original device DB was user_version=12 (v13 APK migrates on launch); restoring the v12 backup is fine — app re-migrates next launch.
