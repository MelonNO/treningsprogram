---
name: feature-research-batch-v1-23-0
description: "R1-R7 gamification/feature batch shipped as v1.23.0 (2026-07-02): key seams (StreakPolicy, NotificationGate, ChallengeCatalog, AchievementCatalog, BeatTarget) + ship gotchas (100MB asset upload timeout; verify remote state before re-creating a release)"
metadata:
  type: project
---

Feature-research batch R1-R7 SHIPPED as **v1.23.0** (2026-07-02, release commit bd35ad9 on main, R7 commit e6b4a46, release id 348139409, asset 103546955 bytes, API-verified live). 803 unit tests green both variants (was 712). DB unchanged v18. 0 live API calls needed — R3's AI awareness is prompt-context only, unit-verified.

**Seams created (reuse these, don't reinvent):**
- `domain/StreakPolicy` — schedule-aware streak walk (rest/gap neutral, MISSED breaks), shared verbatim by GamificationRepository (live) + StatsRecomputer (backup replay); display freshness via `applyStreakFreshness()` in MainActivity.onStart AFTER autoLogRestDays.
- `notify/NotificationGate` — pure fire-time conditions for all 4 notification types; receivers gather inputs, gate decides. Per-type prefs in PreferencesManager (streak warning default ON, others per A-N1); ReminderScheduler cancels eagerly on toggle-off.
- `domain/ChallengeCatalog` — 26 templates (9 adaptive), deterministic weekly draw over the ELIGIBLE subset only; targets frozen at draw time. Perfect Week: once-per-week guard = `perfectWeekAwardedWeekStart` pref; recompute counts perfect weeks from planned_exercises.
- `domain/AchievementCatalog` — tier+category meta for ALL ~200 achievements; `AchievementCatalogTest.definedSetIsThePredefinedList` FAILS if someone adds an achievement without meta (deliberate tripwire).
- `domain/BeatTarget` — R7 PR-preview logic; `BeatTargetTest` locks agreement with `GamificationRepository.isWeightPr`. ViewModel chip derives from combine(currentExercise, sets); flash dedup passes prior in-session working max so resumed sessions never re-flash.

**Why:** second release of the 2026-07-02 staged overnight run; R7 was finished by orchestrator #2b after a session crash left it partial (duplicate repo method = compile error, unwired checkPrPreview, no UI, no tests — audit-before-trust paid off).

**How to apply (ship gotchas):**
- The release APK is now ~100 MB — the GitHub asset upload needs `--max-time 560` (default 2-min Bash timeout killed curl mid-upload; empty assets list after = nothing partial landed, safe to retry).
- If an external status snapshot claims tag/release missing right after you pushed/created them, RE-VERIFY via `git ls-remote` + `GET /releases/tags/<tag>` before re-creating — my push/create HAD succeeded and the snapshot had raced; re-creating an existing release would have errored or duplicated.
