# Brief 04 — Database-mismatch flagging + debug list with re-match / unflag / copy

Type: **Feature** (largest item of the batch)
Cluster: Group A with item 06 (same sheet — one worker)

> Outcome-only brief. Describes the end result and user experience — the "how" belongs to the orchestrator and its workers.

## Context

Pressing an exercise (Program screen, and other entry points) opens the exercise-info sheet (`ui/log/ExerciseInfoBottomSheet.kt`) showing pictures/instructions resolved from the exercise database (`ExerciseDbResolver` maps plan names → DB entry ids). Sometimes the resolver matches the wrong entry — e.g. an exercise showing another movement's pictures. There is an existing debug menu (`ui/settings/SettingsDebugFragment`).

## What the user wants (end result)

1. **Flagging.** Everywhere the exercise-info sheet can be opened (Program screen, during an active workout, any other entry point), the sheet offers an option to flag "this database info doesn't match this exercise." Flagging records the pair *(exercise name → database entry it was wrongly matched to)*. Nothing else changes at flag time — info keeps displaying as before.
2. **Debug list.** The debug menu shows the list of flagged exercises. Per entry the user can:
   - **Unflag** — remove the entry from the list.
   - **Re-match** — search the exercise database by name and pick the correct entry. The new match takes real effect: from then on, that exercise's info sheet shows the chosen entry's pictures/instructions (everywhere the sheet opens). A successful re-match **auto-clears the flag**.
3. **Copy.** The whole list is easily copy-pastable — one tap copies it as text (exercise name and what it was mismatched to).

## Acceptance criteria

- Done when the flag option appears in the exercise-info sheet from every entry point that opens it, and flagging from any of them lands the pair in the debug list.
- Done when flagging changes nothing else in the app at flag time.
- Done when the debug list shows every flagged pair and supports unflag per entry.
- Done when re-match offers a working search over the exercise database, and picking an entry (a) changes what the info sheet shows for that exercise from then on, everywhere, and (b) removes the flag automatically.
- Done when one tap copies the full list to the clipboard as readable text.
- Done when flags and re-match overrides survive app restart.

## Scope and constraints

- In scope: flag action in the sheet, persistence of flags and re-match overrides, the debug-menu list with its three actions, clipboard copy.
- Out of scope: improving the resolver's automatic matching itself (the flag list exists precisely to collect its failures); any non-debug surfacing of the list.
- Standing: build via `./build.sh`; no commits/releases unless asked; no on-device tests unless asked.

## Decisions made under delegation (veto-able)

- D4: copy format (suggest plain text, one `name -> matched-entry` per line) and the search UI's shape are the builder's choice.

## Assumptions (user may override)

- A1: flags and re-match overrides are persistent user data and should be included in backup/export-import like other persistent data (backup-format version bump if that's what the format requires).

## Considerations for whoever builds it

- A re-match override must win over `ExerciseDbResolver`'s automatic mapping wherever the DB id is resolved, so the corrected info appears from every entry point — not just one screen.
- Item 06 (title/label change) edits the same sheet — one worker builds both.
- Flagging when the sheet has *no* DB match at all (nothing resolved): record the pair with an explicit "no match" marker rather than blocking the flag.
