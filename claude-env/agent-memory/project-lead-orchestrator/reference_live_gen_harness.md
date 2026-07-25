---
name: reference-live-gen-harness
description: How to live-test the REAL AiRepository generation path (prompt+parse+gates+validator) on a plain POST from a JVM test on this aarch64 Pi
metadata:
  type: reference
---

To LIVE-verify generation behavior while still exercising the REAL app code (not a rewritten approximation), build a throwaway JUnit test in `app/src/test/java/com/migul/treningsprogram/data/repository/` (same package ⇒ `internal` gate fns are directly callable):

- `@RunWith(RobolectricTestRunner) @ConscryptMode(ConscryptMode.Mode.OFF)` — Robolectric gives a real Context (filesDir/prefs/NotificationManager) so PromptLog/RejectionLog/ExerciseDbResolver/GenerationNotifier construct for real; Conscrypt OFF because its native has no aarch64 build (same reason as [[reference_robolectric_sqlite_aarch64]]). Don't open Room.
- **WorkoutRepository is Room-backed ⇒ un-constructable here, but buildPrompt/parseProgram/validateProgram never call it.** Allocate a bare shell via `sun.misc.Unsafe.allocateInstance` (reflect the class fully — `sun.misc.Unsafe` is NOT referenceable at compile time on JDK 21), pass it to the REAL `AiRepository(...)` constructor.
- Fake `ClaudeApiService.sendMessageStreaming` = the SINGLE live-call choke point: append the crash-durable ledger line BEFORE a blocking `HttpsURLConnection` POST to `https://api.anthropic.com/v1/messages` (headers x-api-key from `claude k`, anthropic-version 2023-06-01), then wrap the returned `content[0].text` back into a 2-event SSE string so the REAL `consumeClaudeStream`/`parseClaudeStream` reconstruct the ClaudeResponse. Model = ClaudeRequest default (claude-sonnet-4-6).
- Call the 3 PRIVATE methods by name+arity reflection (`declaredMethods.first{name && parameterCount}` — the `$default` synthetics have different names/arities): buildPrompt(23 args since v1.22.0 — manualRest param; was 22), parseProgram(1), validateProgram(8 incl. Continuation → invoke inside `suspendCoroutine{ cont -> if(res!==COROUTINE_SUSPENDED) cont.resume(res) }`). ValidationResult is file-private → read its `accepted`/`reason` fields by reflection.
- Replicate the ladder in the harness by calling the REAL internal gate fns (WorkoutTimeEstimator.estimateDayMinutes, dayDurationFeedback, extractJsonOrNull, isLikelyTruncated) and feeding the real dayDurationFeedback back as previousRejectionReason.

Gotchas: (1) run ONLY `testDebugUnitTest` (bare `test` runs debug+release ⇒ the @Test fires TWICE ⇒ doubles paid calls). (2) `--no-daemon` + read config via `System.getenv()?:System.getProperty()` so env reaches the forked test JVM; `--rerun-tasks` to force re-run (env isn't a tracked input). (3) Guard with `assumeTrue(env=="1")` so normal builds never spend money. (4) `break/continue` inside an inline-lambda (getOrElse{}) is experimental in Kotlin 1.9.20 — restructure. (5) Synthesized logged history with an unrealistic exercise/minute ratio can bias sizing — always run a no-history control to separate artifact from real behavior. DELETE the harness when done (throwaway; makes paid calls; leave the tree clean).
