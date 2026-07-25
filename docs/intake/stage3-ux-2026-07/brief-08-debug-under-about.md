# Brief 08 — Move Debug inside the About submenu

**Type:** Feature (IA move)
**Cluster:** T (Settings: 6 + 8) — one trivial worker.

> Outcome-only brief. No confirmation round was possible (user asleep); assumptions are labelled.

## Context
Debug currently lives as a row inside **Backup & Data** (`SettingsBackupFragment`, moved there in v1.18.0; the Backup row's subtitle reads "Export, import, reset, and debug").

## What the user wants (end result)
Debug lives inside **About** instead. Backup & Data no longer mentions or contains it.

## Acceptance criteria
- Done when About contains a Debug entry that opens the existing Debug screen (screen content unchanged).
- Done when Backup & Data no longer shows the Debug row and its top-level subtitle no longer says "debug".
- Done when navigation (including back behavior and the Profile-tab-returns-to-root rule) works for the new path.

## Scope and constraints
- **In scope:** the row's location + affected subtitles.
- **Out of scope:** the Debug screen itself; anything else in Backup & Data or About.

## Assumptions (user may veto)
- **A-08a:** Debug is appended as the last item within About (dev/diagnostic entries read naturally at the bottom).
