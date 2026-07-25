---
name: project-feature-batch-2026-07-03
description: "10-feature batch (goals, relative strength, warm-up ramp, Wrapped, setup notes, +5) shipped as v1.25.0 (2026-07-03); DB v19, backup v6"
metadata: 
  node_type: memory
  type: project
  originSessionId: 79df2a8e-3079-402a-be62-9e4844ca67b9
---

**SHIPPED v1.25.0 (2026-07-03, release commit/tag ac9a7e7 on main, release id 348376464, asset coordinator-API-verified live, versionCode 65, DB v18→v19, backup v5→v6).** From understander research round `docs/intake/feature-research-2026-07-03/` (RESEARCH.md + INDEX + 10 briefs); user picked 10 of the menu; **B3 rep PRs, B6 streak freeze, Wrapped-sharing, goal-aware generation = explicitly NOT picked/deferred**; N2 rest-pacing was already shipped in v1.24.0 (only per-exercise breakdown would be new — user never requested it).

Items: N1 Home numbers-to-beat (= BeatTarget.chipTarget) · N3 relative strength e1RM/BW on Progress (±14 d weigh-in window, 0.5×–2× BW guides) · N4 effort trends → gen prompt (no block when no effort labels; one-line revert if it ever hurts generation) · N5 goal targets (gold chart line, Profile list, Home nudge ≤2.5 kg, reach = working sets only, one-way flip, celebration but NO XP) · N7 name-keyed setup notes (log screen long-press to add; visible line once set) · B1 warm-up ramp 40/60/80% rounded to loadable plates per gym preset · B5 rest-day recovery card (static catalog, biased away from RECOVERING muscles, off-switch) · B7 monthly Wrapped (in-app only) · B10 widget streak+challenge · B11 level titles to Apex (100+). Migration proven on real SQLite; backup merge rules tested (goals: achieved-wins; notes: latest-edit-wins). **914 tests both variants** (840 → +74). **0 of 15 granted live API calls spent** (N4 judged unit-provable; user's first real generation is the live check).

**Why:** two session-limit kills hit this batch's orchestrator; it resumed from transcript once (worked) and needed ground-truth build nudges twice — the "stops waiting for untracked build children" pattern is now the expected norm, not an incident.
**How to apply:** user's on-device checks — Home card density (N1 suffixes + up to 3 new cards), Wrapped styling, widget at small sizes, warm-up card on the log screen, v19 migration on first launch, N4 generation quality over normal use. Judgment calls open to veto: N7 long-press affordance, ±14 d window, 2.5 kg nudge step, B11 title names.
