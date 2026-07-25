# Brief 05 — Generation must survive minimizing the app

Type: Bug
Cluster: B (with 06 — same generation machinery; one worker)

> Outcome-only: this brief describes the end result and user experience. The implementation approach belongs to the orchestrator/worker.

## Context

AI program generation currently runs as an in-process coroutine tied to UI-owned scopes (activity/viewmodel), with no background protection of any kind — no service, no scheduler. A "plan ready / didn't finish" notification already exists and fires when the app is backgrounded at completion time.

## Current (incorrect) behavior — user's report

Start a generation, minimize the app (switch away / go Home): on returning, **no plan is saved** and there is **no error** — the generation silently died. Notably: leaving the app foregrounded with the **screen off works fine** (user confirmed), so the failure is specifically about backgrounding.

## Correct behavior

A started generation **keeps running to completion regardless of what the user does with the app** — switch apps, go Home, lock the phone. On success the plan is saved and the existing "plan ready" notification fires (when backgrounded). On failure, the user finds out (the existing failure notification or a visible error on return) — never silence.

## Diagnose first

The cause is not confirmed. Grounded observations for the diagnosis: generation has no background protection (no foreground service / scheduler); process death cancels it with no persisted state; background network restrictions can kill the call; the auto-gen "week done" marker is only written on success or after the failure cap. Establish *why* minimizing kills it before choosing the fix.

## Acceptance criteria

- Done when a generation started from ANY entry point (weekly auto, Settings "Generate now", setup wizard, day swap/regenerate, rebalance) completes and saves after the user immediately minimizes the app for the whole duration.
- Done when the user gets the existing "plan ready" notification in that scenario.
- Done when a genuinely failed generation surfaces to the user (notification or visible error state on return) instead of silently saving nothing.
- Done when foreground generation UX is unchanged (progress display, terminal errors as today).

## Scope and constraints

- All generation entry points, not just the manual one the user noticed.
- Standing constraints: build via `./build.sh`; no commits/releases unless asked; no on-device UI tests — verify via build + unit tests; live-API checks minimal and decision-driven (standing frugal rule).

## Decisions the user deferred

- None — the outcome was explicitly confirmed ("start a generation, leave the app or lock the phone freely, it finishes on its own and you get the notification — yes").
