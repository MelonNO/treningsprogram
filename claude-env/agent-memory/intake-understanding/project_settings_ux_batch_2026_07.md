---
name: settings-ux-batch-2026-07
description: Confirmed post-v1.16.0 12-item Settings/UX/recovery batch at docs/intake/settings-ux-batch-2026-07/ — clusters, key decisions, Program-tab collision hazard
metadata:
  type: project
---

Confirmed 12-item batch (user's own numbering 1–12) written to `docs/intake/settings-ux-batch-2026-07/` (INDEX + brief-01..12). Understanding user-approved (relayed via coordinator); briefs written but NOT dispatched.

**Why:** post-v1.16.0 Settings information-architecture cleanup + a few Program-tab/recovery fixes.

**How to apply — clustering (so future intake reuses seams):**
- **Cluster A (ONE worker) = items 1,2,3,4,5,6,7** — all touch `SettingsFragment`/`fragment_settings.xml` + `nav_graph.xml` + `MainActivity` tab-map/Profile-reselect + `PreferencesManager`. New **"App Settings"** screen (item 4, intake named it, slot: …Backup&Data → App Settings → About) holds EXACTLY day-reset (moved from Training Profile, +item1 24h labels ride along) + auto-rebalance toggle (moved from Program-options dialog, +item2 default→ON but never override explicit prior choice). Item5 Debug→under Backup&Data; item6 Coach Summary→under AI&Program; item7 final order Training Profile→AI&Program→Exercise Library→Backup&Data→App Settings→About; item3 Profile-btn single tap from any sub-setting returns to Profile ROOT.
- **Cluster B = items 8,9** — generation launched from Settings. Item9 BUG: `SettingsViewModel.doGenerate`→savePlan is full-fresh regen that WIPES logged days (code comment admits it); must reuse existing "Regenerate (keep logged days)" preserve+rebalance for BOTH Settings entry points. Item8: surface full-gen loading anim on Program tab (no auto-switch; Settings keeps its own status = additive).
- **Item 10 = standalone HIGHEST RISK** — remove "Do this workout today"; Start Workout on another day moves→today+rebalance. BROADENING: currently gated to when today NOT logged; now allowed even when today IS logged → append moved workout into today's existing logged session (one continuous session, don't overwrite/dup). Replace today's PLANNED workout if today unlogged.
- **Item 11 standalone** = collapse "Why your program changed" card by default (always starts collapsed, no persistence).
- **Item 12 standalone/independent** = replace flat 48h `MuscleRecovery.RECOVERING_UNTIL_MS` with per-muscle-group base table scaled by LOGGED EFFORT (`WorkoutSet.rpeLabel`, NOT volume/load); deterministic on-device, no API. Flagged build-time: [12a] blank effort fallback (recommend=medium), [12b] fine-grain vs coarse-7 table grain.

**KEY HAZARD to reuse:** the **Program-tab surface** (`ProgramFragment.kt` + `fragment_program.xml`) is touched by items 4 (removes rebalance line from program-options dialog), 8, 10, 11 → must serialize/single-own (recommended land order 10→11→8, rebase 4's dialog line). "Today" everywhere must respect [[project_ux_batch_2026_07]]'s configurable day-boundary cutoff, not raw midnight.

**Grounding facts learned (verify before reuse):** effort IS logged per set as `WorkoutSet.rpeLabel` (String). Auto-rebalance pref default currently `false` w/ no unset-sentinel (item2 needs migration/sentinel to tell "never set" from "explicit off"). Bottom-nav Profile→`ProfileFragment` root→`settingsFragment` list→sub-screens (2 levels deep). MuscleRecovery drives Home "Muscle Recovery" card (C4/U1), uses finer-grain taxonomy + 1.0/0.6/0.3 synergist weighting.
