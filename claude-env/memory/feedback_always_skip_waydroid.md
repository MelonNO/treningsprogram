---
name: feedback_always_skip_waydroid
description: "STANDING: always skip Waydroid / on-device UI testing. Verify via build + unit tests only; the user does any real-device/live verification. Supersedes the old 'prefer on-device verification' stance."
metadata: 
  node_type: memory
  type: feedback
  originSessionId: f6f91f3b-3ce3-443b-a5de-84068e426054
---

**STANDING RULE (user, 2026-06-26): "always skip waydroid."** Do NOT run Waydroid / Maestro / on-device UI tests as part of any verification, and do NOT spawn the `ui-test-worker` for Waydroid runs. The `ui-test-worker` agent is effectively DORMANT under this rule.

**Verification bar instead:** the orchestrator's `./build.sh test` (compile + JVM unit tests) is the bar. Push for STRONG unit coverage of whatever is unit-testable (logic, classification, migrations, pure helpers). For behavior that can only be confirmed live (e.g. real AI generation needing the user's API key, real UI flows), do NOT mark it BLOCKED and do NOT run Waydroid — implement + unit-test it, then HONESTLY state the residual ("X not verified live; confirm on your device") and let the USER do the device/live check. The user ships readily on green unit tests + their own device sanity-check.

**Why:** Stated as a standing instruction after the user deferred Waydroid twice case-by-case earlier in the same session ("skip the waydroid test, keep them around for later"; "i dont want any waydroid testing now") and then generalised it. **This REVERSES and SUPERSEDES the earlier `feedback_prefer_ondevice_verification` memory** (which told agents to build the Waydroid+Maestro harness and actually run behavioral checks rather than punt to BLOCKED). That prior stance no longer applies — the new default is unit-tests-only + user device check.

**Still true / unchanged:** the coordinator still never builds/tests itself ([[orchestrator-owns-changes]]); the orchestrator still owns build + all non-UI tests. The Maestro flows already authored live in `flows/` and the harness is still documented in [[reference_ondevice_test_harness]] IF the user ever explicitly revokes this and asks for an on-device run — but absent an explicit user request, never run them. [[feedback_agent_usage_guideline]]
