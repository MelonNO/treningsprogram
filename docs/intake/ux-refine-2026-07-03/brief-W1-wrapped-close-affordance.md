# W1 — Monthly Wrapped: a persistent close affordance

**Type:** Refinement (friction)
**Cluster:** — (standalone, Wave 1)
**Outcome-only.**

## Context
The monthly Wrapped (`dialog_wrapped.xml`, `WrappedDialogFragment`) is a **full-screen** `DialogFragment` (`Theme_DeviceDefault_NoActionBar_Fullscreen`). It's a long scrollable story — hero, four stat capsules, then up to five more cards (biggest PR, most improved, favourite, achievements, body weight) and an adherence line. The only in-content way to leave is a **"Close" button at the very bottom**, after all of it. There is no top-of-screen close affordance and no action bar; a user who opens Wrapped and wants out must either scroll all the way down or rely on the system Back gesture.

System Back does work, so this isn't broken — but a full-screen surface with no visible exit until the end is a small friction point, and it's inconsistent with how a modal full-screen surface should behave.

## What the user wants (end result)
Wrapped is easy to leave from anywhere in the story. There's a visible, persistent close affordance (a top "✕" / dismiss) that doesn't require scrolling to the bottom, in addition to the existing bottom Close button and system Back.

## Acceptance criteria (Done when …)
- A visible close affordance is reachable without scrolling — present at the top of the Wrapped surface regardless of scroll position.
- Tapping it dismisses Wrapped exactly like the bottom "Close" button.
- The existing bottom "Close" button and system Back both still dismiss.
- The close affordance is styled consistently with Auros (tinted, not a raw stray glyph — coordinate with C2's icon direction) and has a ≥44dp hit target.
- Build passes.

## Scope and constraints
- **In scope:** adding a persistent close affordance to the Wrapped surface.
- **Out of scope:** Wrapped's content, its data derivation (`MonthlyWrapped`), the once-per-month "ready" card on Home, any sharing (explicitly ruled out).

## Decisions baked in
- A full-screen modal story needs an always-visible exit.

## Assumptions (user may override)
- A top-anchored "✕" is the assumed form; the user might prefer a small top bar with a title + close. Either satisfies the outcome.

## Considerations for whoever builds it
- The root is a `ScrollView`; a persistent top close needs to sit outside the scroll (a top bar) or be pinned, not scroll away with the content.
- If C2 lands first, reuse its close/vector-icon treatment so the glyph isn't a raw `✕`.
