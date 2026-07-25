# Brief 06 — New week's plan ready by Monday without opening the app + fix the unreliable launch trigger

Type: Bug (existing trigger not firing for the user) + Feature (unattended Monday readiness)
Cluster: B (with 05 — same generation machinery; one worker). 05's background reliability is a prerequisite for this item's outcome.

> Outcome-only: this brief describes the end result and user experience. The implementation approach belongs to the orchestrator/worker.

## Context

Today the ONLY automatic generation trigger is: on a cold app launch (activity creation) in a new Monday-keyed week, if that week has no plan rows yet — guarded by API key present, onboarding done, program not frozen, and a 3-failed-launches-per-week cap. There is no scheduled/unattended trigger and no "week finished" trigger. The user explicitly did NOT ask for a generate-when-last-workout-completes trigger (option (a) was declined).

## Current (incorrect) behavior — user's report

"On Mondays a new week generation is not automatically started." Two facets, both confirmed wanted:

- **(c) Bug:** even when the user opens the app in the new week, generation does not start for them.
- **(b) Feature gap:** the user expects the new week's plan to be there **without opening the app at all**.

## Correct behavior

1. By **Monday morning**, the new week's plan exists **without the user opening the app** (phone on and connected); the existing "plan ready" notification announces it.
2. If unattended generation couldn't happen (phone off, no network, OS blocked it), then **bringing the app to the foreground in a new, plan-less week reliably triggers generation** — regardless of whether that is a cold start or the app was still alive in the background. No silent "nothing happens" Mondays.

## Diagnose first (for facet c)

Why does the user's launch trigger not fire? Grounded candidates to check, not conclusions: the trigger runs only on activity creation (an app resumed from recents never re-runs it); the 3-failure weekly cap writes the week off; a week with ANY existing plan rows is skipped. Identify the actual cause for the user's symptom before fixing.

## Acceptance criteria

- Done when, with the app not opened at all over the weekend, the new week's plan is present by Monday morning and the "plan ready" notification fired.
- Done when opening OR resuming the app any time in a new week with no plan reliably starts generation (the stale-process case can no longer skip it).
- Done when existing guards keep their meaning: frozen program → no auto-gen; no API key / onboarding incomplete → no auto-gen; repeated failures still cap out rather than retry forever (equivalent semantics acceptable).
- Done when a week that already has a plan is never regenerated automatically (current guarantee preserved).
- Done when failures are surfaced (existing failure notification path), never silent.

## Scope and constraints

- No generate-on-week-finished trigger (explicitly not chosen).
- The plan generated is for the new (current) week, as today.
- Standing constraints: build via `./build.sh`; no commits/releases unless asked; no on-device UI tests; frugal live-API verification.

## Assumptions (user may override)

- A5: unattended generation may run late Sunday night or early Monday — "ready by Monday morning" is the contract; the exact schedule is the builder's choice.

## Considerations for whoever builds it (surfaced, not decided)

- Unattended execution is at the mercy of OS battery policies; the launch/resume fallback (correct-behavior point 2) is the guaranteed floor and must be airtight even if the scheduled path is best-effort.
- Interplay with 05: both facets need generation that survives without the UI in front.
