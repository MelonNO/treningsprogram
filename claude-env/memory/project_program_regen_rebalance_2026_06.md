---
name: project_program_regen_rebalance_2026_06
description: v1.11.0 batch — single-day regen parity bug-fix + week-rebalance/move features + gen notification + wait UX
metadata: 
  node_type: memory
  type: project
  originSessionId: 9c2f9196-b67f-473d-af61-9337036b29e5
---

5-item batch (intake-confirmed via [[feedback_intake_agent_verbatim_relay]]), briefs at `docs/intake/program-regen-rebalance-2026-06/` (INDEX.md + brief-P1..P5; untracked, not committed — matches the project's intake-docs convention). **SHIPPED v1.11.0 (2026-06-28, commit 13aefce on main, DB UNCHANGED at Room v15, versionCode 46→47, release API-verified live: asset `treningsprogram-v1.11.0.apk`, md5 14106213…).** Build clean; 556 unit tests pass (15 new). Implemented by ONE project-lead-orchestrator pass, no worker fan-out (all items collide on the `AiRepository` generation seam + `ProgramFragment`).

Items as shipped:
- **P4 (Items 3+5 merged; bug+feature)** — single-day regen (`generateSingleDayProgram`) now has FULL weekly-generator parity. Root cause of the two bugs: the single-day prompt sent NO history + hard-coded `"targetWeightKg":0` in its return-shape example (→ all-bodyweight), and passed no history/variation signal while excluding the current day (→ same canonical workout each re-roll). Fix: inject real history + weight guidance + realistic example weight; add a variation theme + per-day exercise blacklist; wire in the 3-attempt retry loop, strict ±10-min `dayDurationFeedback` gate + `trimOverflowToWindow` salvage, and `validateProgram` peer-review run in FULL-WEEK context. Fully replaces the day incl. logged sets (`saveDayPlan` deletes+reinserts). "Item 3" was NOT about auto-regen — user meant "single-day regen lacks the week-gen verification pass."
- **P1 (feature)** — new auto-rebalance toggle (default OFF, in Program Options dialog) regenerates non-logged days of the CURRENT week when a day's PRIMARY muscle focus changes (manual or generated); edited day locked; logged days untouched. Minor edits (sets/reps, swap within same focus) do NOT trigger it.
- **P2 (feature)** — "Do this workout today" button pulls any other day's plan into today; move + discard-of-today's-original + rebalance COMMIT ONLY on workout completion (abandon ⇒ week unchanged, assumption [P2-A1] user-confirmed); all directions; always rebalances regardless of P1 toggle.
- **P3 (feature)** — system notification on terminal completion (success OR after 3 fails) for ALL gen types, ONLY when backgrounded; tap opens Program tab. Reuses existing notif channel infra.
- **P5 (feature)** — rotating tip line (12 items, ~4.5s) on all 3 generate-wait screens (Program regen, Settings AI, Setup wizard); real status always visible on a separate line.

New files: `domain/MuscleFocus.kt`, `domain/DayMovePlanner.kt`, `notify/AppForegroundState.kt`, `notify/GenerationNotifier.kt`, `ui/common/GenerationTips.kt` (+3 test classes). Modified the AiRepository/WorkoutRepository/ProgramViewModel/ProgramFragment/Log*/Shared*/PreferencesManager/MainActivity/App/manifest/3 layouts.

RESIDUALS (user verifies on-device after updating — [[feedback_always_skip_waydroid]] + API budget [[feedback_frugal_api_testing]]): P2's cross-screen move flow and P3's actual background-notification behavior confirmed by build+logic only (no Waydroid); P4's real-model output (real weights/variety) unproven end-to-end (no live generate→validate). P4 single-day strict gate can be hard to satisfy for one day (known G1/H3 class issue) — mirrors weekly trim-salvage, surfaces a clear error on persistent failure, never stalls.

SHIP: coordinator drove the release directly (user said "ship"); orchestrator deadlocks on relayed ship authorization, so coordinator owns the publish — same pattern as [[project_generation_peer_review_timebudget_fix]] (v1.10.7). [[reference_release_process]].
