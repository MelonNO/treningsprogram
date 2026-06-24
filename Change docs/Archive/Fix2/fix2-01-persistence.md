# fix2-01 · Workout-session persistence (resume keeps all sets & user values)

**Type:** Bug. **Merged from old 5 + 6** — same root cause (the in-progress session isn't fully persisted when the app is closed).

## Context
Native Android app, active "Log" workout. The app can be closed mid-workout; Home offers "resume workout".

## Current (incorrect) behavior
- **Lost sets on exercise 1:** if the app is closed while the user is on **exercise 1** and they resume from Home, the sets already performed on exercise 1 are **not** shown as part of that session — they're lost. If the app is closed on **any later exercise**, resume works correctly and prior sets are retained.
- **Reverted weights:** if the app is closed mid-workout, on resume the set weights revert to the **AI-suggested** values instead of the values the **user entered**.

## Correct behavior
Closing and resuming a workout preserves the **complete in-progress session exactly**, regardless of which exercise the user was on:
- All logged sets are retained — including those on exercise 1.
- All user-entered values (weight, reps, effort) are exactly as the user left them — never reset to AI suggestions.
- The user resumes the **same** session (not a new one), with full history intact.

## Diagnose first
The exercise-1-vs-later asymmetry suggests the first exercise's logged sets aren't committed the same way as later ones; the weight revert suggests user-entered values aren't persisted (only the AI suggestion is restored). Find the actual cause before patching.

## Acceptance
- [ ] Perform sets on exercise 1, fully close the app, resume from Home → those sets are present in the session.
- [ ] Same on a later exercise → still present (regression check).
- [ ] After close + resume at any point, all user-entered weights/reps/effort are exactly as entered, not AI defaults.
- [ ] The resumed session is the same session, full history intact.

## Coordination / constraints
- Touches active-session state — coordinate with Item 6 (quick-access). Do Item 1 first.
- Don't regress the resume-button routing fix (Home "resume" opens the live session).
- Verify by **fully killing the app and reopening**, on exercise 1 and a later exercise.
