# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Native Android app (Kotlin) — an adaptive workout planner and logger that uses the Claude AI API to analyze previous sessions and automatically adjust future training programs (weights, reps, exercise selection, weekly volume).

**Build environment is set up on this Pi.** Java 21, Android SDK 34, and x86_64 QEMU emulation (for `aapt2`) are all installed. Use `./build.sh` instead of `./gradlew` — it sets the required env vars automatically.

## Build & Run

```bash
# Build debug APK (use build.sh — it sets JAVA_HOME, ANDROID_HOME, QEMU_LD_PREFIX)
./build.sh assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Install on connected Android device (USB debugging enabled)
./build.sh installDebug

# Unit tests
./build.sh test

# Lint
./build.sh lint
```

If you add the three env vars to `~/.bashrc` you can use `./gradlew` directly:
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
export ANDROID_HOME=/home/migul/android-sdk
export QEMU_LD_PREFIX=/opt/x86_64-sysroot   # needed so QEMU can run x86_64 aapt2
```

## Architecture

Single-Activity MVVM with Hilt DI, Jetpack Navigation, and Room persistence.

```
data/
  db/entity/       — Room entities: Exercise, WorkoutSession, WorkoutSet, PlannedExercise
  db/dao/          — DAOs for each entity
  db/AppDatabase   — Room database; DEFAULT_EXERCISES list is seeded on first launch
  api/             — Retrofit interface + Gson models for the Anthropic /v1/messages endpoint
  preferences/     — PreferencesManager (EncryptedSharedPreferences): API key, days/week, goal, experience
  repository/      — WorkoutRepository (DB ops), AiRepository (builds prompt → calls Claude → parses JSON)
di/                — Hilt modules: AppModule, DatabaseModule, NetworkModule
ui/{home,log,history,program,settings}/  — Fragment + ViewModel per screen
```

## AI Adaptation Flow

`AiRepository.generateAdaptedProgram()`:
1. Fetches last 10 completed sessions with their sets from `WorkoutRepository`
2. Builds a structured prompt including the workout history + user profile (goal, experience, days/week)
3. Calls `claude-sonnet-4-6` via Retrofit (`POST https://api.anthropic.com/v1/messages`)
4. Parses the JSON response (handles markdown code fences) into `PlannedExercise` entities
5. Saves the week's plan via `WorkoutRepository.savePlan(weekStart, exercises)`

The prompt instructs Claude to return **only** valid JSON with no prose; `extractJson()` strips any stray fences before parsing.

## Key Conventions

- ViewBinding throughout — no `findViewById`
- `StateFlow` + `repeatOnLifecycle` for UI observation
- `viewModelScope` + coroutines for all async work; no RxJava
- `WorkoutSet` stores `exerciseName` as a denormalized string (no join needed in the adapter)
- Navigation uses plain `Bundle` args (no Safe Args plugin) — session ID is passed as `"sessionId"` Long
- The OkHttp interceptor reads `PreferencesManager.apiKey` on every request, so API key changes in Settings take effect immediately without restarting

## First-Time Setup

1. Launch app → open **Settings** tab
2. Paste your Anthropic API key (`sk-ant-…`)
3. Set training days/week, goal, and experience level → **Save**
4. Go to **Program** tab → **Generate AI Program**
5. The AI builds a weekly plan stored in `planned_exercises` table; **Home** shows today's exercises
6. Tap **Start Workout** → log sets → **Complete Workout**

The exercise library (26 default exercises across 6 muscle groups) is auto-seeded on first DB creation via `WorkoutRepository.ensureExercisesPopulated()`.
