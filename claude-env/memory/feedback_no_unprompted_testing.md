---
name: feedback-no-unprompted-testing
description: "Do not run automated app tests (ADB taps, screenshots, Waydroid testing) unless the user explicitly asks for it."
metadata: 
  node_type: memory
  type: feedback
  originSessionId: decc6282-e381-48b5-b93c-951491bb1fbe
---

Do not test the app on Waydroid unprompted.

**Why:** User explicitly said "do not test the app unprompted" during session 5 when I ran ADB automation without being asked.

**How to apply:** Only run ADB taps, screencaps, uiautomator dumps, or any form of app interaction on Waydroid when the user specifically asks to test or verify something on device. Building and installing is fine; interactive testing is not unless requested.

**EXCEPTION — the orchestrator + ui-test-worker (set 2026-06-24):** The user has granted a STANDING permission that the **project-lead-orchestrator may spawn the `ui-test-worker` agent as much as it wants** (on-device Waydroid/Maestro UI verification) — no per-instance ask needed. So on-device UI testing performed by `ui-test-worker` under the orchestrator is always pre-authorized. This restriction now applies only to the COORDINATOR (which never runs tests/builds anyway, see [[orchestrator-owns-changes]]) and to ad-hoc app interaction outside the orchestrator→ui-test-worker path. See [[agent-usage-guideline]] and [[feedback_prefer_ondevice_verification]].
