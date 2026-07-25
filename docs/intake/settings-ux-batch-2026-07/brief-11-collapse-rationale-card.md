# Brief 11 — Collapse "Why your program changed" by default

**Type:** Feature (polish)
**Cluster:** Standalone (Program-tab surface).
**Outcome-only:** Describes the end result and user experience; does not prescribe implementation.

## Context
On the Program tab, the **"Why your program changed"** rationale card is shown fully expanded whenever the week's plan carries a non-blank model rationale. It is hidden entirely when there is no rationale.

## What the user wants (end result)
The card should be **collapsed by default** — its header/title visible, its body hidden — and expand when tapped. It should **always start collapsed** on each visit (no persisted expand state).

## Acceptance criteria (Done when …)
- When a non-blank rationale exists, the card appears **collapsed**: the "Why your program changed" header is visible; the rationale body text is hidden.
- Tapping the header **expands** the card to reveal the rationale text (and it can be collapsed again).
- Every time the user arrives at the Program tab, the card **starts collapsed**, regardless of any prior expand/collapse state (no persistence).
- When **no** rationale exists, the card stays **hidden** entirely (unchanged).

## Scope and constraints
- **In scope:** the presentation/interaction of the existing rationale card.
- **Out of scope:** the rationale's content or how it is generated.

## Decisions baked in
- Collapsed by default; always starts collapsed each visit (no persistence — confirmed).

## Assumptions (user may override)
- **[A11-1]** A visible affordance (e.g. a chevron or tap hint) signals that the card is expandable.

## Considerations for whoever builds it
- Touches `fragment_program.xml` and `ProgramFragment.kt` (the rationale card and its observer).
- **Program-tab surface hazard:** items 4 (dialog edit), 8 (loading view), and 10 also touch the Program tab surface — see the INDEX cross-group hazard note.

## Standing constraints
- Build with `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.
