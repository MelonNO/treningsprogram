---
name: stage3-ux-batch-2026-07
description: Stage-3 16-item UX batch at docs/intake/stage3-ux-2026-07/ (asleep-user, assume-and-log mode) — Recap overhaul cluster, date-range pickers, Profile de-dup, heatmap tap-through, keypad dismiss; all 16 briefed, none skipped
metadata:
  type: project
---

Confirmed 2026-07-02: 16-item batch at `docs/intake/stage3-ux-2026-07/`, STAGE 3 of a staged overnight run (release 1 = rest-ux, release 2 = feature research, stage 4 = full test). **Asleep-user mode:** "assume and log it in your report … if anything is too unclear skip it and note it" — all 16 grounded in code, none skipped, every call a labelled A-XXx assumption.

**Why:** user batch-dumps UX polish before bed and accepts assumption-driven intake when unavailable.

**How to apply:** this assume-and-log mode is now precedented for overnight batches; keep the A-label pattern. Clusters: H1 Recap 3→9→14→10 (one worker, order mandatory), H2 = 12+13+1 (13 and 1 share HistoryProgressFragment), H3 = 11+15 (Stats), H5 = 2 skeletons LAST, P = 4+5 (post-R5 Profile), T = 6+8, standalone 7 and 16.

Reusable code facts learned:
- History bottom-nav tab = 4 sub-tabs: Recap | Stats | Progress | History (HistoryPagerAdapter). Recap auto-selects the latest session; has openRecap(sessionId)+highlightMuscle mechanism (RecapTargetViewModel) used by Home recovery taps — reused for heatmap tap-through (item 11).
- VolumeHeatmap.Grid = coarse-muscle rows × week columns (Monday starts); cells aggregate a week → "session" taps need a mapping decision (A-11a: most recent that week).
- StrengthPoint already carries bestReps → BW reps chart (item 1) is data-ready.
- Achievement has unlockedAtMs but NO sessionId → per-session attribution (item 14) is timestamp-based, forward-only linkage allowed (A-14a).
- Exercise db ships 2 frames/exercise; workout surfaces alternate them (ExerciseInfoBottomSheet Runnable); library detail shows only frame 0 (item 7 gap).
- Weight keypad = inline layoutWeightKeypad visibility panel (not a dialog) — outside-tap dismiss is manual hit-testing territory (item 16).
- Profile 4-stat grid + all-time topPrs (getTopPersonalRecords) were the item 4/5 targets.
