# Bug 04 — "Home gym" prescribes squat-rack exercises

## Context
Native Android app. Equipment presets drive which exercises the AI plan may prescribe. The **"Home gym" preset includes a barbell but no squat rack.**

## Current (incorrect) behavior
With Home gym selected, the plan prescribes exercises that require a squat rack (e.g. back squat, rack pull).

## Correct behavior
- The equipment filter treats **barbell and squat rack as separate** equipment — having a barbell does **not** imply having a rack.
- Rack-dependent exercises are **excluded** when no rack is available.
- Barbell movements that don't need a rack (bench press, deadlift, barbell row, etc.) remain allowed.

## Diagnose first
Find where the squat-rack requirement is not being applied — likely the filter conflates "barbell" with "rack", or rack-requiring exercises aren't tagged/excluded.

## Acceptance
- [ ] With Home gym (barbell, no rack), generated plans contain **no** rack-dependent exercises.
- [ ] Barbell-without-rack exercises are still allowed (no over-exclusion).
- [ ] Holds across several plan regenerations.

## Constraints
- Keep the exclusion logic **general** (this is the same class of bug as the earlier rubber-band issue) — not a one-off hack for the squat rack.

## Related
- Separate from Bug 06 (the regen pop-up) — different code, different concern.
