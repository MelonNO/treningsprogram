# fix2-02 · Calisthenics swap must fully update the exercise

**Type:** Bug. Builds on the existing easier/harder swap feature.

## Context
Native Android app. During a workout, the user can swap a calisthenics exercise for an easier or harder variant.

## Current (incorrect) behavior
When swapping, the exercise doesn't fully update to the new variant — name, image, explanation/DB info, and/or the prescription still reflect the old exercise.

## Correct behavior
Swapping replaces **everything** to match the new exercise:
- Name, image, and the explanation/DB info all update to the new exercise.
- The **prescription** (target sets/reps/target) updates to suit the new variant.
- No field still shows the previous exercise's data; logging continues cleanly against the new exercise.

## Acceptance
- [ ] After a swap, name, image, and explanation/DB info all reflect the new exercise.
- [ ] The prescription updates to the new variant.
- [ ] No leftover data from the previous exercise remains anywhere on the screen.
- [ ] Logging works against the swapped-in exercise.

## Constraints
- Verify on-device (Maestro / AVD) by swapping and inspecting every field.
