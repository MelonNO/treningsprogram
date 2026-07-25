---
name: project-treningsprogram
description: "Android adaptive workout planner app in /home/migul/treningsprogram — Kotlin, MVVM, Room, Hilt, Claude AI API. Build env on Pi. Current DB version 13; latest release v1.8.0."
metadata: 
  node_type: memory
  type: project
  originSessionId: 048af290-866b-45c1-8e3b-1940b1eb3acf
---

Adaptive workout planner and logger Android app.

**Why:** User wants AI-driven training that adapts weights, reps, and exercise selection based on previous sessions.

**How to apply:** Build IS possible on this Pi — use `./build.sh` (sets JAVA_HOME, ANDROID_HOME, QEMU_LD_PREFIX). Java 21, Android SDK 34, x86_64 QEMU emulation for aapt2 are installed. Do NOT use `./gradlew` directly.

Stack: Kotlin, MVVM, Hilt DI, Room, Jetpack Navigation, Retrofit → Anthropic API (`claude-sonnet-4-6`), EncryptedSharedPreferences for API key.

Package: `com.migul.treningsprogram`

## Key files

- `data/repository/AiRepository.kt` — builds structured scientific prompt from workout history, calls Claude, parses JSON. Also contains `getOnboardingQuestions()` and `validateProgram()`.
- `data/repository/WorkoutRepository.kt` — all DB ops; `thisMonday()` / `currentDayOfWeek()` helpers at file scope
- `data/repository/GamificationRepository.kt` — XP/level/streak/achievement/PR processing on workout complete
- `data/repository/WgerRepository.kt` — checks `ExerciseCatalog` first (bundled, reliable), falls back to live wger.de search for unknown exercises
- `data/preferences/PreferencesManager.kt` — stores API key, days/week, goal, experience, session duration, gym preset ID, `lastAutoGenerateWeek`, `separateCardioDays`, `lastGenerationAttemptCount`, `hasCompletedOnboarding`, `onboardingContext`, `wizardEquipment`, **`injuries`**, **`priorityMuscles`** (comma-separated), **`dislikedExercises`**
- `ui/setup/SetupWizardFragment.kt` — 6-step first-launch wizard (Goal, Schedule, Preferences, Equipment, API Key, Training Profile)
- `ui/setup/SetupWizardViewModel.kt` — wizard VM; reads equipment/injuries/priorities/dislikes from prefs before generation
- `ui/onboarding/OnboardingBottomSheet.kt` — legacy bottom sheet still used from Settings when `hasCompletedOnboarding == false`

## DB schema — version 8

Entities: Exercise, WorkoutSession, WorkoutSet, PlannedExercise, UserStats, Achievement, GymPreset, BodyMeasurement  
Migration chain: 1→2→3→4→5→6 (BodyMeasurement)→7 (WorkoutSet adds `isWarmup`, `rpeLabel`)→**8 (deletes "No Equipment" gym preset — merged into wizard "Bodyweight only" option)**

## Wizard flow (6 steps, as of session 5)

Steps: 0=Fitness Goal, 1=Training Schedule, 2=Preferences, 3=Equipment, 4=Connect Claude (API key), 5=Training Profile → then Generating screen (index 6)

**Step 3 Equipment:** Shows existing GymPreset rows as selectable cards + hardcoded "Bodyweight only" (id=-1L). "No Equipment" preset removed (same as bodyweight). "+ Create new preset" button at bottom.

**Step 5 Training Profile (new in session 5):** Hardcoded form — NO AI-generated questions. Three inputs:
1. Injuries / limitations (TextInputEditText, multiline)
2. Priority muscle chips (ChipGroup multi-select: Chest, Back, Shoulders, Arms, Legs, Glutes, Core)
3. Exercises to exclude (TextInputEditText, multiline)

On advance (step 5): saves injuries/priorityMuscles/dislikedExercises to prefs, then calls `viewModel.generateProgram()`. Button label is "Generate My Program" on step 5.

`SetupWizardViewModel` no longer has `loadQuestions()`, `questions`, `isLoadingQuestions`, or `questionsError` — all AI-question loading from wizard is removed.

## AI generation flow

