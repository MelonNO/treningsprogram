# Brief 01 — The rest timer must end when the workout does

**Type:** Bug (cause known — see grounded facts)
**Cluster:** Independent. Shares no code with brief 02.

> **Outcome-only.** This brief describes the end result and the user experience. It does not
> prescribe an implementation. The "Grounded facts" section at the end is orientation, not a
> prescribed fix.

---

## Context

The rest timer is deliberately independent of the workout screen — that is what lets it keep counting
while the user swipes the sheet away, switches app, or locks the phone. It shows a live countdown in
the notification shade, and when it reaches zero it announces itself: the phone vibrates, a chime
plays, and a heads-up notification appears.

That independence has no upper bound. Nothing on the "finish workout" path — or the "abandon workout"
path — ever tells the timer to stop.

## What the user wants (end result)

When a workout session ends, the rest timer ends with it. Silently, immediately, and regardless of
*how* the session ended.

The user's own words: *"when the workout is finished the rest timer from the last two is still
counting so if the finish workout button is pressed before the last rest timer is finished the user
will get a alert after the workout is finished."*

## Current (incorrect) behaviour

1. User logs their final set. A rest timer starts automatically.
2. User presses **Complete Workout** before that rest elapses.
3. The session is saved and the user is taken away from the logging screen.
4. **The countdown notification stays in the shade**, still counting.
5. When it reaches zero, the phone **vibrates (500 ms)**, plays a **completion chime**, and shows a
   heads-up notification reading **"Rest complete!" / "Time to work!"** — for a workout that is over.

The same happens when the user **abandons** the workout instead of completing it.

## Correct behaviour

A workout session ending — **completed, abandoned, or by any exit route added in future** — ends the
rest timer as part of that. Ending it is **silent**:

- The countdown disappears from the notification shade straight away.
- No vibration.
- No chime.
- No "Rest complete!" notification.
- No confirmation prompt, no "your rest timer is still running" dialog. The user was explicit: it
  should just end.

## Also in scope — a second false alert from the same alert path

The user reviewed and approved sweeping this in.

If Android kills the app's process while a rest timer is running and later restarts the timer
service, the service can fire its **completion alert immediately on restart** — vibrate, chime and
"Rest complete!" — with no rest ever having been running. Same lie, different trigger.

This must not happen either: the completion alert may only fire when a rest that was genuinely
running has genuinely reached zero.

## Acceptance criteria

- **Done when** completing a workout while a rest timer is running removes the countdown notification
  immediately and produces no vibration, no sound and no "Rest complete!" notification — then or at
  any later point.
- **Done when** abandoning a workout while a rest timer is running does exactly the same.
- **Done when** the behaviour is expressed as a general rule — *a session ending ends the timer* — so
  that a new way of leaving a workout, added later, inherits it without a second fix.
- **Done when** the existing **Skip** button still works exactly as it does today: silent stop, no
  completion alert.
- **Done when** a rest timer that runs to zero *during* a live workout still gives the full alert —
  vibration, chime and notification. The fix must not silence the feature it is protecting.
- **Done when** a restart of the timer service after the app's process was killed cannot, by itself,
  produce a completion alert.
- **Done when** unit tests cover: session-completed-with-timer-running, session-abandoned-with-timer-
  running, timer-completes-mid-session (alert still fires), and skip (still silent).

## Scope and constraints

**In scope**
- The rest timer's lifetime relative to a workout session.
- The false completion alert on service restart.

**Out of scope**
- Rest duration values, the ± adjustment, per-category rest times — untouched.
- The timer's behaviour *during* a workout: backgrounding, screen-off, sheet dismissal all keep
  working as they do now. This brief only adds an ending.
- Any other timer in the app.

**Hard constraints**
- Build via **`./build.sh`**, never `./gradlew` directly.
- **No commits or releases** unless the user asks.
- **No on-device or automated UI tests** (standing rule). Verify via build + unit tests; the user does
  the device check. Note that **JVM tests cannot prove the notification/vibration behaviour on a real
  device** — the user's own check is the real proof for this item.

## Decisions baked in (the user's own answers)

1. Ending the timer is **silent** — no prompt, no "stop the timer?" dialog. (Q1.1: *"correct"*)
2. **Abandoning** a workout ends it too, not just completing. (Q1.2: *"yes"*)
3. Fixed as a **general rule**, not as a patch on the Finish button, so future exit routes inherit it.
   (Improvement a: *"correct"*)
4. The **process-restart false alert** is swept into the same fix. (Improvement b: *"go"*)

## Considerations for whoever builds it

- There is a single convergent signal for "this session is over" already in the code (see grounded
  facts). Whether to hang the rule off that, off the session lifecycle, or somewhere else is the
  builder's call — the requirement is only that *every* ending ends the timer.
- The existing stop path was written to be silent **on purpose**, and there is a comment explaining
  why. Read it before changing anything about how stopping works; the silence is load-bearing for the
  Skip button.
- The restart false-alert and the session-end fix are close together in the same two files. They are
  one worker's job, not two.

---

## Grounded facts (verified 2026-08-07 — orientation only, not a prescribed fix)

- `app/src/main/java/com/migul/treningsprogram/ui/log/RestTimerManager.kt` — `@Singleton` (line 11),
  so it is **application-scoped** and outlives the logging fragment entirely.
- `RestTimerManager.stop()` (line 51) is **already silent by design**. Its comment (lines 55–56):

  ```
  // Do NOT zero remainingMs here — setting it to 0 triggers the service's completion
  // handler (vibrate + notification) even on a manual skip. The next start() overwrites it.
  ```

- **The only caller of `stop()` in the entire app** is the Skip button:
  `ui/log/RestTimerBottomSheet.kt:72`. Line 110 notes that swipe-down dismissal deliberately does
  *not* stop the timer. **Nothing else stops it — that is the bug.**
- The timer is started at `ui/log/LogWorkoutFragment.kt:973–974`, after a set is logged.
- `ui/log/RestTimerService.kt` — completion handler at lines 46–55: when `ms <= 0 && !running &&
  wasRunning` it calls `vibrate()` (500 ms, line 154), `playCompletionSound()`, and posts
  `"Rest complete!"` / `"Time to work!"` (lines 122–123), auto-cancelled after 2.5 s.
- The service is `START_STICKY` (line 63). On a process-kill restart, `onCreate` runs against a
  **fresh singleton manager** whose `remainingMs` is `0` and `isRunning` is `false`, while
  `wasRunning` is initialised to `true` (line 42) — so the first emission satisfies the completion
  condition and fires the alert with no rest ever running. **This is the second false alert described
  above.**
- Both session endings converge on one flag: `ui/log/LogWorkoutViewModel.kt` —
  `completeWorkout()` (line 707) and `abandonSession()` (line 764) both set `_sessionAbandoned`
  (lines 715, 778); `LogWorkoutFragment.kt:390` collects it and navigates away.
