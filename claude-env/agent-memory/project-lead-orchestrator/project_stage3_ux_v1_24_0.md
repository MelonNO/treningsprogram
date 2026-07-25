---
name: stage3-ux-v1-24-0
description: "Stage-3 16-item UX batch shipped as v1.24.0 (2026-07-02): Recap overhaul seams, calendar range filters, non-obvious gotchas (0-kg-excluded strength history, session-id-unsafe backups, missing-changelog pattern)"
metadata:
  type: project
---

Stage-3 UX batch SHIPPED as **v1.24.0** (2026-07-02, commit fba7c08, tag/release/asset API-verified;
830 tests both variants; DB unchanged v18; 0 API calls).

**Why:** third stage of the 2026-07-02 overnight staged run; 16 outcome-only briefs at
`docs/intake/stage3-ux-2026-07/` (assumptions A-01a…A-16a stood unvetoed).

**How to apply — seams and non-obvious findings for future work:**
- `WorkoutSetDao.getStrengthHistory` deliberately filters `weightKg > 0` — bodyweight work is
  INVISIBLE to the Progress weight chart. The reps chart (item 1) needed the parallel
  `getSessionRepsHistory` (no weight filter, independent MAXes). Don't "fix" the weight query.
- Achievement→session attribution (Recap "Earned this session") is TIMESTAMP-based
  (`domain/SessionEarned`, window = start..start+duration+15min grace, omit-when-ambiguous), NOT a
  sessionId column: backup restore re-inserts sessions with NEW row ids, so persisted session ids
  would break cross-restore, while unlockedAtMs survives merges (earliest-wins) and is only ever
  stamped at completion. Editing a session's date breaks its attribution (accepted, omitted not
  misattributed).
- Recap muscles card + Home recovery panel now share ONE taxonomy via
  `MuscleClassifier.finerMusclesFor` (`domain/FineMuscleVolume`, weighted whole-set rounding).
  `HistoryRecapFragment.exercisesHittingMuscle` also accepts BROAD labels (heatmap rows) via
  `broadGroupFor` roll-up.
- Shared calendar-range seam: `domain/DateRangeFilter` (null = All, inclusive logical days,
  in-memory only per A-12a/13a) used by both History log and Progress; `filteredSessions` is
  NULLABLE (null = loading → skeleton) — don't "simplify" it back to non-null. **UPDATE
  v1.24.1: the original double-stateIn version of this chain never bound on-device (stage-4
  F3) — it is now single-layer with the filter in `domain/HistorySearch`; see
  [[stage4-patch-v1-24-1]] before touching it.**
- Log-screen root is now `ui/log/TouchObservingLinearLayout` (dispatch-observing for keypad
  outside-tap dismiss, pass-through). Any future full-screen overlay work on that screen should
  reuse `onDispatchDown` instead of adding another interceptor.
- `VolumeHeatmapView` consumes touches only when `onCellTap` is set; cell hit-testing mirrors
  onDraw geometry exactly — keep them in sync if drawing changes.
- **Changelog gap pattern:** v1.23.0 shipped WITHOUT its `ui/common/Changelog.kt` entry (release-2
  orchestrator missed it); I backfilled it in v1.24.0. CHECK Changelog.ENTRIES has an entry for the
  new versionCode as part of every release — the What's-new sheet and About screen key off it.
- Release mechanics reconfirmed: ~103 MB asset uploads fine with `curl --max-time 560` (took well
  under); token via `git credential fill`; release build ~5 min on the Pi (R8 is the long pole);
  stale-APK discipline matters — the outputs dir held v1.23.0's APK until the new build landed
  (verify output-metadata.json versionCode + fresh timestamp before upload).
- H5DispatcherTest flake fired again (first full `test` run, release variant) and passed on
  re-run — consistent with [[reference_flaky_tests]].
