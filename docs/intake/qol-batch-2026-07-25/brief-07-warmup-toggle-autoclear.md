# Brief 07 — Warm-up toggle auto-clears after every logged set

Type: UX change (small)
Cluster: A (with 01 and 10 — same log-workout screen; one worker or strict sequence)

> Outcome-only: this brief describes the end result and user experience. The implementation approach belongs to the orchestrator/worker.

## Context

The log-workout screen has a per-set "warm-up" chip the user checks before pressing Log Set; the set is stored with a warm-up flag. Warm-up sets are excluded from stats, XP, PRs, and session-counting everywhere downstream. Today the chip is **sticky**: once checked it stays checked (across sets and even across exercise switches) until manually tapped off — so a forgotten toggle silently records real working sets as warm-ups.

Separate and unaffected: the "Log warm-up sets" ramp button logs its ramp steps as warm-ups on its own, without the chip.

## Current vs correct behavior

- Current: warm-up chip stays on after logging a set.
- Correct: the chip **clears itself immediately after every logged set**. Each warm-up set is explicitly marked by tapping the chip again (user chose this over clear-on-exercise-change).

## Acceptance criteria

- Done when logging a set with the warm-up chip on records that set as warm-up AND leaves the chip off for the next set.
- Done when logging with the chip off keeps it off (no change to the normal path).
- Done when the warm-up ramp's "Log warm-up sets" flow still records its sets as warm-ups exactly as before, independent of the chip.
- Done when nothing else about set logging changes (RPE clearing, rest timer, etc. behave as today).

## Scope and constraints

- Only the chip's stickiness changes; the meaning of the flag and all downstream treatment of warm-up sets is untouched.
- Standing constraints: build via `./build.sh`; no commits/releases unless asked; no on-device UI tests — verify via unit tests.

## Decisions baked in

- Clear after **each** logged set (user chose option (a) explicitly, understanding a second warm-up set needs a re-tap).