### generateAdaptedProgram() signature
```kotlin
suspend fun generateAdaptedProgram(
    daysPerWeek, goal, experience,
    sessionDurationMinutes, equipment, equipmentNotes,
    separateCardioDays,
    injuries,         // hard constraint: no aggravating exercises
    priorityMuscles,  // extra weekly sets on these groups
    dislikedExercises, // NEVER include these
    onboardingContext  // legacy, still used from Settings bottom-sheet path
)
```

Prompt now has 3 extra sections (injected only when non-blank):
- `INJURIES AND LIMITATIONS (HARD CONSTRAINTS)` — two rules: (1) no exercises that aggravate the injury; (2) include 1-2 light rehab/strengthening accessories per session where safe and commonly recommended (e.g. rotator cuff for shoulder, hip abductors for knee, bird-dogs for lower back)
- `PRIORITY MUSCLE GROUPS` — rule: +2 sets/week, train ≥2×/week
- `EXERCISES TO EXCLUDE` — hard NEVER rule

### Generation with validation loop
Max 3 attempts (`MAX_GENERATION_ATTEMPTS = 3`). `validateProgram()` is a second Claude call (reviewer). Returns `GenerationResult(exercises, attemptCount)`.

### Auto-generation
`MainActivity.checkAndAutoGenerateWeeklyPlan()` — passes injuries/priorityMuscles/dislikedExercises from prefs alongside other profile fields.

## Gamification layer

- **XP formula:** 50 base + 5/set + 30/PR + challenge bonuses
- **Level formula:** `floor(sqrt(xp/200)) + 1`; titles from Rookie (L1) to Transcendent (L20+)
- **Streak, Achievements (14), Weekly challenges (3/week, 12 templates), PRs** — all in GamificationRepository

## Tab order (bottom nav)

Home → Program → Stats (History) → Profile

## Settings screen

Sections: TRAINING PROFILE (days/week, goal, experience, session duration, cardio toggle) → AI GENERATION (API key, Generate Now, attempt counter) → EQUIPMENT (gym preset selector) → **TRAINING PROFILE card** (injuries textarea, priority muscles chips, disliked exercises textarea) → Save button → DATA (reset workouts).

The Training Profile card fields are loaded from prefs on view creation and written back on Save.

## Signing / release

Release keystore at `keystore.jks` (gitignored). Config in `keystore.properties` (gitignored). Both at repo root. versionCode=2, versionName="1.1". Do NOT push new releases unless user explicitly asks.

GitHub releases: v1.0.0 (unsigned, deleted), v1.1.0 (signed), v1.2.0 (onboarding feature).

## Waydroid / ADB testing

ADB over TCP: `adb connect 192.168.240.112:5555` (Waydroid device). Screen: 1920×1044, density 180.  
**uiautomator is required** to get real element coordinates — `adb shell uiautomator dump /sdcard/ui.xml` then pull and parse bounds. The Waydroid display uses windowed mode; before testing maximize the app window using the Maximize button found in the UI dump. Pixel coordinates in the ADB screencap match the uiautomator bounds directly.  
ADB key auth: `sudo tee /var/lib/waydroid/data/misc/adb/adb_keys` with `~/.android/adbkey.pub`.  
Install: `adb -s 192.168.240.112:5555 install -r <apk>` (NOT `./build.sh installDebug` which hangs over ADB TCP).

**Waydroid gotchas:**
- If container shows FROZEN in `waydroid status`, unfreeze it with `waydroid app launch com.android.settings` then wait ~8s
- `adb install` can hang silently — if no output after ~30s, kill the adb processes and retry
- Never run two concurrent `adb install` commands (they clobber each other)
- **Text input**: `adb shell input text` sends text to wherever the keyboard is active. If the soft keyboard is visible, `adb shell input tap <x> <y>` taps on the keyboard keys, not the UI coordinates. Dismiss keyboard with `KEYCODE_ESCAPE`, NOT `KEYCODE_BACK` — Back navigates away from the fragment
- `adb shell input text "foo bar"` — spaces work if quoted; `%20` is sent literally
- Main activity name: `com.migul.treningsprogram/.MainActivity` (no `ui.` prefix)

## Rest timer subsystem (session 8)

