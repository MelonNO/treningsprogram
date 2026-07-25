# Brief R2 — Notification center: every notification individually toggleable

**Type:** Feature — user-approved direction (Q3 "sure implement this, but make it possible for the user to disable any specific notification in the settings")
**Cluster:** Standalone worker; conceptually after R1 (the streak nudge uses R1's streak semantics).

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
Notification plumbing already exists: `notify/ReminderScheduler` + `WorkoutReminderReceiver` (daily training-day reminder, v1.20.0 — time picker in Settings → App Settings → Reminders, default OFF, fires only when today is a planned training day, not yet fully logged, and the app is backgrounded; survives reboot via `ReminderBootReceiver`) and `GenerationNotifier` (generation-complete, fires when a program finishes generating while the app is backgrounded). Preferences: `workoutRemindersEnabled`, `reminderHour`, `reminderMinute`. The user explicitly wants per-notification control — e.g. only the streak warning and nothing else.

## What the user wants (end result)
A **Notifications** section (growing out of the existing Reminders section in App Settings) that lists every notification type the app can send, **each with its own on/off switch**, so any combination is possible:

1. **Training-day reminder** (exists today): daily at the user's chosen time on planned training days when nothing is logged yet. Keeps its time picker. Default OFF (unchanged).
2. **Streak warning** (new): one evening nudge on a planned training day when nothing is logged and the streak (per R1 semantics) would break at day's end — e.g. "Your 12-day streak ends tonight — there's still time." Never fires when the streak is 0/1, when today is a rest day, or when the workout is done.
3. **Weigh-in reminder** (new, pairs with R3): a gentle weekly reminder to log body weight, on a user-visible day/time. Default OFF.
4. **Program ready** (exists today as generation-complete): now listed and toggleable like the rest. Default ON (current behavior).

All types respect the missing-notification-permission graceful no-op and never fire while the app is in the foreground (existing conventions).

## Acceptance criteria
- Done when the Notifications settings screen shows the four types above, each independently switchable, and e.g. "only streak warning ON" results in exactly and only streak warnings firing.
- Done when the existing training-day reminder keeps working exactly as shipped (time, conditions, reboot survival) under its toggle — no regression, no duplicate section left behind.
- Done when the streak warning fires at most once per day, only under the conditions above, and its text names the actual streak length at stake.
- Done when the weigh-in reminder fires at most once per week at the configured slot, and only if no weigh-in was logged that day.
- Done when disabling a type stops it immediately (no stale alarms firing visible notifications).
- Done when generation-complete keeps current behavior with its toggle ON, and stays silent with it OFF.
- Scheduling/condition logic unit-tested off-device where pure; user verifies real notifications on-device.

## Scope and constraints
- **In scope:** the settings surface, the per-type toggles + defaults, the two new notification types, refactoring the existing two under the same roof.
- **Out of scope:** the setup wizard (settings only); notification *content* beyond simple useful text; rich actions; anything push/server-side.

## Decisions baked in
- Per-type toggles, any combination possible (the user's exact ask).
- Build on the existing v1.20.0 reminder machinery rather than duplicating it.

## Assumptions (user may override)
- **A-N1:** defaults — training-day reminder OFF (as today), streak warning **ON** (rare + high value), weigh-in reminder OFF, program-ready ON (as today).
- **A-N2:** streak warning time — fixed sensible evening slot (e.g. 20:00) with its own small time preference only if trivial; not a second full picker by default.
- **A-N3:** weigh-in reminder defaults to one fixed weekly slot (e.g. Monday morning) with day/time adjustable.

## Considerations for whoever builds it
- The streak warning depends on R1's "streak at risk" definition — coordinate ordering.
- Multiple daily alarms now coexist; ensure they don't cancel each other (distinct request codes / schedules).
