# test-04 · Troubleshooting & Debugging

The workflow and tools for the agent to diagnose, not just detect, bugs — including intermittent and UI ones.

## The diagnose loop
1. **Reproduce** reliably (script it; for intermittent bugs, loop it — see below).
2. **Capture** everything on failure: logcat, screenshot, UI hierarchy, and app state.
3. **Localize** — read the stack trace / state to the exact cause.
4. **Hypothesize** a root cause (offer alternatives if ambiguous).
5. **Fix**, then **re-verify at the lowest layer** that reproduces it.
6. Add a **regression test** at that layer.

## Logs & crash capture
```bash
adb logcat -c                       # clear
# ...run the repro...
adb logcat -d > run.log             # dump
adb logcat -b crash -d              # crash buffer (full stack)
adb logcat -d "*:E"                 # errors only
adb logcat -d --pid=$(adb shell pidof -s <your.app.id>)
```
Persist a log file per test run. For a crash, get the exception type + top stack frames + the line in your code; for the previously-crashing timer-finish path, watch the completion handler/notification post.

## Reproducing intermittent bugs (e.g. the nav mismatch)
- **Loop the flow** many times (Maestro `repeat`, or a shell loop) to surface a flaky failure.
- **Vary timing**: insert/remove waits, tap fast, background/foreground between taps.
- **Force config changes**: rotate and re-assert (config-change recreation is a common culprit).
  ```bash
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation 1   # landscape; 0 = portrait
  ```
- **Instrument**: add temporary logging of the navigation route vs the selected-tab state on every tab change, then diff them in the logs to catch the desync. Remove the instrumentation once root cause is found.
- **Capture state each iteration** so the failing run has a full snapshot.

## Inspecting UI & state on device
```bash
adb exec-out screencap -p > shot.png                 # screenshot
adb shell uiautomator dump && adb pull /sdcard/window_dump.xml   # hierarchy
maestro hierarchy                                    # live element tree (ids, state)
adb shell dumpsys activity top                       # current activity/fragment
adb shell dumpsys notification --noredact            # notification state
```
For app data (e.g. Room) on a debuggable build:
```bash
adb shell run-as <your.app.id> ls databases
adb exec-out run-as <your.app.id> cat databases/<db> > local.db   # inspect with sqlite3
```

## Determinism for repro
- Restore a known **emulator snapshot** before each repro run (clean state).
- Seed data and inject a **fixed clock** for time-based bugs (timers).
- Animations off (set in `test-01`).

## Performance / ANR (if needed)
```bash
adb shell dumpsys gfxinfo <your.app.id>      # frame timing
adb shell am trace-ipc / perfetto            # deeper tracing if a stall is suspected
```

## Output of a troubleshooting session
For each bug: a reliable repro (script/flow), the captured root cause (stack/state), the fix, the regression test added, and — if it couldn't be reproduced or fixed — an honest note of what was tried and what's still unknown.
