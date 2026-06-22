# Issue 13 — AI prescribes rubber-band exercises the user can't do

**One-liner:** The user does not have rubber bands, but the AI still prescribes resistance-band exercises because it isn't applying that equipment constraint.

## App context
The AI plan generator has access to the user's available-equipment list and is supposed to only prescribe exercises the user can perform.

## Current (incorrect) behavior
- The user does **not** own rubber/resistance bands. The AI has the equipment list, but it does not respect the rubber-bands constraint and still prescribes band-dependent exercises the user cannot do.

## Correct behavior (target)
- The AI excludes any exercise that requires equipment the user lacks. Specifically, with rubber/resistance bands marked unavailable, no band-dependent exercise is prescribed.

## Acceptance criteria
- [ ] With rubber bands marked unavailable, generated plans contain **no** band-dependent exercises.
- [ ] Spot-check across several plan regenerations → consistently no band exercises.
- [ ] Exercises requiring other unavailable equipment are likewise excluded (verify the filter is general, not a one-off).

## Coordination / related issues
- **Catalog/equipment-dependent (shared with Issues 10, 12).** Uses the catalog's per-exercise `equipment` tag.

## Constraints & scope
- **Diagnose first:** likely the equipment filter doesn't map the "resistance band / rubber band" category, or band exercises carry an equipment value the filter misses. Confirm where the mapping breaks before changing the generator.
- Ensure "bands" is in the user's exclusion set when not owned, and that the catalog's band-equipment value is recognized.
