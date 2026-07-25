# Feature research 2026-07 — BACKLOG (parked, not briefed)

Sketches only — each needs a user conversation before becoming a brief. Nothing here is dispatched. Grounded in the current codebase (v1.21.0); "exists" claims verified at research time.

## Training optimality

- **B1 — Warm-up ramp suggestions.** For heavy compounds, suggest a concrete warm-up ladder (e.g. ~40/60/80% of today's working weight, rounded to weights actually loadable on the user's 50 mm home setup via the existing `PlateMath`/GymPreset profile), one tap to accept, logged with the existing warm-up chip. High value for a home lifter training alone; parked because it adds UI to the already-busy logging screen the same night two other batches touch it, and ramp philosophy (percentages, set counts) deserves the user's input.
- **B2 — Feed stall/RPE context into generation.** `StallDetector` + `DeloadPolicy` already detect plateaus and trigger deloads, and per-set RPE is logged — but the generation prompt doesn't explicitly receive "these lifts are stalled" or RPE trends. Naming stalled lifts and effort trends in the prompt would let the AI vary/deload precisely instead of inferring. Parked: prompt-quality work is live-gen-iterative (frugal API budget) and overlaps `AiRepository` with R3 + batch-1.
- **B3 — Rep-PR and e1RM-PR types.** Today only heaviest-weight PRs exist; "8 × 60 kg when your best was 6 × 60 kg" goes uncelebrated. Epley e1RM machinery already exists for charts. Parked: changes the PR definition that XP/achievements/challenges hang off — needs a deliberate rebalance decision.
- **B4 — More body measurements.** Waist/arms/chest etc. on the R3 foundation (entity currently weight-only). Parked pending user interest; schema + chart + backup surface area.
- **B5 — Rest-day active recovery.** Optional mobility/walk suggestion card on rest days (recovery panel already knows what's sore). Parked: content source and scope unclear; could feel like nagging — user call.

## Motivation / gamification

- **B6 — Streak insurance ("freeze" token).** One earned grace day (e.g. per Perfect Week) that auto-saves a streak when a planned day is missed. Duolingo-proven, pairs naturally with R1/R4 — but it softens the exact semantics the user just approved, so it's explicitly a user decision.
- **B7 — Year/quarter "Wrapped" recap.** A shareable end-of-period story (total volume, biggest PR, best streak, favorite exercise) from data that all exists (`WeeklySummary`, stats, sets). Fun, self-contained, zero risk to mechanics; parked for scope tonight.
- **B8 — Share cards.** Render a PR / Perfect Week / level-up as a vivid Auros image to share. Parked: user trains at home and has never mentioned social — validate appetite first.
- **B9 — Quest chains / seasonal events.** Multi-week narrative goals beyond the weekly challenges (e.g. 4-week volume ladder). Big design surface; R4's adaptive pool should bed in first.
- **B10 — Home-screen widget upgrade.** `TodayWorkoutWidgetProvider` exists (today's plan); add streak flame + weekly-challenge progress so the phone's home screen nudges without notifications. Straightforward; parked only for tonight's scope.
- **B11 — Level curve past 20.** `levelTitle` tops out ("Transcendent" 20+) and levels slow sharply (sqrt curve); long-term users plateau in title-land. A richer late-game title ladder + per-level flavor is cheap fun — parked as cosmetic.

## Other

- **B12 — Cloud backup OAuth.** Existing gap: `data/cloud` scaffolding is gated on an unconfigured Google OAuth client ID; manual export/import is the live path. Needs a console setup session with the user, not code-first work.
- **B13 — Exercise demo media.** Library detail screens are text-only; the free-exercise-db (with GIFs) was explored in repo history. Parked: asset licensing/size decisions are user calls.
