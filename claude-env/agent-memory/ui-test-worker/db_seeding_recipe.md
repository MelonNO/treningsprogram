---
name: db-seeding-recipe
description: How to seed/backup/restore the Room DB on Waydroid for UI tests (schema, run-as sqlite3 gotchas, date math)
metadata:
  type: reference
---

DB path on device: `databases/treningsprogram.db` (+ `-wal`, `-shm`) inside the app sandbox. Access via `adb -s 192.168.240.112:5555 shell run-as com.migul.treningsprogram sqlite3 databases/treningsprogram.db`.

## Schema (the columns that bite)
- `workout_sessions(id, dateMs, durationMinutes, notes, isCompleted)` — completed = isCompleted=1.
- `workout_sets(id, sessionId, exerciseName, muscleGroup, setNumber, reps, weightKg, isWarmup, rpeLabel, loggedAtMs)`.
  - **Weight column is `weightKg` (Float), NOT `weight`.**
  - **`rpeLabel` is NOT NULL with no SQL-level default** — every INSERT into workout_sets MUST include `rpeLabel` (use `''`). Omitting it → "NOT NULL constraint failed: workout_sets.rpeLabel".
  - `muscleGroup` must be one of {Chest,Back,Legs,Shoulders,Arms,Core} for C4/recovery + muscle-volume queries (they filter `muscleGroup != ''`).
- Strength/PR/recovery queries all filter `isWarmup = 0` and `s.isCompleted = 1`. The time axis for trend/recovery is `workout_sessions.dateMs` (NOT loggedAtMs).

## run-as sqlite3 quoting gotchas
- Inline SQL with parentheses (e.g. `COUNT(*)`) via `adb shell run-as ... sqlite3 ... "..."` breaks: the OUTER shell sees `(` and errors "syntax error: unexpected '('". Wrap the whole `run-as ...` in a single-quoted arg to `adb shell`: `adb shell 'run-as com.migul.treningsprogram sqlite3 databases/treningsprogram.db "SELECT COUNT(*) ..."'`.
- For multi-statement seeds: write a `.sql` file, `adb push` it to `/data/local/tmp/seed.sql`, then `adb shell 'run-as <pkg> cp /data/local/tmp/seed.sql files/seed.sql'` and `adb shell 'run-as <pkg> sh -c "sqlite3 databases/treningsprogram.db < files/seed.sql"'`. (run-as can't read /data/local/tmp directly under all shells; copy into the sandbox first.)
- After seeding while the app may hold the DB: `adb shell am force-stop <pkg>` first, then seed, then `PRAGMA wal_checkpoint(TRUNCATE)`, then relaunch. Room reopens and sees the rows.

## Backup / restore (leave DB as found)
- Backup: `run-as <pkg> cp databases/treningsprogram.db files/tp_backup.db` (+ `-wal`, `-shm`).
- Restore: force-stop app; `cp files/tp_backup.db databases/treningsprogram.db` (+ wal/shm); relaunch. Verify counts match the pre-test snapshot.
- NOTE the device had pre-existing data (1 completed session id=2 with Bench Press/Squat/Sled Push). Always snapshot counts + distinct exercises BEFORE wiping.

## Date math for recovery bands (C4)
Compute on host with `TZ=UTC python3` (device clock is UTC, ~2h behind host wall clock; don't trust device `date`). Bands ([[selectors-wave1]]): set latest session dateMs to now-24h → Recovering (<48h); now-96h → Ready (48h–7d); now-8d → Overdue (>7d); omit a group entirely → Untrained.
Epley e1RM = weight*(1+reps/30); identical across last 3 sessions → B3 stalled; rising → C1 multi-PR + not stalled.
