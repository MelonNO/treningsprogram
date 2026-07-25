---
name: bugsweep-ux-wave1
description: Bug-sweep+UX batch (docs/intake/bug-sweep-ux-2026-06) — 5-wave plan, file-ownership clusters, Wave 1 crash-recovery + integration record
metadata:
  type: project
---

Post-v1.8.0 "bug sweep + UX" batch at `docs/intake/bug-sweep-ux-2026-06/`. 5 waves (SEQUENCE.md). Per-wave verification gate governs (build green + JVM tests + NON-AUTHOR on-device evidence). NO commits to `main` / NO release without the user's OWN word (v1.8.0 ship auth does NOT carry over; coordinator relays are not user authority — see [[relayed-consent]]). Base = commit a201ee0 (v1.8.0).

**Wave 1 file-ownership clusters (co-assign colliding files to ONE worker):**
- **F3** = AiRepository/network seam ALONE. **U1+U3** = Home (HomeFragment/HomeViewModel/fragment_home.xml) + MuscleRecovery model. **S4+F2** = Stats/History (HistoryProgressFragment/HistoryViewModel + WorkoutSetDao). **F1+S6** = gamification (AppDatabase/AchievementDao/GamificationRepository). **S7+S8** = settings/backup + lifecycle (MainActivity + settings fragments + SetupWizard).
- **Two known integration overlaps, both DISJOINT-region (git auto-merges clean):** `WorkoutSetDao.kt` (U1 adds ExerciseSessionRow+observeExerciseSessionRows at top/bottom; S4 adds isWarmup=0 to getWeeklyVolume/getPRsWithDate/observePRsWithDate mid-file). `SettingsViewModel.kt` (F3 adds friendlyAiErrorMessage in generate onFailure; S7 does StateFlow→SharedFlow one-shot-msg conversions + factoryReset table-clears).

**Wave 1 CRASH-RECOVERY (2026-06-24, Pi crashed mid-wave):** WIP survived as uncommitted working-tree in 5 worktrees under `.claude/worktrees/agent-*`. Preserved each to its `worktree-agent-*` branch (exclude `.kotlin/` artifacts). Integrated by cherry-picking all 5 WIP commits onto branch `wave1-integration` (off a201ee0) — both overlaps auto-merged, no conflicts. Stale empty branches a2780045f137f58be / a4e4ed82628ce3c66 hold no unique code.

**Wave 1 bugs I (orchestrator) found+fixed during second-party verification (NOT in worktrees):**
- **MuscleRecovery float-precision bug:** `(rawElapsed:Long / weight:Float).toLong()` rounds at 48h/7d boundaries (Float ~24-bit mantissa, 8-digit ms overflows). With weight=1.0 result MUST equal raw Long. Fix: `if (weight in 0.999f..1.001f) rawElapsed else (rawElapsed / weight.toDouble()).toLong()` in all 3 sites (stateFor/recoveryFraction/remainingRecoveryMs). Caught by 2 failing C4RecoveryStateTest boundary tests — proof the gate works.
- **S5 library dropdown desync:** ExerciseLibraryFragment.setupFilters() reset dropdown text to "All" on rotation while VM kept the filter → list filtered but label lies. Fix: `setText(viewModel.muscle.value ?: anyMuscle, false)`.
- **S8 nav double-tap gap:** worktree only guarded SettingsFragment/SettingsDebugFragment. 6 more fragments had unguarded `findNavController().navigate()` (Profile/SettingsTraining/Library/Program/HistoryRecap/Home). Pattern: `if (findNavController().currentDestination?.id == R.id.<ownDest>) navigate(...)`. HistoryRecap is a ViewPager child → guard against `R.id.historyFragment` (no historyRecapFragment destination exists). Home async callbacks use `if (...currentDestination?.id != R.id.homeFragment) return@...`.

**JVM gate result:** ~345 distinct tests (690 across debug+release variants), 0 fail, after fixes. assembleDebug + assembleRelease both green.

**Wave 1 CONFIRMED 2026-06-24** (on-device verified by non-author ui-test-worker on APK md5 025ccf0c; JVM re-confirmed by orchestrator). All 10 items PASS: F3, U1, U3, S1, S4, F2, F1, S6, S7, S8. Two carry-forward findings (NOT Wave 1 blockers):
- **S7 import is MERGE, not wipe-and-replace** — ExportRepository.importFromJson uses BackupMerger.* (file header: "MERGES… never wipes"; import dialog tells user nothing is deleted). The S7 BRIEF is STALE (says wipe-and-replace) — the in-flight cloud-backup work changed it. Code is internally consistent + correct. Deferred PRODUCT decision (merge vs wipe) for the USER — surface as finding, do NOT silently decide. This OVERRODE my diff-only review (I hadn't read ExportRepository — no worktree touched it); the on-device finding made me read it. Lesson: verify the actual data-path code, not just worktree diffs.
- **Intermittent full-regeneration parse failure** ("No JSON found in AI response") — model-output variance; FAILED GRACEFULLY (F3 safety net working). Response-parse robustness = S3 scope (Wave 2), not a Wave 1 defect. Carry to Wave 2.

**How to apply:** for future waves reuse the cluster map above. The MuscleRecovery Long/Float division class of bug + the nav-double-tap guard pattern are the two recurring fragile areas. See [[warmup-and-muscle-consistency]], [[reference-ondevice-test-harness]], [[ship-handoff]].
