# Issue 04 — Entered weight/reps revert to AI suggestion; future prefill logic

**One-liner:** A user-entered weight is lost (reverts to the AI suggestion) when navigating away and back; future sessions should prefill from the user's last actual performance.

## App context
In the active "Log" session, each set has an adjustable weight and reps. The AI provides a suggested weight per exercise. The user can override it.

## Current (incorrect) behavior
- The user sets weight to 8 kg (AI suggested 5 kg). Pressing "Next" then "Back" reverts the value to the AI suggestion (5 kg) instead of keeping the user's 8 kg.

## Correct behavior (target)
1. **In-session persistence:** a value the user enters for a set persists across in-session navigation (Next → Back, exercise switches). The user's entered value is never silently replaced by the AI suggestion.
2. **Future-session prefill:** in new sessions, weight and reps prefill from the user's **last actual performed values** for that exercise. The AI's role is to **suggest** an increase when previous performance warrants it — surfaced as a recommendation, not as a value that overwrites the prefill.

Value precedence: user's entered value (this session) > last actual values (prefill) > AI suggestion (shown as a recommendation).

## Acceptance criteria
- [ ] Enter 8 kg, press Next then Back → still 8 kg.
- [ ] A new session prefills weight/reps from the last actual values for that exercise.
- [ ] When a progression is warranted, the AI's suggested increase is visible as a suggestion and does not silently replace the prefilled value.

## Coordination / related issues
- Touches active-session state (same area as Issues 02/03's state-preservation). Be aware of overlap if those are in flight.

## Constraints & scope
- Keep three sources clearly separated: user-entered, last-actual, AI-suggested. Flag the exact UI for surfacing the AI suggestion as a decision rather than inventing it.
