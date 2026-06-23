# test-02 · Strategy & Coverage (what to test where)

Principle: **test at the lowest layer that can catch the bug.** Most checks run on the JVM in seconds; the emulator is reserved for what genuinely needs a real device.

## What goes at each layer

**JVM unit — Robolectric (`./gradlew test`)**
- Business logic, ViewModels, repositories, use-cases.
- Data mapping and rules — e.g. the equipment filter (squat-rack exclusion), the exercise name→image resolver, progression logic.
- Screen state: what state produces what UI condition.

**JVM UI behavior — Robolectric + Compose/View test rule**
- Conditional UI: e.g. the save button appears only when there are unsaved changes.
- Dialog/pop-up presence: e.g. the equipment-change "regenerate plan" pop-up shows on change.
- List rendering, empty states, form validation.

**JVM screenshots — Roborazzi**
- Visual regression for each screen and key components (mask dynamic content like timers/dates).

**On-device e2e — AVD + Maestro**
- Real navigation timing and the tab-highlight↔screen agreement (the nav-mismatch bug).
- Touch **gestures**: the rest-timer swipe-down-hide / swipe-up-recall.
- **Notifications**: the persistent rest-timer notification, completion sound/vibration, tap-to-return — verified **backgrounded and closed**.
- WebViews (e.g. an exercise-explanation page), system UI, picture-in-picture.
- Full user flows end-to-end (generate plan → start workout → log sets → complete).

## What "complete" coverage means here
- **Every screen:** at least one logic/state test + one Roborazzi screenshot.
- **Every user flow:** one Maestro e2e.
- **Every fixed bug:** a regression test at the lowest layer that can catch it.
- **Every dialog/conditional element:** a presence/visibility test.

## Feature / bug → layer mapping (apply to the current backlog)
- Equipment filter (squat rack, bands) → **JVM unit**.
- Save-button visibility (change-gated) → **JVM UI**.
- Equipment-change pop-up presence → **JVM UI** (existence) + e2e (real trigger path).
- Navigation mismatch → **e2e (Maestro)** + a JVM test on the nav/selected-tab state.
- Timer hide / recall / manual start → e2e gestures; the persistent **notification** → e2e backgrounded/closed.
- Image resolver / data → **JVM unit**; screen visuals → **Roborazzi**.

## Determinism (non-negotiable for stable tests)
- Disable animations on the emulator.
- Seed test data; inject a fixed clock for anything time-based (timers, dates).
- Fix locale and Roborazzi device qualifiers so screenshots are reproducible.
- Restore a known emulator snapshot before each e2e run.

## Caveats (don't over-trust the JVM layer)
- Robolectric is not a full emulator: no real screen, some APIs partial; **system UI, notifications, and WebViews must be tested on the emulator**.
- JVM screenshot font/anti-aliasing can differ slightly from device GPU — keep a small device-screenshot pass for pixel-critical screens.
