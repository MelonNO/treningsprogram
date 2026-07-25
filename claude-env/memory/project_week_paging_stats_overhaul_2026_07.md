---
name: project-week-paging-stats-overhaul-2026-07
description: "v1.21.0 SHIPPED (2026-07-02, commits 33652bb + eb4da13, tag+release API-verified live): Program week-swipe paging, Stats visual overhaul, per-gym plate profiles (50mm/7kg home default, DB v18), Monday-week unification"
metadata: 
  node_type: memory
  type: project
  originSessionId: 0388e015-1980-48ce-9f66-2988bf3e102b
---

**SHIPPED v1.21.0 (2026-07-02)** — commits 33652bb (audit follow-ups) + eb4da13 (release), tag+release
API-verified live, asset `treningsprogram-v1.21.0.apk` (103.5 MB). Coordinator coded directly (same
user waiver as [[project-design-refine-2026-07]]). 685 tests green (H5 flake passes in isolation).

Delivered:
- **Program week paging**: swipe RIGHT on the week card → past weeks (read-only; `HorizontalSwipeLayout`
  intercepts over clickable day chips; MIN_WEEK_OFFSET=-12; banner + tap-to-return; every mutating
  control hidden because ALL edit paths are keyed on thisMonday() — editing a past week would corrupt
  the current one). ProgramViewModel.weekPlan is now `_weekOffset.flatMapLatest { getPlannedForWeek(mondayForOffset(it)) }`.
- **Stats/History overhaul**: "this week" pulse card (WeekDelta domain, sets/sessions vs last week),
  segmented rep-range capsule + legend (killed off-palette #E91E63/#4CAF50), gradient muscle bars
  (ColorUtils.blendARGB tip), today-cell cyan outline, LinearGradient glow under StrengthChartView
  line, pill TabLayout indicator (tab_indicator_pill + tabIndicatorGravity=stretch).
- **Per-gym plate profiles** (user: home bar is a 50 mm barbell, 7 kg — NOT 20 kg): GymPreset +4
  nullable columns (barWeightKg/dumbbellBarWeightKg/platesCsv/loadableDumbbells), **DB v17→v18**
  additive migration; null = app default = 50 mm home setup (7 kg bar, plates
  20/15/10/5/2/1.45/1.25/1/0.5, loadable dumbbells → readout on dumbbell lifts vs 2 kg handle).
  PlateMath.PlateProfile.from(preset); editor fields in the Gym Presets dialog; seeds: Full Equipment
  Gym = 20 kg/standard/fixed, Home Gym = nulls (home defaults).
- **Monday-week unification**: RecapGraphs volume+frequency now bucket Monday-based logical weeks via
  mondayOfEpochDay (epoch day 0 = Thursday → (d+3)%7); old Thursday-epoch SQL WeekVolume query and
  data class deleted.

On-device checks pending (user): week swipe feel, plate readout values, Stats pulse card, pill tabs.
