# Feature Research & Gamification Overhaul — 2026-07 — INDEX

**Prepared for:** Project-lead orchestrator
**Source:** User-mandated research sweep ("thorough research of the app … features optimal for training, motivating for the user, or other" + "review of today's gamifying objects and improve these"), relayed via coordinator.
**Status:** Direction CONFIRMED by the user before sleep (Q1–Q4 below, answers relayed verbatim); document creation **pre-authorized without a final confirmation round**. Every remaining product decision is a **labelled assumption** the user can veto in the morning.
**App state at intake:** v1.21.0, DB v18. **This batch is the SECOND release tonight** — the rest-ux batch (`docs/intake/rest-ux-batch-2026-07/`) ships first; several briefs here touch the same files and must build on the post-batch-1 tree.

## Confirmation note
The user answered four direction questions (relayed verbatim via the coordinator):
- **Q1 — schedule-aware streak:** "good" → R1.
- **Q2 — body-weight features:** "yes do this" → R3.
- **Q3 — notifications:** "sure implement this, but make it possible for the user to disable any specific notification in the settings" → R2 (per-type toggles are a hard requirement).
- **Q4 — gamification scope:** "complete creative freedom" → R4, R5, R6.

Producing these documents is **intake only** — NOT a dispatch. The coordinator dispatches the orchestrator separately.

## Items

