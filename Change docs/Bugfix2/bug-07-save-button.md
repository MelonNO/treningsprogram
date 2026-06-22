# Bug 07 — Training-profile: sticky, change-gated save button

## Context
Native Android app. The training profile tab is a form/settings screen with a Save button.

## Current (incorrect) behavior
The save button sits at the bottom of the scrolling form (and/or is always shown), so it scrolls out of view and/or appears when there's nothing to save.

## Correct behavior
- The save button **appears only when there are unsaved changes.**
- When shown, it is **always visible / sticky** (does not scroll away).
- It is **not** positioned at the bottom of the screen.

## Decision (pick a default, report it)
"Always visible but not at the bottom" leaves the exact placement open — **default to a pinned top bar or a floating top control** unless the owner specifies otherwise.

## Acceptance
- [ ] With no unsaved changes, no save button is shown.
- [ ] Making a change reveals it; it stays visible while scrolling.
- [ ] It is not at the bottom of the screen.
- [ ] Tapping it saves; once saved (no unsaved changes) it hides again.

## Constraints
- "Unsaved changes" detection must be accurate — don't show it spuriously, don't miss real edits.
- Scope to the training profile tab.
