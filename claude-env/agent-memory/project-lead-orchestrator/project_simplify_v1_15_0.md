---
name: project-simplify-v1-15-0
description: v1.15.0 codebase-simplification release (Stage 3 overnight run) — what was dead vs the framework-wired "looks-dead-but-isn't" traps
metadata:
  type: project
---

Stage ③ of the 2026-07-01 overnight run: pure dead-code/dead-resource cleanup, **zero behavior change**, shipped as **v1.15.0** (versionCode 51, DB unchanged v16, commit c65735d on main). No API calls used.

**What was provably dead and removed (13 files, ~660 LOC):** two never-instantiated RecyclerView adapters (SessionAdapter, ActiveExerciseAdapter) + their only-referenced-by-them item layouts (item_session, item_active_set); layouts item_program_exercise + dialog_add_set; menu top_menu; drawables ic_log + ic_settings (ic_settings was only in the dead top_menu); 15 unused strings + color primary_variant; two dead private vals (goals/experiences) in SettingsAiFragment. After this pass lint UnusedResources = 0.

**Why:** user wants the code + filesystem as simple as possible without touching any user-facing behavior.

**How to apply / traps for the NEXT cleanup (this codebase specifically):**
- A naive "zero cross-file reference" grep FALSE-flags these — do NOT remove them: Hilt modules (AppModule, DatabaseModule, BackupBindingsModule — wired by generated code only); Gson API models (Wger* in WgerApi.kt, populated by reflection, defaults like `= emptyList()`); same-file-only types/functions (MatchSource/ResolveResult in ExerciseDbResolver, GenerationResult in AiRepository, ProfileUiState, top-level `mondayOf`/`dayOfWeekOf` used only inside WorkoutRepository.kt).
- Wger API IS a live feature (used by ProgramFragment + LogWorkoutFragment) — keep. Cloud/Drive backup is gated but UI-reachable — keep (removing = feature removal).
- The kotlinc analyzer runs but did NOT surface any unused private funcs / commented-out blocks — this codebase is already clean of those categories; don't expect volume.
- Removing an actually-referenced string/resource FAILS assembleDebug (aapt) — string/resource removal is self-verifying via the build, in addition to grep+lint.
- Verified via: assembleDebug + assembleRelease (R8/lintVital) BUILD SUCCESSFUL, 612 tests unchanged, lint 0 errors.

**Latent items flagged (NOT fixed — out of scope for cleanup):** ExerciseInfoBottomSheet.kt:83 has an always-true redundant `dbId != null` check (cosmetic, not a bug); cloud backup uses the deprecated Google Sign-In API (GoogleDriveAuth / SettingsBackupFragment).
