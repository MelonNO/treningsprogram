# Treningsprogram

An adaptive workout planner and logger for Android. Logs your training sessions, then uses the Claude AI API to analyze your history and automatically adjust next week's program — weights, reps, exercise selection, and weekly volume — based on how you actually performed.

## Features

- **AI-generated weekly programs** — Claude analyzes your last 10 sessions and builds a science-backed plan tailored to your goal, experience level, and training frequency
- **Guided logging** — one exercise at a time with per-set warmup flags, RPE ratings, and a rest timer bottom sheet after each set
- **Adaptive progressive overload** — trend analysis per exercise (progressing / plateaued / regressing) drives automatic load adjustments
- **Cardio support** — dedicated cardio days with duration targets; separate cardio day toggle in settings
- **Exercise images** — pulled from the wger.de open fitness API with fuzzy name matching
- **Gamification** — XP, levels (Rookie → Transcendent), streaks, 14 achievements, weekly rotating challenges, and personal record detection
- **Body measurements** — log and track weight, body fat %, and other measurements over time
- **Strength progress charts** — custom `StrengthChartView` in the Stats tab
- **Gym presets** — equipment profiles that constrain exercise selection
- **Encrypted API key storage** — `EncryptedSharedPreferences`; key is read per-request so changes take effect immediately

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Architecture | Single-Activity MVVM |
| DI | Hilt |
| Persistence | Room (DB v7) |
| Navigation | Jetpack Navigation |
| Networking | Retrofit + OkHttp |
| AI | Anthropic Claude API (`claude-sonnet-4-6`) |
| Image loading | Coil |
| Security | EncryptedSharedPreferences |
| Async | Coroutines + StateFlow |

## Project Structure

```
app/src/main/java/com/migul/treningsprogram/
├── data/
│   ├── api/                  Retrofit interface + Gson models for Anthropic /v1/messages
│   ├── db/
│   │   ├── entity/           Room entities: Exercise, WorkoutSession, WorkoutSet,
│   │   │                       PlannedExercise, UserStats, Achievement, BodyMeasurement
│   │   └── dao/              DAOs for each entity
│   ├── preferences/          PreferencesManager (EncryptedSharedPreferences),
│   │                           DailyChallengeManager (3 challenges/week, ISO week key)
│   └── repository/
│       ├── WorkoutRepository   All DB ops; session start/complete, plan save/fetch
│       ├── AiRepository        Builds prompt → calls Claude → parses JSON plan
│       ├── GamificationRepository  XP/level/streak/achievement/PR processing
│       └── WgerRepository      Exercise image fetching with fuzzy name matching
├── di/                       Hilt modules: AppModule, DatabaseModule, NetworkModule
└── ui/
    ├── home/                 Today's plan preview, active session resume, XP badge
    ├── program/              Week calendar, day detail, Start Workout
    ├── log/                  Guided active session logger with rest timer
    ├── history/              Log, Progress (chart), Stats — three-tab layout
    ├── profile/              Level/XP, weekly challenges, PRs, achievements
    └── settings/             Training profile, AI generation, gym presets
```

## AI Adaptation

`AiRepository.generateAdaptedProgram()`:

1. Fetches the last 10 completed sessions with sets
2. Filters out warmup sets and computes a trend per exercise (PROGRESSING / PLATEAUED / REGRESSING)
3. Builds a structured prompt encoding:
   - Goal-specific rep/set/rest schemes (Strength, Hypertrophy, Endurance, Weight Loss)
   - Training split by days/week (2 = Full Body A+B, 3 = PPL or Full Body A/B/C, 4 = Upper/Lower ×2, 5 = Push/Pull/Legs/Upper/Cardio, 6 = PPL ×2)
   - Explicit MUST / MUST NOT exercise rules per day type to enforce balanced splits
   - Per-muscle weekly volume targets and 2×/week frequency rules for hypertrophy
   - Exercise hierarchy (compound before isolation for the same muscle)
   - Progressive overload instructions derived from trend data (+2.5 kg for PROGRESSING, deload for REGRESSING)
   - Recovery constraints (no same muscle on consecutive days, 48-72 h between heavy compounds)
4. Calls `POST https://api.anthropic.com/v1/messages` via Retrofit
5. Strips any markdown fences and parses the JSON response into `PlannedExercise` entities
6. Saves the week's plan to the `planned_exercises` table

Auto-generation runs once per ISO week on app launch (stored in `lastAutoGenerateWeek`). Manual generation is available in Settings → AI Generation → **Generate Now**.

## Build & Run

The build environment targets **Android SDK 34 / Java 21**. Use `./build.sh` — it sets `JAVA_HOME`, `ANDROID_HOME`, and `QEMU_LD_PREFIX` (needed for x86_64 `aapt2` under QEMU).

```bash
# Debug APK
./build.sh assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Install on connected device (USB debugging enabled)
./build.sh installDebug

# Unit tests
./build.sh test

# Lint
./build.sh lint
```

To use `./gradlew` directly, add these to your shell profile:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
export ANDROID_HOME=/home/migul/android-sdk
export QEMU_LD_PREFIX=/opt/x86_64-sysroot
```

## First-Time Setup

1. Launch the app and open the **Profile** tab → **Settings**
2. Paste your Anthropic API key (`sk-ant-…`)
3. Set training days/week, goal, and experience level → **Save**
4. The app auto-generates a weekly plan on launch; or go to Settings → **Generate Now**
5. Open the **Program** tab to see the week's plan, then tap **Start Workout**
6. Log your sets → **Complete Workout**

The exercise library (26 default exercises across 6 muscle groups) is seeded automatically on first launch.

## Database

Room database at version 7. Migration chain: `1→2` (user_stats + achievements) `→ 3 → 4 → 5 → 6` (BodyMeasurement) `→ 7` (WorkoutSet adds `isWarmup: Boolean` and `rpeLabel: String`).
