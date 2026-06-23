# Testing & Troubleshooting — Index (for a coding agent)

Goal: set up the testing toolchain and give the agent a repeatable way to **completely test and troubleshoot** this app — logic, data, UI elements, gestures, navigation, notifications, and full user flows.

Context: **native Android (Kotlin/Java)**, host is **bare-metal Linux with KVM**.

## The layered model (test at the lowest layer that can catch the bug)
1. **JVM unit/logic — Robolectric** (`./gradlew test`): runs the Android framework on the JVM, no emulator, seconds. Logic, ViewModels, repositories, data mapping, screen state, conditional UI, dialog presence.
2. **JVM screenshots — Roborazzi**: visual regression of screens/components on the JVM, no emulator.
3. **On-device e2e — AVD + KVM, driven by Maestro**: the ~10% that needs a real device — real navigation timing, touch gestures, **notifications**, WebViews, system UI, and end-to-end flows.

**Inner loop is JVM (fast); the emulator is the exception, not the default.** Reserve it for what genuinely can't run on the JVM.

## Document map
- `test-01-environment-setup.md` — install & verify the whole toolchain (KVM, SDK/emulator, Robolectric, Roborazzi, Maestro/MCP, ADB).
- `test-02-strategy-and-coverage.md` — what to test at which layer; what "complete" coverage means; feature→layer mapping.
- `test-03-ui-and-screenshot-testing.md` — testing UI elements: Compose/View tests, screenshot tests, Maestro flows, the agent-via-MCP loop, gestures, notifications.
- `test-04-troubleshooting-and-debugging.md` — logs, crash capture, reproducing intermittent bugs, UI/state inspection, the diagnose loop.

## Standing rules
- **Determinism:** disable animations, seed data, fix the clock and locale/qualifiers, and restore a known emulator snapshot before e2e runs. Flaky tests are worse than no tests.
- **Version-pin:** confirm exact tool versions / Gradle coordinates against the official docs (linked in `test-01`) before relying on any command here — they drift.
- **Regression-first:** every bug fixed gets a test at the **lowest layer that can catch it**, so it can't silently return.