| ID | Title | Type | Pillar | Brief file | Status |
|----|-------|------|--------|------------|--------|
| R1 | Schedule-aware streak (rest days don't break it; missed days do) | Feature (mechanics) | Motivation | `brief-R1-schedule-aware-streak.md` | Ready |
| R2 | Notification center — every notification individually toggleable | Feature | Motivation | `brief-R2-notification-center.md` | Ready |
| R3 | Body-weight insights: trend on Home + Progress chart + AI awareness | Feature | Training | `brief-R3-bodyweight-insights.md` | Ready |
| R4 | Weekly challenges 2.0 + Perfect Week bonus | Feature (mechanics) | Motivation | `brief-R4-challenges-2-perfect-week.md` | Ready |
| R5 | Achievement gallery: categories, rarity tiers, visible progress | Feature (presentation) | Motivation | `brief-R5-achievement-gallery.md` | Ready |
| R6 | Workout-complete celebration upgrade (XP count-up, PR numbers, tiered unlock cards) | Feature (presentation) | Motivation | `brief-R6-completion-celebration.md` | Ready |
| R7 | "Beat last time" target chip + live PR flash while logging | Feature | Training + Motivation | `brief-R7-beat-last-time.md` | Ready |
| — | Everything else researched | — | — | `BACKLOG.md` (B1–B13 sketches) | Parked |

## Gamification review — findings that drove R1/R4/R5/R6
(Recorded so the orchestrator understands the *why*; the briefs carry the *what*.)
- **Streak is broken as a motivator:** consecutive-calendar-day rule means planned rest days reset it; 23 streak-related achievements are effectively unreachable for a 3–4 day/week trainee → R1.
- **Challenges go stale:** 3/week drawn from only 12 static templates, none referencing the user's own training; completing a full planned week earns nothing → R4.
- **~200 achievements, flat presentation:** no grouping, no rarity, no progress-to-next — the collection's motivational pull is mostly wasted → R5.
- **The payoff moment is the flattest surface in the app:** a stock alert dialog, while the *surrounding* animations (day-chip bounce, week bar, XP bar, level-up overlay) are already good → R6.
- **PR feedback is delayed and numberless:** detected only at completion, shown without old→new numbers; no explicit "number to beat" while training → R7.
- Healthy and untouched: XP formula and level curve (fine for now; late-game titles parked as B11), XP log, level-up overlay, day-badge/week-bar animation chain.

## Merge / cluster + parallelization guidance
Grounded in the files each item touches (post-batch-1 tree).

**Cluster G-mechanics — R1 → R4: ONE worker, in that order.** Both rewrite the same seam: `GamificationRepository`, `data/backup/StatsRecomputer` (recompute parity is an explicit AC in both), `DailyChallengeManager`, `UserStats` handling. R4's Perfect Week and adaptive targets also read the plan/week progress.

**Cluster G-presentation — R5 → R6: ONE worker, in that order.** R6's unlock cards use R5's tier model. R5 touches achievement metadata (`AppDatabase.PREDEFINED_ACHIEVEMENTS` reconcile) + Profile UI; R6 touches `LogWorkoutFragment.showResultDialog` + a new result surface. Light contention with G-mechanics on `GamificationRepository`/`WorkoutResult` (R6 wants old→new PR weights surfaced) — coordinate the `WorkoutResult` shape change between the two workers or serialize the clusters.

**R2 — own worker.** `notify/*`, `PreferencesManager`, App Settings screen. Conceptually after R1 (streak-at-risk definition) but file-independent enough to run in parallel with everything except the settings screen (untouched by others here).

**R3 — own worker.** Home + Stats/Progress UI + `AiRepository` prompt build. No overlap inside this batch; `AiRepository` was touched by batch-1 (sequential, so rebase only).

**R7 — own worker, LAST.** Logging-screen surface (`LogWorkoutFragment`/`LogWorkoutViewModel`/layout) — the same files batch-1 rewrites tonight and R6 touches for the dialog. Build strictly after batch-1 lands and after R6's dialog change, or fold into the presentation worker's tail.

### Suggested order
1. In parallel: **G-mechanics (R1→R4)**, **R2**, **R3**.
2. **G-presentation (R5→R6)** — start R5 in parallel too; R6 waits for the `WorkoutResult`-shape coordination with G-mechanics.
3. **R7 last** (busiest shared file; smallest item).

| Group | Items | One worker? | Note |
|-------|-------|-------------|------|
| G-mechanics | R1 → R4 | Yes | Streak semantics first, challenges/Perfect-Week on top; StatsRecomputer parity in both |
| G-presentation | R5 → R6 | Yes | Tier model before celebration; coordinate WorkoutResult shape with G-mechanics |
| — | R2 | Own worker | Notification hub; needs R1's streak-at-risk definition |
| — | R3 | Own worker | Body weight; only AiRepository overlap is with already-shipped batch-1 |
| — | R7 | Own worker, last | Logging screen — after batch-1 AND R6 |

## Confirmed decisions
- Streak = plan adherence (Q1). Body-weight expansion incl. AI awareness (Q2). Notifications with **per-type** toggles (Q3 — hard requirement). Full creative freedom on gamification (Q4). Two-release night: this batch builds on the post-rest-ux tree.

## Assumptions applied (user may veto — also labelled in each brief)
- **A-R1/A-R2 (R1):** forward-only streak migration (no retroactive replay / mass unlock); pre-feature history is neutral.
- **A-N1–A-N3 (R2):** defaults — training reminder OFF, streak warning ON, weigh-in OFF, program-ready ON; streak warning at a fixed evening slot; weigh-in weekly, day/time adjustable.
- **A-B1–A-B3 (R3):** smoothed trend; AI gets weight as context (no new hard rules); chart lives in Stats → Progress.
- **A-C1–A-C3 (R4):** Perfect Week = +150 XP; no-plan weeks have no Perfect Week; adaptive targets challenging-but-reachable.
- **A-G1–A-G2 (R5):** four rarity tiers by threshold difficulty; gallery stays under Profile.
- **A-X1–A-X2 (R6):** PR old→new shown going forward only; celebration is in-app, no sounds/share.
- **A-P1–A-P2 (R7):** target/PR = historical best weight (app's existing PR rule); inline moment small, big celebration stays at completion.

## Cross-cutting constraints
- Build via `./build.sh`; verify with build + unit tests only. **No Waydroid/on-device/automated UI testing** — the user verifies on-device.
- **No commits or releases unless explicitly authorized** for tonight's two-release plan; this batch ships **second**, after rest-ux.
- **Frugal live-API testing** (R3's generation behavior is the only live-gen-sensitive item; prompt inclusion is unit-verifiable).
- Any new XP source must keep the **backup-merge stats recompute** reconciling (explicit ACs in R1/R4).
- Keep the gamification layer **vivid** — established user preference; tone down nothing.
