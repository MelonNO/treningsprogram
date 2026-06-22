# Issue 11 — Tap exercise name → external explanation website

**One-liner:** Tapping an exercise/workout name should open an external website explaining that exercise.

## App context
Exercises are shown by name on the Program cards and in the active "Log" screen.

## Current (incorrect) behavior
- Exercise names are not actionable; there is no way to get an explanation/how-to for an exercise.

## Correct behavior (target)
- The exercise name is tappable. Tapping it opens an external web page (system browser or in-app webview) that explains/demonstrates that specific exercise.

## Acceptance criteria
- [ ] Tapping an exercise name opens an explanation page for that exact exercise.
- [ ] Works for every exercise in the catalog (no dead links / wrong-exercise pages).
- [ ] Returning from the page leaves the session state intact.

## Coordination / related issues
- **Catalog-dependent (shared with Issues 10, 12, 13).** Uses the same exercise identity (id/name) as the image binding.

## Constraints & scope
- A per-exercise URL must be resolved reliably. **A direct mapping (catalog id/name → a known exercise-database URL) is more reliable than a live web search** — flag the URL-resolution strategy as a decision and prefer the deterministic option.
- Decide browser vs. in-app webview; either is acceptable as long as state is preserved on return.