Timer logic moved out of `RestTimerBottomSheet` into:
- `ui/log/RestTimerManager.kt` — Hilt singleton, coroutine countdown, StateFlow state, starts/stops service
- `ui/log/RestTimerService.kt` — ForegroundService, live countdown notification, vibrates on finish (`dataSync` type)
- `RestTimerBottomSheet` now just displays from manager; swipe-down hides without stopping; "Skip" stops+hides
- Swipe-up gesture on `LogWorkoutFragment` root shows/starts timer

## Exercise catalog (session 8)

- `data/ExerciseCatalog.kt` — bundled `Map<String, CatalogEntry>` covering all 36 default exercises (imageUrl from free-exercise-db GitHub raw, instructions, equipment, muscleGroup). No runtime network call for catalog data.
- `WgerRepository.getExerciseImageUrl()` now checks catalog first, falls back to live wger.de search.
- `data/CalisthenicsProgressionMap.kt` — 6 progression families (push-up, pull-up, squat, dip, core, row), ordered easiest→hardest. `looksLikeCalisthenics()` detects by exact match or keyword.
- `ui/log/ExerciseInfoBottomSheet.kt` — shows exercise instructions from catalog on tap of exercise name.

## Export/import (session 8)

- `data/repository/ExportRepository.kt` — exports all user data to versioned JSON (`schema_version: 1`). API key excluded. Imports by clearing + re-inserting.
- Settings: "Export Backup" (FileProvider + ACTION_SEND) and "Import Backup" (ACTION_OPEN_DOCUMENT) buttons added.
- `res/xml/file_provider_paths.xml` and FileProvider declaration in manifest added.

## Key fixes applied (session 8) — all 15 issues from bugfix brief

1. **Resume button** — HomeFragment navigates to `active.id` (was creating new session)
2. **Rotation** — `configChanges="orientation|screenSize|keyboardHidden"` on MainActivity; ViewModel `loadSession`/`resumeSession` guards against re-init
3. **Weight persistence** — `_savedWeights`/`_savedReps` maps in ViewModel; priority: saved > last-actual > AI; saved on Next/Back/Skip
4. **Timer crash (08)** / **Awesome! crash (15)** — `CountDownTimer` eliminated; `isAdded` guards on `showResultDialog` and click handler
5. **Admin time** — `ADMIN_TIME_PER_EXERCISE_SECONDS = 60` in ProgramFragment; `exerciseEstimateSeconds()` helper
6. **Equipment filter** — AiRepository prompt has explicit AVAILABLE/NOT-AVAILABLE/FORBIDDEN sections
7. **Calisthenics swap** — `swapCurrentExercise()` in ViewModel; swap button in LogWorkoutFragment for calisthenics

## AndroidManifest additions (session 8)

`POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `VIBRATE` permissions; `RestTimerService` (dataSync); `FileProvider`.

## AI prompt (session 8)

`buildPrompt()` now has three equipment sections: AVAILABLE, NOT AVAILABLE — STRICTLY FORBIDDEN (names missing categories including bands), FORBIDDEN EXERCISE LIST (~15 band exercises added when bands absent). Band substitutions conditional on `hasBands`.

## Build status

Current release: **v1.8.0** (2026-06-24, commit `a201ee0`, tag pushed, signed APK `treningsprogram-v1.8.0.apk` md5 `b545f6115086c18f1ce137a13f205391` on GitHub; **DB version 13**). Prior: v1.7.0 (commit `db495ec`, versionCode=35); v1.6.2 = versionCode 33.

## v1.8.0 — 8-feature batch (2026-06-24, released)

Autonomous overnight build of the 2026-06 feature batch (4 verification waves, each on-device verified; the Pi crashed mid-Wave-1 and the pipeline was resumed). See [[project-feature-batch-2026-06]] for full per-wave PASS evidence + residuals. Features: **C1** estimated-1RM/PR trends (RecapTrends, Epley, warm-ups excluded), **C4** recovery/freshness view (Home), **E3** exercise library browser (full 873-entry bundled catalog, search+filter, via Settings), **B3** plateau/stall detection (`domain/StallDetector.kt`, feeds a STALLED LIFTS prompt block), **B2** program-change rationale (response-side `rationale` field, persisted per-week on planned_exercises, shown on Program tab), **B1** weekly AI coach summary (auto/weekly, dedicated screen via Settings "Coach Summary"), **E2** named programs + periodized mesocycles + stall-triggered deload (`Program` entity, active-program drives Home/Program, reuses B3), **E1** manual program editing (edit/add/delete/reorder on the active program, `domain/DayPlanEditor.kt`). DB 10→13 (MIGRATION_10_11 rationale col, 11_12 weekly_summaries, 12_13 programs+programId with clean backfill). Saved programs added to backup/export (envelope v2→3, mergePrograms). 215 JVM tests green (debug+release); on-device UI verified (Waydroid/Maestro). Residuals (non-blocking): B1 summaries not yet in backup set; E2 live-AI deload week not observed on Waydroid (pre-existing 120s OkHttp timeout, env not defect); pre-existing legacy Progress "PR" widget counts warm-ups ([[project-pr-widget-warmup-bug]]).

## v1.7.0 — Cloud backup + manual transfer parity (2026-06-24, released)

Automatic cloud backup engine (Google Drive `appDataFolder`) + greatly improved manual Export/Import. `data/backup/` (BackupMerger, BackupScheduler, StatsRecomputer, BackupMigrations, PreferencesMerger, BackupModels) + `data/cloud/` (GoogleDriveAuth, DriveBackupClient via `NetHttpTransport`, DriveBackupUploader) + `di/BackupModule.kt`. Complete data capture (all entities incl. exercise library + gym presets + wider prefs; **API key always excluded**), cross-version forward-migration (v1 manual export → v2), MERGE-based restore (union sessions/sets/measurements + achievements, newest-plan-per-week, stats/XP/streak recomputed, per-setting reconciliation) — replaces wipe-and-replace. 117 JVM tests pass; on-device UI NOT verified.
- **CLOUD IS GATED/DORMANT:** needs a real Google Web OAuth client ID in `app/src/main/res/values/backup_config.xml` (placeholder `REPLACE_WITH_WEB_CLIENT_ID`); until set, `GoogleDriveAuth.isConfigured`=false and auto-backup safely no-ops. User chose NOT to configure OAuth — the live, shipped path is the **manual** Export/Import, which has full parity (same engine).
- Known minor: daily-challenge XP bonus not replayable in post-merge stats recompute (documented small under-count after cross-device merge).

## v1.6.2 — QA pass: stats accuracy & locale-safe week keys (2026-06-23, released, commit 3d2e7a3)

Released v1.6.2 (signed assembleRelease, APK `treningsprogram-v1.6.2.apk`). **No DB change (still v10).** Full QA pass via project-lead-orchestrator + 3 diagnostic agents; 86 JVM tests / 0 fail, lint 0 errors; UI-visible fixes on-device VERIFIED (Waydroid+Maestro, [[reference_ondevice_test_harness]]). Several agent "critical" claims (wizard data-loss, export crash, rotation loss, non-deterministic reps) were checked and **rejected as non-bugs**.
- **F1** `data/MuscleClassifier.kt` (new, keyword-based) + wired in `LogWorkoutViewModel`: swapped/added/custom exercises (e.g. "Archer Push-Up", "Pistol Squat") were stored with blank `muscleGroup` and silently dropped from recap, Stats muscle-volume, and muscle challenges; now classified.
- **H2/H4** `WorkoutSetDao`: added `isWarmup = 0` to total-sets/volume, rep-range, muscle-volume, and `getStrengthHistory` queries — warm-ups were inflating Stats/Profile totals and distorting the strength chart.
- **F5** `ProfileViewModel`: Volume/Sets/Top-PR tiles made reactive (`refreshSetTotals()` per `userStats` emission); were stale until the VM was recreated.
- **F2** `LogWorkoutFragment`: progress bar now `(idx+1)*100/size` → reaches 100% on the final exercise.
- **F3**: consolidated the two divergent UI muscle-badge classifiers onto `MuscleClassifier` (`displayName()` / `colorFor()`); `ProgramFragment` + `LogWorkoutFragment` delegate (fixes e.g. "Romanian Deadlift" badge differing Back vs Legs).
- **G2 (locale-safe week keys)**: `autoGenWeekKey()` at file scope in `WorkoutRepository` (= `"wk-${thisMonday()}"`), routed through all **4** auto-gen-key sites (MainActivity reader+writer, SettingsViewModel, SetupWizardViewModel) — they must match or auto-gen re-fires every launch. Separately, `isoWeekKey()` top-level in `DailyChallengeManager` (Locale.ROOT + Monday / min-4-days, keeps `yyyy-'W'ww` so byte-identical for Monday-first locales → no challenge re-roll) for the weekly-challenge rotation key.
- Tests added: `MuscleClassifierTest`, `AutoGenWeekKeyTest`, `DailyChallengeWeekKeyTest`.
- **NOT fixed:** achievements orphan-rows ([[project-achievements-orphan-rows]], user said don't); minor deferred edge cases (Home `todayCompleted` uses session start date; one animation-callback guard; wizard custom-duration staleness on an unusual back/forward path).

## v1.6.1 — Fix2 batch + recap pacing (2026-06-23, released, commit 03734cf)

Released v1.6.1 (signed assembleRelease, APK `treningsprogram-v1.6.1.apk` on GitHub). DB bumped to **v10** (`loggedAtMs` per-`WorkoutSet` column + `MIGRATION_9_10`).
- **Fix2 items, all on-device VERIFIED** via Waydroid+Maestro (see [[reference_ondevice_test_harness]]): (1) session persistence — resume to most-recently-logged set's exercise + per-session draft restore (`prefs.workoutDraftJson`), fixes lost ex-1 sets + reverted weights; (2) calisthenics swap overwrites all fields; (3) no PR on first-ever performance (`GamificationRepository.isWeightPr`); (4) Skip button removed (Next still advances); (5) explanation window shows AI "Coach's note" + DB info (`ExerciseInfoBottomSheet`); (6) quick-access menu `QuickAccessBottomSheet` (tap progress bar; jump/add/Add-anyway, insert-after-current + renumber).
- Also shipped: **recap pacing** (work/rest/idle + rest-adherence section, `SessionPacing` in `SessionRecap.kt`, `HistoryRecapFragment`) — completes a v1.6.0 deliberate gap; built+compiles but was NOT in the verification mandate (unverified). NOTE: swap & added-exercises are in-memory (ViewModel `_guidedPlan`), revert to saved DB plan after process kill by design; logged sets always persist.

## Session 2026-06-23 — v1.5.18 → v1.6.0

- **v1.5.18:** bodyweight log moved from History/Progress to the **Home** screen (`HomeFragment` + `HomeViewModel` now own `BodyMeasurementDao`); achievements overhauled for variety.
- **Achievements expanded to 200** predefined (`AppDatabase.PREDEFINED_ACHIEVEMENTS`), checked in `GamificationRepository.checkAchievements` (params: stats, setCount, exerciseCount, totalVolumeKg, sessionPrCount). Includes per-session-PR and cross-stat "combo" mechanics. See [[project-achievements-orphan-rows]] for a known display bug.
- **v1.5.19:** Profile achievements made **collapsible**; locked achievements render as masked 🔒 "shadow" rows ("???", no unlock criteria); only **3** locked shadows shown below the unlocked ones.
- **v1.6.0 — Recap & Trends** (spec: `Change docs/recap-trends-feature-spec.md`): new **Recap** sub-tab under the History/Stats tab (now 4 sub-tabs: Recap/Stats/Progress/History, Recap is index 0). `HistoryRecapFragment` builds session recap sections programmatically; `RecapTrendsFragment` (full-screen nav dest `recapTrendsFragment`, args exerciseName+sessionDateMs) is the exercise-scoped trend. `WorkoutRepository.buildSessionRecap()` + `domain/model/SessionRecap.kt`. Entry points (Home "View Recap" button, completion modal "View full analysis", History row tap) coordinate via activity-scoped `RecapTargetViewModel`.
  - **Two deliberate gaps vs spec:** (1) the work/rest/idle + rest-adherence section is omitted — schema has only session total `durationMinutes`, no per-set timestamps; adding it needs a schema change to log a timestamp per `WorkoutSet`. (2) cardio gets a reps/sets framing (no tonnage) and Trends still uses the weight chart for cardio.
  - PR/"vs last" use date-aware DAO queries (`getMaxWeightBefore`, `getLastSetsForExerciseBefore`) so a historical session shows what it earned at the time.

## Releases pushed

- v1.3.3 → v1.3.5: R8 fixes, wizard, session timer fixes
- v1.5.0 → v1.5.6: animations, 7 bug fixes, ProGuard fixes, rest timer improvements, notification runtime permission (Android 13+), removed top action bar + in-screen back header, exercise alias expansion (30 names), Settings tab freeze fix (use `menu.findItem(id)?.isChecked` not `selectedItemId`)
- v1.5.7: rest timer skip vibration bug, per-day regen missing injury constraints, lint fix, import defensive null check

## Current features (as of 2026-06-22)

- Per-day regeneration: `ProgramFragment` has "Regenerate This Day" button. Shows dialog with equipment and notes inputs. Calls `ProgramViewModel.regenerateDay()` → `AiRepository.generateSingleDayProgram()` → `WorkoutRepository.saveDayPlan()`. Only replaces that day in DB.
- `PlannedExerciseDao.deleteForDay(weekStart, day)` — new DAO method
- `WorkoutRepository.saveDayPlan(weekStart, dayOfWeek, exercises)` — replaces just one day
- `AiRepository.generateSingleDayProgram(...)` — single-day focused prompt, provides rest-of-week context. Now accepts `injuries`, `priorityMuscles`, `dislikedExercises` params (injected into prompt); `ProgramViewModel.regenerateDay()` passes these from prefs.

## Test environment (2026-06-22)

**JVM unit tests** — 54 tests across 5 suites, all passing. `./build.sh test`.  
- `ExerciseCatalogTest` (20), `ExerciseDbResolverTest` (10), `ExtractJsonTest` (11), `ProgramViewModelTest` (4), `ProgramJsonParsingTest` (9)  
- Robolectric added (4.13) but ARM64 Linux native lib issue prevents use — all tests are pure JUnit (no Robolectric runner).

**Roborazzi** — plugin added (1.7.0) but blocked by Robolectric ARM64 native lib issue. No screenshot tests yet.

**Android Emulator** — NOT AVAILABLE for ARM64 Linux. Google only ships ARM64 emulator for macOS. KVM accessible (`/dev/kvm`, ACL `user:migul:rw-`), system image installed, but no emulator binary for this host.

**Maestro** — NOT YET INSTALLED. Run `! curl -fsSL "https://get.maestro.mobile.dev" | bash` to install. Flows created in `flows/`: smoke.yaml, tab_navigation.yaml, rest_timer_notification.yaml, full_workout_flow.yaml. Needs ADB device (Waydroid or physical).

**Waydroid ADB** — `adb connect 192.168.240.112:5555`. Screen 1920×1044, density 180. Use `adb shell uiautomator dump /sdcard/ui.xml` to get element coords. Install via `adb install -r`, NOT `./build.sh installDebug` (hangs over TCP).

## ADB UI test results (session 10 — 2026-06-22)

**All screens tested and verified:**

T10 History/Stats screen: **PASS** — Stats (4 tiles, consistency grid, rep range, export), Progress (exercise selector, time chips, PR empty state, body weight logger), History (search, filter chips, empty state) all render correctly  
T11 Profile screen: **PASS** — L1 badge, XP, stats tiles, PR empty state, Achievements (0/14), Settings row  
T12 Settings hub: **PASS** — 4 sub-screens (Training Profile, AI & Program, Backup & Data, Debug) all navigate correctly  
T13 Training Profile settings: **PASS** — Schedule, Goal/Experience spinners, Equipment preset, Customisation (injuries, muscles, disliked, cardio toggle) all loaded from prefs; save button conditionally visible  
T14 AI & Program settings: **PASS** — API key field and Generate Now button present  
T15 Backup & Data settings: **PASS** — Export/Import backup and Danger Zone (Reset Workouts, Factory Reset) present  
T16 Log workout — set logging: **PASS** — Squat 10kg × 10 reps logged successfully; rest timer auto-started at 0:31  
T17 Rest timer bottom sheet: **PASS** — Opens from recall bar, shows exercise name and AI-suggested duration (1:30)  
T18 Weight/reps steppers: **PASS** — +/- buttons work correctly (4× weight+ = 10kg, 10× reps+ = 10 reps)

**Bugs found and fixed in session 10:**

BUG-01 **Program screen empty state** (FIXED) — Black screen when no plan exists. Added `card_empty_state` with "No Program Yet" message and "Open Settings" button navigating to Profile tab.

BUG-02 **Exercise name spell-checker popup** (FIXED) — Android keyboard showed irrelevant autocorrect suggestions (Blench, Bencher's) blocking the UI. Fixed by adding `inputType="textNoSuggestions"` and `privateImeOptions="nm"` to `et_freestyle_exercise`.

BUG-03 **Keyboard does not dismiss on Done in log screen** (FIXED) — Exercise name, weight, and reps fields had no way to dismiss keyboard. Fixed by:
  - Adding `imeOptions="actionDone"` to all three fields (shows ✓ Done button)
  - Adding `setOnEditorActionListener` in LogWorkoutFragment to clear focus + call `hideSoftInputFromWindow`

BUG-04 **Layout compressed to 10px when keyboard shows** (FIXED) — `adjustResize` compressed weight/reps controls to tiny touch targets. Changed `windowSoftInputMode` to `adjustPan` so layout pans instead of resizing. Weight/reps controls remain full-size after Done dismisses keyboard.

**Waydroid testing limitation noted:** Waydroid's keyboard does not always respond to `KEYCODE_ESCAPE` or `KEYCODE_ENTER` to dismiss — on real devices the Done flow works as expected. This is an environment limitation, not an app bug.

## ADB UI test results (session 9 — 2026-06-22)

T01 Rest timer recall bar: **PASS** — bar visible at [0,809][1920,877] during active free session  
T02 Tab highlighting: **PASS (partial)** — Home, Program, Profile tabs all highlight correctly  
T02 History tab: **NOT TESTED** — needs ADB session  
T03 Back arrow: **PASS** — settingsFragment shows back arrow (correct: sub-destination), profileFragment does not (in topLevelIds)  
T07 Save button position: **PASS** — Save Changes button at bottom of Training Profile  
T08 Save button appears on change: **PASS** — typing in Training Days field shows Save Changes button  
T04/T05 Program + Regenerate dialog: **NOT TESTED** — requires a generated plan and ADB  
T09 Gym preset select no-popup: **CODE VERIFIED** — `btn_select_preset` calls `viewModel.selectPreset()` + re-renders, no dialog shown  

**False positive bug from test agent:** "Profile tab back arrow" — NOT a real bug. `profileFragment` (Profile tab destination) IS in `topLevelIds`. The back arrow the agent saw was on `settingsFragment` (sub-destination from Profile → Settings), which is correct behavior.

## Static analysis results (session 11 — 2026-06-22)

JVM tests: **54 tests passing** (BUILD SUCCESSFUL). Lint: **clean** after one fix.

**Bugs found and fixed in session 11:**

BUG-05 **Rest timer Skip triggers vibration + notification** (FIXED) — `RestTimerManager.stop()` set `_remainingMs.value = 0L`, which triggered the service's `wasRunning` completion handler (vibrate + "Rest complete!" notification) even on manual skip. Fix: removed `_remainingMs.value = 0L` from `stop()`. Next `start()` overwrites it. `RestTimerManager.kt`.

BUG-06 **Per-day regeneration ignored injury constraints** (FIXED) — `generateSingleDayProgram()` had no parameters for `injuries`, `priorityMuscles`, or `dislikedExercises`, so per-day regen silently ignored them. Fix: added these parameters to the signature, injected into prompt, passed from `ProgramViewModel.regenerateDay()` via `prefsManager`. `AiRepository.kt`, `ProgramViewModel.kt`.

**Lint fix:** `android:tint` → `app:tint` in `item_crash_entry.xml` (ImageView in crash log list item).

**Defensive fix:** `ExportRepository.importFromJson()` — `data.preferences` can be null at runtime despite non-nullable Kotlin type (Gson bypass). Added null check with user-facing error rather than NPE.

**Race condition investigated (NOT a bug):** `RestTimerBottomSheet` checks `ms <= 0L && !timerManager.isRunning.value` in a `remainingMs` collector. Both StateFlow updates (`_remainingMs = 0L`, `_isRunning = false`) execute synchronously (no suspension point between them) on Dispatchers.Default before the Main thread collector can run, so `isRunning.value` reads the already-updated `false` value. No race.
