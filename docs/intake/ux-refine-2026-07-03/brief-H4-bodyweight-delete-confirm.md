# H4 — Body-weight entry delete: confirm or undo (no silent data loss)

**Type:** Fix (friction / data-safety)
**Cluster:** A (Home) — same files as H1–H3
**Outcome-only.**

## Context
On the Home body-weight card, each recent weigh-in row has a long-press handler that deletes the entry immediately:

`HomeFragment.renderBodyWeightEntries` → `row.setOnLongClickListener { viewModel.deleteBodyMeasurement(m); true }`

There is **no confirmation and no undo**. A long-press — easy to trigger accidentally while scrolling or handling the phone — permanently removes a body-weight record with no way to recover it. Body weight also feeds the trend line and AI context, so an accidental deletion is silently lossy.

## Current (incorrect) behaviour
Long-pressing a body-weight entry deletes it instantly and irreversibly, with no prompt and no feedback.

## Correct behaviour
Deleting a body-weight entry is recoverable. The user gets either a lightweight undo (preferred) or a confirm step, so an accidental long-press cannot silently destroy data.

## Acceptance criteria (Done when …)
- Long-pressing a body-weight entry no longer results in immediate, unrecoverable deletion.
- After a delete, the user can recover the entry within a short window (per A-H4a, a snackbar "Weigh-in deleted · Undo") OR is asked to confirm before deletion.
- If undo is used, the entry (with its original date and value) is restored and the trend line reflects it again.
- The gesture stays fast — no multi-step dialog stack for the common case if the undo approach is chosen.
- Build passes; a unit test covers the delete-then-undo (or confirm-cancel) path.

## Scope and constraints
- **In scope:** the delete affordance for body-weight entries on Home.
- **Out of scope:** any other delete flow in the app (only this one was found unconfirmed); the body-weight schema.
- No schema change — restoring an entry re-inserts the same values.

## Decisions baked in
- Destroying a body-weight record must be recoverable.

## Assumptions (user may override)
- **A-H4a** — Use an **Undo snackbar** rather than a blocking confirm dialog, to keep the gesture quick while making loss recoverable. (User may prefer a confirm dialog instead.)

## Considerations for whoever builds it
- `deleteBodyMeasurement` takes the `BodyMeasurement`; an undo can re-add the same object. Confirm re-insertion doesn't duplicate if the user hammers undo.
- Coordinate with H2: if recent entries move behind an expand affordance, the delete gesture and its new undo must live wherever the entries end up.
