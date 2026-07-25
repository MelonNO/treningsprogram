---
name: settings-ux-batch-2026-07
description: 12-item Settings/UX batch (branch settings-ux-batch-2026-07) — IA restructure, per-muscle recovery, start-workout merge/append, generation preserve-logged; rec v1.18.0, DB stays v17
metadata:
  type: project
---

12-item Settings/UX batch, briefs at `docs/intake/settings-ux-batch-2026-07/`. Built by the
orchestrator ITSELF (single-tree, sequenced commits — NOT parallel workers; too many shared files:
PreferencesManager/MainActivity/ProgramFragment/nav_graph). Branch `settings-ux-batch-2026-07` off
main `b270d4b` (v1.17.1). 4 commits (12→ClusterA→10→8/9/11). 651 tests green, APK clean, **DB stays
v17 (no schema change — item 12 reuses the existing `rpeLabel` column)**. Recommended ship **v1.18.0**
(MINOR). Coordinator ships; orchestrator did NOT release.

**Why:** post-v1.17.1 UX polish + two real bugs (item 3 nav, item 9 overwrite-logged) + one large
data-mutating feature (item 10).

**How to apply — the load-bearing seams/decisions (grep before trusting):**
- **Item 2 auto-rebalance default ON:** just flipped `PreferencesManager.autoRebalanceEnabled` getter
  default false→true. Safe because the key is written ONLY on explicit user toggle → absent key =
  "never chosen" = ON; explicit OFF preserved. NO sentinel/migration. New App Settings switch inits
  BEFORE attaching its listener so display never writes.
- **Item 3 Profile-returns-to-root:** `MainActivity` overrides `bottomNav.setOnItemSelectedListener`
  (profile → popBackStack(profileFragment,false) else onNavDestinationSelected; other tabs delegate to
  NavigationUI unchanged) PLUS a `setOnItemReselectedListener` — belt-and-suspenders because I could
  NOT test on-device which of select/reselect fires. **UNVERIFIED on device.**
- **Item 4/1:** new `SettingsAppFragment`/`fragment_settings_app.xml` holds day-boundary (24h labels,
  no AM/PM anywhere — it was the app's ONLY AM/PM surface) + auto-rebalance switch. Day-boundary
  removed from SettingsTrainingFragment; auto-rebalance removed from ProgramFragment options dialog.
- **Items 5/6/7 IA:** Debug now under Backup&Data, Coach Summary under AI&Program; top order Training
  Profile→AI&Program→Exercise Library→Backup&Data→App Settings→About. `MainActivity.destToTab` maps
  settingsAppFragment→profile.
- **Item 10 (highest risk, data-mutating) — reopen-and-append design:**
  - Plan layer: `DayMovePlanner.applyMoveToTarget` (pure, unit-tested) — APPEND moved rows after
    today's existing rows if today has any isLogged row (kept verbatim), else REPLACE. `commitDayMove`
    rebuilds target day from it.
  - Session layer: `WorkoutRepository.startAppendableTodaySession()` REOPENS today's logical-day
    completed workout (isCompleted=false) so new sets append into the SAME session → one History
    entry. **CRITICAL:** abandon of a reopened session must `recompleteSession` (restore), NEVER
    delete — else the original workout is destroyed (handled in LogWorkoutViewModel.abandonSession via
    `isReopenedAppend`). Duration = base + segment.
  - "Start Workout on another day" already did the implicit move via `resolveMoveSource` (from the
    v1.14.0 batch); item 10 just removed the second button + added the append + relaxed the today-
    logged gate. [A10-2] (a logged OTHER day is not a redo candidate) is NOT enforced — Start Workout
    is still shown on logged days (pre-existing behavior); flagged as residual.
- **Item 8 GenerationState:** new `domain/GenerationState` @Singleton (fullGenerating + status). Written
  by `SettingsViewModel.doGenerate` (begin/update/end), observed by ProgramViewModel→ProgramFragment
  (new `card_full_generating`). Additive to Settings status; no auto-switch. Auto-gen in MainActivity
  deliberately does NOT set it (out of scope).
- **Item 9:** `SettingsViewModel.doGenerate` now uses RegeneratePlanner.loggedDays/lockedExercises +
  `savePlanPreservingLoggedDays` (the SAME keep-logged path as ProgramVM.runRebalance) instead of the
  full-fresh `savePlan`. Empty week still = full first generation. MainActivity auto-gen left on
  savePlan (only runs when week empty — can't overwrite logged, per brief scope).
- **Item 12 (recovery) — 12a/12b decided:** per-fine-muscle base table `MuscleRecovery.baseRecoveryMsFor`
  (12b=fine-grain; Quads/Hams/LowerBack 72h, Glutes/UpperBack 60h, **Chest kept 48h to preserve C4/U1
  tests**, small muscles 36h, Cardio 24h) × `effortMultiplier` (Hard 1.30, Easy 0.75, Moderate+blank
  1.0 — **12a: blank=medium**). Effort = MAX RPE per (exercise,session) via a new `effortLevel` column
  on `ExerciseSessionRow` (DAO GROUP BY + CASE). Synergist weight scaling KEPT. Existing recovery
  functions got a defaulted `recoveringWindowMs` param so C4/U1 stay green with the 48h default.

**Residuals (user/on-device):** item 3 nav one-tap, item 10 session reopen/append/abandon-restore, and
item 8 loading animation are UNVERIFIED on device (Waydroid dormant [[feedback_always_skip_waydroid]]).
Item 10 abandon-of-append KEEPS any sets added in the segment (prioritizes never losing the original).
No live API calls made.
