# Issue 10 — No exercise images render

**One-liner:** No exercise images display anywhere; the image slots are blank.

## App context
The Program cards and the active "Log" screen are meant to show an image for each exercise. Exercise data should come from a fixed catalog keyed by a stable id.

## Current (incorrect) behavior
- No images display at all (blank/absent) — not mismatched, simply missing.

## Correct behavior (target)
- Every exercise shows a correct image, reliably bound to that exercise, present for every prescribable exercise — no blanks, no mismatches.

## Acceptance criteria
- [ ] Every exercise in the plan and in the Log flow shows an image that depicts that exercise.
- [ ] No exercise renders a blank/placeholder where an image should be.
- [ ] Cycling through all exercises shows images framed consistently.

## Coordination / related issues
- **Catalog-dependent, shared with Issues 11, 12, 13** (instructions link, easier/harder alternatives, equipment filtering all rely on the same exercise catalog). **Recommend aligning on the catalog source first**, as that decision underpins all four.

## Constraints & scope
- Recommended reliable approach (method open to the agent): bind images to exercises by **stable catalog id**, not runtime name-matching, and constrain prescribable exercises to the catalog so an image always exists. A public-domain option is `free-exercise-db` (each exercise has a stable `id` and an `images` array; images are servable via `https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/<path>`). The end-state requirement is "correct image always shows"; the binding method is the agent's choice as long as it's reliable.
- First **diagnose why images are currently empty** (broken path, failed fetch, missing binding) before restructuring.
