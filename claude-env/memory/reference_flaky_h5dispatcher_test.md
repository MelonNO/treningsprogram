---
name: reference_flaky_h5dispatcher_test
description: H5DispatcherTest.consumesOnSuppliedDispatcherThread… is a flaky concurrency test — passes in isolation
metadata: 
  node_type: memory
  type: reference
  originSessionId: 93132e3a-86c3-4439-b945-a168069b56ff
---

`com.migul.treningsprogram.H5DispatcherTest > consumesOnSuppliedDispatcherThread_notCallerThread_andClosesBody` (H5DispatcherTest.kt:86) intermittently FAILS in a full `testDebugUnitTest` run with a `ComparisonFailure`, but **passes reliably when re-run in isolation** (`--tests "com.migul.treningsprogram.H5DispatcherTest"`). It's a thread-timing test for the SSE streaming dispatcher — a flake, not a real regression. When a change is resource/UI-only and this is the *only* failure out of 651, treat the suite as green after confirming it passes in isolation; don't chase it.
