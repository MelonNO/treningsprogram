# Bug 06 — Equipment-change "regenerate plan" pop-up missing

## Context
Native Android app. Changing gym equipment presets in settings. (The required pop-up is one the agent is already familiar with.)

## Current (incorrect) behavior
Changing the equipment preset shows no pop-up.

## Correct behavior
- When the user changes the equipment preset, a pop-up appears **informing them they must generate a new workout plan** for the change to take effect.
- **Informational only:** it does not itself change equipment or regenerate the plan. The existing plan stays until the user regenerates.

## Acceptance
- [ ] Changing the equipment preset reliably shows the regen-reminder pop-up.
- [ ] Dismissing it leaves the new equipment setting saved and the current plan unchanged.
- [ ] The pop-up triggers on the actual change event for all preset changes.

## Related
- Separate from Bug 04 (the filter fix). This brief is the **informational reminder only.**

## Constraints
- Verify the trigger fires for every path that changes the equipment preset.
