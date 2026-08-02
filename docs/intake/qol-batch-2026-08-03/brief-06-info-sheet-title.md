# Brief 06 — Exercise-info sheet: title = program name, DB name above DB content

Type: **Feature** (small)
Cluster: Group A with item 04 (same sheet — one worker)

> Outcome-only brief. Describes the end result and user experience — the "how" belongs to the orchestrator and its workers.

## Context

The exercise-info sheet (`ui/log/ExerciseInfoBottomSheet.kt`) receives both the program's exercise name and the resolved DB entry, but its top title currently prefers the database entry's name (`text = dbEntry?.name ?: name`, line ~136). So the heading can differ from the exercise the user actually tapped in their program.

## Current behavior vs desired

- Current: top title shows the database entry's name when a match exists.
- Desired: top title always shows the **program's (AI-generated) exercise name** — the name the user tapped. The database entry's own name appears as a label **right above the database content** (pictures/instructions), so the user can still see what entry the info comes from.

## Acceptance criteria

- Done when the sheet's top title is the program's exercise name from every entry point, matched or not.
- Done when, for a matched exercise, the DB entry's name appears directly above the database-sourced content.
- Done when an unmatched exercise (no DB entry) shows no stray/empty DB-name label.

## Scope and constraints

- In scope: the sheet's title and the new DB-name label only.
- Out of scope: the sheet's content or matching logic (item 04 owns re-matching).
- Standing: build via `./build.sh`; no commits/releases unless asked; no on-device tests unless asked.

## Decisions made under delegation (veto-able)

- D3: when the DB entry's name is identical to the program name, hide the label rather than showing the same string twice.

## Considerations for whoever builds it

- Build together with item 04 (same file). After a re-match (item 04), the label must show the newly chosen entry's name — the two features must agree on where the resolved entry comes from.
