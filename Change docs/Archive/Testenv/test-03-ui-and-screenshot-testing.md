# test-03 · UI & Screenshot Testing (testing the UI elements)

How the agent exercises every UI element — buttons, lists, dialogs, forms, navigation, gestures, and notifications.

## Stable selectors first
Add **test tags / resource-ids / content descriptions** to UI elements so selectors don't depend on brittle visible text. Compose: `Modifier.testTag("save_button")`. This makes both Robolectric and Maestro tests robust and doubles as accessibility coverage.

## JVM UI tests (Robolectric + Compose test rule) — the fast bulk
```kotlin
@RunWith(AndroidJUnit4::class)
class SaveButtonTest {
  @get:Rule val compose = createComposeRule()

  @Test fun saveButton_hiddenUntilChange_thenVisible() {
    compose.setContent { TrainingProfileScreen(state = clean()) }
    compose.onNodeWithTag("save_button").assertDoesNotExist()
    compose.onNodeWithTag("name_field").performTextInput("X")
    compose.onNodeWithTag("save_button").assertIsDisplayed()
  }
}
```
Use these for conditional UI (save-button visibility), **dialog presence** (equipment pop-up), list/empty states, and validation. Finders: `onNodeWithTag/Text/ContentDescription`; actions: `performClick`, `performTextInput`, `performTouchInput`; assertions: `assertIsDisplayed/assertExists/assertDoesNotExist`.

## Gestures (e.g. the rest-timer swipe)
- **JVM (Compose):** `onNodeWithTag("rest_timer").performTouchInput { swipeDown() }` then assert the timer state persists (hidden but running).
- **e2e (Maestro):** real swipe on the device — see flow below. Gestures that depend on real touch dispatch should also be confirmed on the emulator.

## Screenshot tests (Roborazzi, JVM)
```kotlin
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ProgramScreenShot {
  @get:Rule val compose = createComposeRule()
  @Test fun programScreen() {
    compose.setContent { ProgramScreen(sample()) }
    compose.onRoot().captureRoboImage()   // recorded/verified by gradle tasks
  }
}
```
- `recordRoborazziDebug` to create goldens, `verifyRoborazziDebug` in CI.
- **Mask dynamic regions** (timers, dates, XP) so screenshots aren't flaky.

## Maestro flows (real-device UI & flows)
`flows/timer.yaml`:
```yaml
appId: <your.app.id>
---
- launchApp
- tapOn: { id: "start_workout" }
- tapOn: { id: "log_set" }
- assertVisible: { id: "rest_timer" }
- swipe: { from: { id: "rest_timer" }, direction: DOWN }   # hide
- assertNotVisible: { id: "rest_timer" }
- assertVisible: { id: "timer_recall_bar" }                # the visible affordance
- swipe: { from: { id: "timer_recall_bar" }, direction: UP } # recall
- assertVisible: { id: "rest_timer" }
```
Cover: navigation (tap each tab, assert the right screen + highlight), the timer hide/recall, dialogs, and the full plan→workout→complete flow.

## Navigation correctness (the nav-mismatch bug)
Maestro flow that taps each tab and asserts both the **screen content** and the **highlighted tab** match — repeated, and after a workout and after rotation:
```yaml
- repeat:
    times: 20
    commands:
      - tapOn: { id: "tab_home" }
      - assertVisible: { id: "screen_home" }
      - assertVisible: { id: "tab_home_selected" }
      # ...repeat for program/stats/user
- rotateDevice  # then re-assert agreement
```

## Notifications (must be on the emulator — JVM can't do this)
For the persistent rest-timer notification:
```bash
adb shell dumpsys notification --noredact | grep -A5 <your.app.id>   # assert it exists & ticks
```
Background the app (`adb shell input keyevent KEYCODE_HOME`), let the timer finish, and verify completion (notification posted, then tap-to-return). Sound/vibration firing is harder to assert programmatically — verify the trigger was invoked and spot-check manually.

## The agent's live loop via Maestro MCP
Run `maestro mcp` so the agent can, over MCP: read the **current UI hierarchy**, tap/assert elements, and capture screenshots — instead of guessing from raw screenshots. Use `maestro studio` / `maestro hierarchy` to discover element ids when authoring. This is the interactive "look at the live screen, act, re-check" loop for troubleshooting UI bugs.

## Accessibility
While adding test tags, verify meaningful `contentDescription`s on interactive elements — improves both accessibility and selector stability.
