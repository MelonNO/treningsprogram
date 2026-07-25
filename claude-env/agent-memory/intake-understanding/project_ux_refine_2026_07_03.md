---
name: ux-refine-2026-07-03
description: Self-directed 10-item UI/UX refinement batch at docs/intake/ux-refine-2026-07-03/ (post-v1.25.0, method-delegated no-confirm mode) — Home hierarchy/density, log small-screen, eyebrow+glyph+empty-state consistency, Wrapped close, day-chip fit
metadata:
  type: project
---

Self-directed refinement batch written 2026-07-03 at `docs/intake/ux-refine-2026-07-03/` (INDEX + 10 briefs). User delegated method control ("do some more ui ux refinements you controll the method") → NO confirmation round; every call is an A-XXx assumption.

**Why:** app absorbed 5 feature releases in ~48h (v1.22.0–v1.25.0, now v1.25.0 / main ac9a7e7); this hunts where growth hurt UX (density/consistency/friction/small-screen), not features.

**How to apply:** precedent for method-delegated no-confirm refinement rounds — audit code, write briefs directly, label every judgment A-XXx. Batch = 10 items S/M, nothing L.

Items & clusters:
- **Cluster A (Home, fragment_home.xml+HomeFragment.kt, ONE worker seq):** H1 hierarchy/reorder (raise today's-plan/Start-Workout above Weekly Challenges — primary CTA is currently 3rd card, below fold), H2 body-weight card compaction (always-open weigh-in input → glance+on-demand), H3 recovery card hide-when-empty (only always-on card that shows an empty "all rested" line), H4 body-weight long-press delete needs undo/confirm (currently instant irreversible delete, HomeFragment renderBodyWeightEntries).
- **Cluster B (Log, fragment_log_workout.xml, ONE worker L1→C2):** L1 spec-chip row wrap (3 TextView chips in non-wrapping horizontal LinearLayout in left col sharing width w/ 88dp thumb → weight chip clips ≤320dp), C2 glyph→vector-icon on log/timer controls (⏸⏱⛶).
- **Cluster C (cross-cutting sweeps, WAVE 2 after A/B merge):** C1 eyebrow unification (TWO patterns: dot+TextAppearance.Auros.Eyebrow ~9 layouts incl Home cards+heroes vs dot-less Widget.Auros.Eyebrow ~23 layouts incl Profile/Settings/Log/Stats; A-C1a = standardize in-card to dot-less, hero keeps dot), C3 empty-state voice/weight consistency (bare Home-recent/Profile-PRs vs rich Program empty).
- **Standalone (Wave 1):** W1 Wrapped persistent close (full-screen DialogFragment, only bottom Close after long scroll), P1 week day-chip fit (7× fixed 40dp badge = 280dp floor, clips ≤320dp).

**Build order:** Wave 1 = A, B, W1, P1 parallel; Wave 2 = C1 then C3. HAZARD: fragment_home.xml touched by H1-H4+C1+C3; fragment_log_workout.xml by L1+C2+C1; dialog_wrapped.xml by W1+C1 → sweeps run last.

**Assessed & NOT briefed (told user):** F2 (BW "0 kg" recap) + F3 (History skeleton) already fixed in v1.24.1; F1 keypad-outside-dismiss = likely Waydroid IME artifact (Done/Back work, needs real device); widget already defensively coded. Ratified constraints respected: baselines≠PRs, no rep PRs, no streak freeze, no Wrapped sharing, keypad tap-out = stage-3 item16 spec, Stats balance high-level, gamification stays vivid.

Reusable code facts learned:
- Home content order (post-hero): XP card → [deload] → Weekly Challenges → Today's plan(Start Workout) → [goal-nudge/wrapped-ready/rest-recovery conditionals] → Body weight → Muscle recovery → Recent workouts.
- Today card has 4 states via combine(): Start Workout / Resume / View Recap / Log Freestyle.
- Two eyebrow styles both in themes.xml: TextAppearance.Auros.Eyebrow (inline, paired w/ bg_eyebrow_dot View) and Widget.Auros.Eyebrow (self-contained style, no dot).
- Recovery card renderRecovery early-returns "All muscles rested" TextView on empty (card never hidden).
- BW entry delete = setOnLongClickListener → deleteBodyMeasurement, NO confirm/undo.
- Wrapped = full-screen DialogFragment (Theme fullscreen no-actionbar), root ScrollView, only bottom Close.
- Program week strip = 7× item_day_chip weight=1 but tv_day_abbr fixed 40x40dp.
- Celebration (dialog_workout_result) + Wrapped use emoji section headers (🏆💥🎯) by design = gamification, keep.
