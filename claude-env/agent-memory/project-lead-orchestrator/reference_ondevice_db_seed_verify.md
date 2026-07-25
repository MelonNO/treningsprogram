---
name: ondevice-db-seed-verify
description: "How to verify DAO/aggregation fixes on-device without the flaky Maestro text entry — seed workout_sets via run-as sqlite3 and read query results back"
metadata:
  type: reference
---

To verify Room DAO / aggregation-query fixes on the Waydroid device deterministically (instead of fighting Maestro's unreliable text entry), seed rows directly and read the post-fix query results back via `run-as sqlite3`.

**Recipe that worked (2026-06-23 QA pass):**
1. Build + `adb install -r`, then verify the installed APK by MD5 against the repo APK (catch stale builds — see [[reference_ondevice_test_harness]]).
2. Write a `.sql` script to scratch, `adb push` it to `/data/local/tmp/foo.sql`, then run:
   `adb -s 192.168.240.112:5555 shell "cat /data/local/tmp/foo.sql | run-as com.migul.treningsprogram sqlite3 databases/treningsprogram.db"`
   (piping a pushed file avoids all the shell-quoting pain of inline SQL through run-as.)
3. Put `SELECT '=== label ==='` lines between statements so the output is self-describing.
4. Restore state at the end of the same script: `DELETE FROM workout_sets WHERE sessionId=<sentinel>; DELETE FROM workout_sessions WHERE id=<sentinel>;` and re-assert the BEFORE count == AFTER count for reversibility.

**Gotchas:**
- `workout_sessions` has a NOT NULL `notes TEXT` column — an INSERT that omits `notes` fails with "NOT NULL constraint failed: workout_sessions.notes", and because the session row never lands, any query joining `... AND s.isCompleted=1` silently returns empty. Always insert sessions as `(id, dateMs, durationMinutes, notes, isCompleted)`.
- Use a sentinel session id far above real ones (e.g. 999001). Set `PRAGMA foreign_keys=OFF;` at the top if inserting sets before/without a matching session.
- DB path: `databases/treningsprogram.db` (+ `-wal`, `-shm`). sqlite3 is at `/system/bin/sqlite3`.
- Contrast technique: run BOTH the post-fix query and the OLD (pre-fix) query in the same script to prove the bug existed and the fix changes the result (e.g. strength-history warm-up filter: old MAX gave 999, new gave 50).

For pure logic (classifiers, parsing, index math) prefer JVM unit tests via `./build.sh test` — far faster and they gate the build.
