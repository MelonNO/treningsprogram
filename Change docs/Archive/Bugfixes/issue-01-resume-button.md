# Issue 01 — "Resume Workout" button: wrong route + stale label

**One-liner:** The Home "Resume workout" button opens the wrong screen and keeps showing after the workout is done.

## App context
Mobile workout app. Tabs: Home, Program (plan + active "Log" session), Stats/Logs, Profile. When a session is in progress, Home shows a "Resume workout" affordance. There is also a separate freestyle / free-session tab.

## Current (incorrect) behavior
- Tapping "Resume workout" navigates to the freestyle/free-session tab instead of the actual in-progress session.
- The "Resume session" affordance keeps showing even after the workout has been completed.

## Correct behavior (target)
- Tapping "Resume workout" opens the active in-progress session, restored to its current exercise/set state.
- When there is no in-progress session (none started, or the current one is finished), the resume affordance is hidden/cleared.

## Acceptance criteria
- [ ] With a session in progress, tapping "Resume workout" lands on that exact session, not the freestyle tab.
- [ ] After completing a workout, the resume affordance no longer appears on Home.
- [ ] Starting a new session makes it reappear and route correctly.

## Constraints & scope
- Investigate the root cause (routing target + the condition that controls the button's visibility) before changing it.
- Do not alter the freestyle tab's own behavior.
- Touch only this routing/visibility logic.
