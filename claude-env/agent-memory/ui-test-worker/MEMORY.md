# UI Test Worker — Memory Index

- [Wave 1 screens — selectors & nav](selectors_wave1.md) — C1/C4/E3/B3 IDs, nav paths, and quirks (Recap tab has no standalone cards; tap exercise rows)
- [DB seeding recipe](db_seeding_recipe.md) — schema, run-as sqlite3 quoting gotchas, backup/restore, date computation for recovery bands
- [Maestro flows for Wave 1](maestro_flows_wave1.md) — where the flows live and what each covers
- [Wave 2 screens — selectors & nav](selectors_wave2.md) — B1 coach-summary + B2 rationale IDs, the launch trigger guard, empty-state determinism trick
- [Maestro flows for Wave 2](maestro_flows_wave2.md) — B1/B2 flow locations and seeding pre-state per flow
- [Wave 3 screens — selectors & nav](selectors_wave3.md) — E2 programs table (schema v13), switcher/spinner/deload-chip IDs, Home deload banner, switch-without-merge check
- [Deload trigger notes](deload_trigger_notes.md) — how to seed ≥2 stalled lifts + fire deload; live-gen 6-call pipeline + 120s OkHttp timeout flakiness on Waydroid; deterministic indicator fallback
- [Maestro flows for Wave 3](maestro_flows_wave3.md) — E2 flow locations, per-flow seeding, spinner-dropdown regex quirk
- [E1 manual-edit — selectors & nav](selectors_e1_manual_edit.md) — item_day_overview card buttons (edit/move/delete), idless dialog field order, Home tv_today_plan format, orderInDay re-index
- [Maestro flows for E1](maestro_flows_e1.md) — E1 flow chain, seeding short-name day, scroll/index gotchas (scroll the chip not the name; enabled-filter index unreliable)
- [Bug-sweep wave1 selectors & nav](selectors_bugsweep_wave1.md) — U1 recovery (recovering-only fine-grain), F3 regen ids, S1 log ids+row format, S7 backup (import is MERGE not replace), S8 wizard/nav guards
- [Waydroid+Maestro harness quirks](harness_waydroid_quirks.md) — NEVER svc-wifi-disable (kills adb+routing); IME inputText appends/eraseText no-op + field-clear recipe; ANR trace via dumpsys dropbox; uiautomator non-idle w/ rest timer; launchApp resets nav; setOrientation enums
- [Settings/Debug screen selectors](selectors_settings_debug.md) — Profile→card_settings hub rows; AI&Program (et_api_key/btn_save_api_key/btn_generate_now=week-plan gen); Debug logs (prompt/crash); v1.10.4 streaming generate = main-thread ANR
- [S2 Program-tab selectors & nav](selectors_s2_program_tab.md) — switcher/spinner/options, Save/Rename dialog IME quirk (no hideKeyboard), rapid-edit race DB-integrity recipe, deload-keep-on-regen behavior
- [Maestro flows for S2/S3](maestro_flows_s2_s3.md) — S2 flow locations + the live-AI regen flakiness finding (4 runs all onFailure; clean fail path; success effectively BLOCKED on Waydroid)
- [U2 XP + onboarding-gate selectors (patch v14)](selectors_u2_xp_patch.md) — XP bar surfaces, XP log empty/populated, freestyle-workout XP capture; DETERMINISTIC non-AI onboarding via import-merge (hasCompletedOnboarding OR-logic)
- [B08/B09 selectors & nav](selectors_b08_b09.md) — rest-day two-mode switch/chips/hint (switch ON=count, OFF=rest), wizard defaults Sat+Sun; B09 regenerate-keep-logged label + no-op guard + settings-AI caption
- [Maestro flows for B08/B09](maestro_flows_b08_b09.md) — flow locations + gotchas (first-run via rm encrypted prefs; Start-Workout creates draft session; dynamic day chips by text)
