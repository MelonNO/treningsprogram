# fix2-03 · No PR on first performance

**Type:** Bug.

## Context
Native Android app. PRs are tracked per exercise and surfaced (e.g. completion modal, recap, profile).

## Current (incorrect) behavior
A PR can be registered on the **very first** time an exercise is performed — even though nothing was beaten.

## Correct behavior
- The first-ever performance of an exercise sets the **baseline** and is **never itself a PR**.
- A PR is awarded only when a prior performance is **beaten**.
- Applies to **all PR types**: heaviest weight, most reps, best estimated max, best volume.

## Acceptance
- [ ] The first time an exercise is performed, **no** PR of any type is awarded.
- [ ] The baseline is still recorded so future PRs can be measured against it.
- [ ] A later performance that beats the baseline correctly awards the PR.
- [ ] Holds for every PR type.

## Constraints
- Don't retroactively strip legitimately-earned PRs from existing data.
- Verifiable on the JVM (Robolectric) — this is a logic rule.
