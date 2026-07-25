# Feature Batch 2026-07-03 — INDEX

**Prepared for:** Project-lead orchestrator
**Source:** Research round `RESEARCH.md` (this folder); the user picked the items from that menu — selection relayed verbatim via coordinator: "do N1 N3 N4 N5 N7 B1 B7 b10 b11 b5".
**Status:** Selection CONFIRMED by the user (their own words, relayed verbatim). Remaining product calls are **labelled assumptions** (A-XXx) the user can veto — the two the user explicitly asked about are additionally marked DEFERRED DECISION. Producing these documents is **intake only** — NOT a dispatch; the coordinator dispatches the orchestrator separately.
**App state at intake:** v1.24.1 (main = 3b05561), 840 unit tests, DB v18, backup v5.

## Not in this batch
- **B3 (rep/e1RM PRs)** and **B6 (streak freeze)** — offered, **not picked**. Do not build.
- **N2 (rest & pacing insights)** — user asked "is n2 not already implemented?" — **answer: yes, essentially shipped** in v1.24.0's Recap overhaul: `SessionRecap.SessionPacing` derives avg rest from per-set `loggedAtMs` gaps, compares against the plan's prescribed rest, detects idle (>5 min) and long pauses, and Recap shows REST & PACING plus DURATION planned-vs-actual. The only remaining delta from the N2 pitch is a **per-exercise** rest breakdown (today it's one session-level average). Not in this batch unless the user asks for that delta.

## Items

| ID | Title | Type | Cluster | Brief file | Status |
|----|-------|------|---------|------------|--------|
| N1 | Pre-workout "numbers to beat" on Home | Feature | Home (first) | `brief-N1-home-targets.md` | Ready |
| N3 | Relative strength (e1RM ÷ body weight) | Feature | Progress (before N5) | `brief-N3-relative-strength.md` | Ready |
| N4 | Logged effort trends feed generation | Feature (prompt-side) | Standalone | `brief-N4-effort-into-generation.md` | Ready |
| N5 | Goal targets for the big lifts | Feature (new mechanic) | Progress + Home + Schema | `brief-N5-goal-targets.md` | Ready |
| N7 | Per-exercise setup notes | Feature | Log screen (with B1) + Schema | `brief-N7-exercise-setup-notes.md` | Ready |
| B1 | Warm-up ramp suggestions | Feature | Log screen (with N7, B1 first) | `brief-B1-warmup-ramp.md` | Ready |
| B5 | Rest-day active recovery card | Feature | Home (after N1) | `brief-B5-rest-day-recovery.md` | Ready |
| B7 | Monthly "Wrapped" recap | Feature | Recap owner + Home card | `brief-B7-wrapped-recap.md` | Ready |
| B10 | Widget: streak + challenge progress | Feature | Standalone | `brief-B10-widget-upgrade.md` | Ready |
| B11 | Level titles past 20 | Feature (cosmetic) | Standalone filler | `brief-B11-level-titles.md` | Ready |

## Merge / cluster + parallelization guidance
Grounded in the files each item touches on the v1.24.1 tree.

**Cluster L — logging screen (B1 → N7): ONE worker, in that order.** Both edit `LogWorkoutFragment`/`LogWorkoutViewModel`/the log layout — the app's busiest surface, rewritten repeatedly in recent batches. B1 (bigger UI footprint, uses `ui/log/PlateMath` + active GymPreset) first; N7's quiet note line second. N7's schema part is coordinated under Cluster S.

**Cluster S — schema/backup seam (N5 + N7): ONE coordinated migration.** Both add persistent user data → DB v18→v19 and backup v5→v6 should happen **once**, covering the goals table (N5) and the exercise note (N7), with one registered backup step (`BackupModels` pattern). Do not let two workers race on `AppDatabase`/backup files: either land a schema-first unit both build on, or serialize whichever lands second onto the first's migration. Restore-of-old-backups AC applies to both.

**Cluster P — Progress chart (N3 → N5's target line): same surface (`HistoryProgressFragment`).** N3 first, then N5's goal line — or one worker takes both chart changes.

**Cluster H — Home surface (N1 → B5 / B7's card / N5's nudge).** Four items add to Home. N1 (today-card targets) lands first; the other three are additive cards/nudges — serialize their landings on the Home files (any order among B5/B7/N5 is fine; pick one deliberate card-stack order: today card w/ targets → goal nudge → Wrapped-ready → rest-day card, the last only on rest days).

**Standalones:** N4 (`AiRepository` — sole prompt item in the batch, unit-verifiable, frugal-API note in brief), B10 (widget), B11 (title map — safe filler for any worker).

### Suggested order
1. **Wave 1 (parallel):** Cluster L (B1→N7 UI), N3, N4, B10, B11, N1.
2. **Wave 2:** N5 (after N3 on Progress, after N1 on Home, schema via Cluster S), B7 and B5 (Home cards after N1; B7 also owns Recap).
3. Cluster S migration lands once, wherever the orchestrator schedules it — before or with the first of N5/N7 to persist data.

| Group | Items | One worker? | Note |
|-------|-------|-------------|------|
| L | B1 → N7 | Yes | Logging screen owner; order mandatory |
| S | N5 + N7 schema | Coordinated | ONE migration (v19) + ONE backup bump (v6) |
| P | N3 → N5 chart | Yes or serialize | Progress fragment |
| H | N1 → B5/B7/N5 bits | Serialize landings | Home card stack, N1 first |
| — | N4, B10, B11 | Own/any worker | Independent |

## Confirmed decisions
- The 10 selected items above (user's verbatim pick). B3/B6 explicitly not picked.
- Standing user rules honored throughout: **first-ever lifts are baselines, never PRs**; **muscle balance in Stats stays high-level**; **frugal live-API** (N4/A-G3 written to respect it); creative-freedom grant covers gamification flavor (B10/B11).

## Deferred decisions (user asked; defaults applied, veto any time)
- **A-Y3 (B7):** no share/export of Wrapped in v1 (user hasn't answered the share-appetite question; a later yes adds export without rework).
- **A-G3 (N5):** the AI does not see active goals in v1 (a later yes becomes a small prompt follow-up under frugal-API rules).

## Assumptions applied (all labelled in the briefs; veto any)
- N1: A-N1a (quiet muted target line on Home).
- N3: A-R1 (milestones chart-only, no achievements/XP), A-R2 (weighted lifts only).
- N4: A-E1 (effort = soft context, no new validator rules), A-E2 (existing trends lookback).
- N5: A-G1 (celebration, no XP in v1), A-G2 (reach = logged working set / e1RM meets target), A-G4 (managed from exercise context + Profile list), A-G5 (date is flavor, no failure state).
- N7: A-S1 (one short note per exercise), A-S2 (attaches to existing exercise identity, incl. custom).
- B1: A-W1 (≈40/60/80% ladder w/ sensible skips), A-W2 (applicability = existing heavy-compound classification), A-W3 (based on today's planned first-working-set weight).
- B5: A-A1 (small built-in static catalog, no AI), A-A2 (text-only), A-A3 (per-day dismiss + permanent off-switch in App Settings).
- B7: A-Y1 (monthly cadence), A-Y2 (lives in Recap area + Home ready-card).
- B10: A-D1 (upgrade the one existing widget).
- B11: A-L1 (builder names titles under the creative-freedom grant).

## Cross-cutting constraints
- Build via `./build.sh`; verify with build + unit tests only. **No Waydroid/on-device/automated UI testing** — the user verifies on-device.
- **No commits or releases unless explicitly authorized.**
- **Frugal live-API:** N4 is the only API-adjacent item; prompt inclusion is unit-verified, quality judged by the user in normal use — no live A/B sweeps.
- Backup-merge parity: nothing in this batch adds an XP source (A-G1 deliberately avoids it), so `StatsRecomputer` should need no change — if any worker finds otherwise, stop and flag.
- Day/period boundaries use the app's logical-day + Monday-week conventions (`DayBoundary`) — prior UTC bugs make this a known trap (B7 especially).
