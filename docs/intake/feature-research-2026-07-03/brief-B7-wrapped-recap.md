# Brief B7 — Monthly "Wrapped" recap

**Type:** Feature (motivation / reflection)
**Cluster:** Recap surface owner; Home card lands after N1 (see INDEX)

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
Every input for a period story already exists: per-week summaries (`WeeklySummary`), sets/volume, PR history, streak, achievements with rarity, challenges/Perfect Weeks, body-weight trend. The Recap sub-tab was just rebuilt (v1.24.0) as a session-centric surface with the app's best visual language. Nothing today tells the user what a *month* meant.

## What the user wants (end result)
1. At the end of each calendar month, the app produces a "Wrapped"-style recap: a scrollable, vividly designed story of the month — e.g. sessions completed vs planned, total volume, biggest PR (respecting the baseline rule: first-ever lifts are baselines, not PRs), longest streak, Perfect Weeks, achievements unlocked (with rarity), body-weight trend, favorite/most-improved exercise.
2. It is discoverable, not pushy: a card on Home when a new one is ready, and past months browsable from the Recap area.
3. It reads as celebration and honest reflection — thin months are presented gently (fewer panels), never as shaming.
4. Months with little or no data produce either a graceful mini-recap or nothing at all — never a page of zeros.
5. Pure presentation over existing data: no effect on XP, streaks, challenges, or any mechanic.

## Acceptance criteria
- Done when, after a month with training data ends, its Wrapped is available and every figure matches the app's existing stats for that month (spot-verified in unit tests over fixture data).
- Done when past months are browsable and re-openable, and months before the install/with no data are handled gracefully.
- Done when the Home "ready" card appears once per new month and disappears after viewing/dismissal.
- Done when the baseline-not-PR rule holds in the PR panel.
- Done when generating/viewing Wrapped changes no stored stats, XP, or achievements.

## Scope and constraints
- **In scope:** monthly recap generation from existing data, the viewing surface, Home ready-card, browsable history of past months.
- **Out of scope (v1):** sharing/export as image (**deferred decision** — see A-Y3); quarter/year editions (natural follow-ups once monthly exists); AI-written prose (keep it data-driven; no live API).
- No DB schema change expected (derivable from existing data); if a small "seen/dismissed" marker is needed, preferences are the expected register — flag if anything more is required.
- Standing: build via `./build.sh`; no commits/releases unless asked; no on-device/automated UI tests.

## Assumptions (user may override)
- **A-Y1:** cadence is **monthly** (calendar month, respecting the app's day-boundary rules); quarter/year are out of v1.
- **A-Y2:** lives in the History→Recap area (a period entry alongside session recaps), with the Home card as the discovery moment.
- **A-Y3 (DEFERRED DECISION, user asked as Q4):** **no share/export in v1** — the user hasn't said they want shareable images. A yes later adds an export step on top of this surface without rework.

## Considerations for whoever builds it
- The Recap surface was just rebuilt — extend its established visual language (stat capsules, rarity colors, Auros gradients) rather than inventing a new one.
- Month boundaries must use the app's logical-day/Monday-week conventions (`DayBoundary`) — the codebase has had UTC-vs-logical-day bugs before.
