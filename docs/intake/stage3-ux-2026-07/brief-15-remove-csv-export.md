# Brief 15 — Remove "Export CSV" from the Stats page

**Type:** Feature (removal)
**Cluster:** H3 (Stats tab: 11 + 15) — one worker.

> Outcome-only brief. No confirmation round was possible (user asleep); assumptions are labelled.

## Context
The Stats sub-tab has an Export button (`HistoryStatsFragment.btnExport` → `HistoryViewModel.exportCsv` → share intent) that shares the workout history as CSV text. The full-fidelity data path is the JSON backup export/import in Settings → Backup & Data, which stays.

## What the user wants (end result)
The CSV export control is **gone from the Stats page**. Settings' backup export remains the way to get data out.

## Acceptance criteria
- Done when the Stats sub-tab shows no export button and its layout closes up cleanly.
- Done when Settings → Backup & Data export/import is untouched.
- CSV-only code left unreferenced by this removal may be deleted.

## Scope and constraints
- **In scope:** the Stats page's export control (+ its now-dead plumbing).
- **Out of scope:** the JSON backup system; any other Stats content.

## Assumptions (user may veto)
- **A-15a:** the CSV capability is removed outright (not relocated) — the user said "remove", and the JSON backup covers data portability.
