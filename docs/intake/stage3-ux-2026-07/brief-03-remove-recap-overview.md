# Brief 03 — Remove the overview graphs from the Recap tab

**Type:** Feature (removal)
**Cluster:** H1 (Recap overhaul: 3 → 9 → 14 → 10) — one worker, this first.

> Outcome-only brief. No confirmation round was possible (user asleep); assumptions are labelled.

## Context
The Recap sub-tab (`HistoryRecapFragment`) renders two things: the per-session recap (header, deltas, PRs, muscles, effort, adherence, duration, pacing) and — above/around it — an aggregate **overview section** (`renderOverview`: header text + "volume over time", "training frequency", and "muscle distribution" cards built from `RecapGraphs`). The Stats sub-tab has its own aggregate dashboard, so the overview duplicates the "whole history" role on a screen meant for one session.

## What the user wants (end result)
The overview graphs and their accompanying information are **gone from Recap**. Recap is purely the per-session view: pick a session, see that session. The Stats tab keeps all its aggregates untouched.

## Acceptance criteria
- Done when the Recap sub-tab shows no aggregate overview cards or overview header — only the session picker and the selected session's recap.
- Done when no dead gap remains where the section was, and the default (latest-session) load still works.
- Done when the Stats tab is byte-for-byte unaffected.
- Dead code left unreferenced by this removal (overview-only rendering paths) may be cleaned up; `RecapGraphs` logic shared with anything else must survive.

## Scope and constraints
- **In scope:** the Recap sub-tab's overview section only.
- **Out of scope:** the per-session recap content (items 9/10/14 reshape that); Stats tab.

## Assumptions (user may veto)
- **A-03a:** "and information" = the overview section's header/explanatory text that belongs to those graphs — nothing from the per-session recap itself is removed.
