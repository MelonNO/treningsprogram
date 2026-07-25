---
name: exercise-recognition-v1-16-0
description: v1.16.0 recognition overhaul — how "unrecognized" works, the resolver family-net + classifier reorder, normalizeName stemming gotcha, and the live-catalog Robolectric harness
metadata:
  type: project
---

SHIPPED v1.16.0 (2026-07-01, commit 51dceeb on main, DB v16→v17, release API-verified live). Made ~130 verbose AI-generated exercise names recognizable. One orchestrator pass, no live API. 617 unit tests (612 baseline + 5 new), assembleDebug + assembleRelease both green.

## The load-bearing mental model (verify in code before trusting)
- **"Unrecognized exercises" list = `ExerciseDbResolver.resolve()` NULL misses**, recorded by `resolutionLog.recordMiss()` and surfaced via `SettingsViewModel.refreshUnrecognized()` → `resolutionLog.getMissReport()`. It is NOT driven by `MuscleClassifier`. To clear a name from that list you MUST make `resolve()` return non-null. Muscle classification is a SEPARATE surface (stats/volume). A name is "recognized" if resolver-matched AND/OR classified.
- `resolve()` order: exact → seedAlias → userAlias → fuzzy → **familyResolve (NEW, v1.16.0)** → LLM-enqueue+recordMiss. familyResolve is placed AFTER fuzzy on purpose: it only rescues true misses, so it never changes any currently-resolving name (zero regression to existing matches).

## The two pattern-level fixes
- **`ExerciseDbResolver.familyResolve(norm)`** — ordered keyword→dbId `when` mapping the normalized name to a representative library entry by MOVEMENT FAMILY (rows/press/curl/tricep/fly/calf/ankle-rehab/carry/etc.), most-specific first. Keyed on movement keywords, not literal strings → future verbose names in the same family resolve too.
- **`MuscleClassifier.fromName`** — reordered by SPECIFICITY so a movement's own keyword beats incidental setup words. Root cause of the old bugs: `fromName` runs on the RAW lowercased name (parens NOT stripped), so "(Seated on Bench)"/"(Held at Chest)"/"Chest-Supported" injected false "bench"/"chest" and the broad Chest rule (rule ~4) fired before Shoulders/Legs/Arms/Core. Fix: shoulder-press, shrug, back-extension, row, Legs, Arms, Core rules all moved ABOVE the generic Chest catch-all. Do NOT reintroduce a Chest rule above the specific-movement rules.

## Non-obvious gotchas (cost real iteration)
- **`ExerciseCatalog.normalizeName` plural-stems** every token ≥5 chars ending in 's' (not "ss"): "tibialis"→"tibiali", "flyes"→"flye", "circles"→"circle". So any keyword you match against the NORMALIZED string must use the STEM (family rule uses `"tibiali"`, not `"tibialis"`). `fromName` operates on the raw (unstemmed) name, so it uses `"tibialis"` there — the two layers need different spellings for the same concept.
- **`"ab "` false-matches `"rehab "`/`"prehab "`** (latent since the F1 fix). Word-boundary it: `startsWith("ab ") || contains(" ab ")`. Exposed when Core moved above Chest.
- normalizeName strips parentheticals `\(.*?\)` but NOT trailing `— qualifier` clauses; familyResolve's substring matching tolerates the leftover qualifier words, so no normalizeName change was needed.

## Decisions (ratified, apply consistently)
- Pure ankle/foot mobility & balance (ankle alphabet/circles, dorsiflexion, inversion/eversion, balance holds, toe scrunch, heel-toe, proprioception) → classifier `""` (EXCLUDED from muscle volume, same as v1.10.2) BUT resolver-matched to `Ankle_Circles`/`Ankle_On_The_Knee` so they leave the unrecognized list. Loaded lower-leg (calf/tibialis/heel RAISE) → Legs. Loaded carries (farmer/suitcase/zercher) → Core. NO new muscle group was introduced (would ripple through colorFor/ALL_FINE_MUSCLES/recovery/stats — too invasive).
- DB bumped v16→v17: data-only `MIGRATION_16_17` re-derives stored `muscleGroup` via shared `MuscleGroupResolver` (byte-identical shape to `MIGRATION_14_15`); reps/weight never touched; idempotent. R2BackfillMigrationTest proves it on real xerial SQLite.

## Live-catalog test harness (reusable)
To run the REAL `ExerciseDbResolver` against the real 873-entry DB in a JVM test: `@RunWith(RobolectricTestRunner) @ConscryptMode(OFF)`, `ExerciseCatalog.initialize(RuntimeEnvironment.getApplication())`, then `ExerciseDbResolver(ExerciseResolutionLog(ctx))`. Robolectric CAN read main assets on this aarch64 Pi (only its SQLite/Conscrypt natives are missing). The 128-name list lives at `app/src/test/resources/unrecognized-exercises.txt` (committed copy; the `docs/intake/overnight-run-2026-07/` source folder was left untouched). See `R2VerboseExerciseRecognitionTest`.
