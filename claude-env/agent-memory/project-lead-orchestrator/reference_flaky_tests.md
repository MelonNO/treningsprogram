---
name: flaky-tests
description: Known intermittently-failing JVM unit tests that fail on thread scheduling, not on code changes — re-run before treating as a regression
metadata:
  type: reference
---

Some tests in `app/src/test` are timing/thread-scheduling flaky and can fail once then pass on an
immediate re-run WITHOUT any code change. Before treating a single failure as a regression, check
whether the failing test is one of these and RE-RUN `./build.sh test`.

- **`H5DispatcherTest.consumesOnSuppliedDispatcherThread_notCallerThread_andClosesBody`** (H5DispatcherTest.kt:86)
  — asserts the streaming body read runs on a named IO-dispatcher thread ("h5-io-worker") and off the
  caller thread. Depends on coroutine/executor scheduling; observed failing spuriously in
  `testReleaseUnitTest` then passing clean on re-run (2026-07, during the settings-ux batch — the batch
  touched none of the streaming/dispatcher code). Unrelated to app logic changes.

**How to apply:** a lone failure in one of these, in code you didn't touch (recovery/nav/settings/etc.),
is almost certainly the flake — re-run once; only investigate if it fails repeatedly.
