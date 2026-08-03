# Brief 01 — Flagged exercises stop showing the mismatched pictures and description

Type: **Feature** (small; direct follow-up to v1.30.0's db-mismatch flagging)
Cluster: independent — no file overlap with brief 02

> Outcome-only brief. Describes the end result and user experience — the "how" belongs to the orchestrator and its workers.

## Context

v1.30.0 (shipped 2026-08-03) added database-mismatch flagging: from the exercise-info sheet (`ui/log/ExerciseInfoBottomSheet.kt`, opened from Program, active workout, and History), the user can flag "this database info doesn't match this exercise." Flags/overrides persist in `data/ExerciseInfoCorrections.kt`; a debug screen (Settings → About → Debug → Flagged Matches, `ui/settings/SettingsFlaggedFragment.kt`) offers unflag, re-match (app-wide override that auto-clears the flag), and copy. As shipped, flagging records the pair but **the sheet keeps displaying the wrong DB info**. Prior brief: `docs/intake/qol-batch-2026-08-03/brief-04-db-mismatch-flagging.md`.

## What the user wants (end result)

Once an exercise is flagged, the mismatched database content stops being shown — **everywhere** the info sheet opens (active workout, Program screen, History), not only mid-workout (user confirmed "everywhere"). Hidden content = everything sourced from the mismatched DB entry: the picture(s), the instructions, and the DB metadata lines (muscles, equipment, level/category, "From database: …" label).

What the sheet still shows for a flagged exercise:

- The exercise name (title).
- The AI **"Coach's note"** when one exists — user explicitly asked for this ("show the AI notes"); it comes from the plan, not the mismatched DB entry, so it stays trustworthy.
- The **performed-sets section** when opened from History (also not DB-sourced).
- A short line explaining the info is hidden because the match was flagged, keeping the existing pointer to Settings → About → Debug → Flagged Matches.

Lifecycle stays as shipped: **re-match** in the debug screen restores full (corrected) info automatically; **unflag** restores the original display.

## Acceptance criteria

- Done when a flagged exercise's info sheet shows none of the mismatched DB entry's pictures, instructions, or metadata — from every entry point that opens the sheet.
- Done when the sheet still shows the exercise name, the Coach's note when present, and (History path) the performed-sets section.
- Done when the sheet tells the user the info is hidden due to the flag and where to manage it.
- Done when re-matching in the debug screen brings full corrected info back (flag auto-cleared), and unflagging brings the original display back.
- Done when unflagged exercises are completely unaffected.
- Done when the hidden state survives app restart (flags already persist).

## Scope and constraints

- In scope: the info sheet's flagged-state display, at all entry points.
- Out of scope: the flagging mechanism, debug screen, persistence, and backup handling (all shipped and unchanged); the resolver's automatic matching.
- Standing: build via `./build.sh`; no commits/releases unless asked; no on-device tests unless asked.

## Decisions made under delegation (veto-able)

- D1: exact wording and layout of the hidden state are the builder's choice (user delegated UI shape; the elements listed above are fixed).
- D2: whether the name-keyed *static catalog* fallback info (`ExerciseCatalog.getEntry(name)` — a different source than the flagged DB match) may show for a flagged exercise is the builder's judgment call. Safe default: hide it too and show only the explanation line — the user flagged "wrong pictures/instructions" and a wrong static entry would repeat the offense.

## Considerations for whoever builds it

- The sheet must keep opening for flagged exercises (the user chose "show name + coach's note + explanation" over "don't open at all").
- The flag prompt row itself ("Flagged — manage under …") already handles the flagged state; the new behavior extends around it, including flagging *from within the open sheet* — the DB content should disappear immediately when the user taps the flag, not only on next open.
- The image cycler (`imageHandler` alternating frames) must not start for flagged exercises.
