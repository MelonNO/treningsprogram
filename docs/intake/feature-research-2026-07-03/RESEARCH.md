# Feature Research — 2026-07-03 — CANDIDATES (not briefed, not confirmed)

**Prepared for:** the user (candidate menu) — briefs are written only for the items the user selects.
**App state at research:** v1.24.1 live (main = 3b05561), 840 unit tests green, DB v18. Researched immediately after the four-release night (v1.22.0 rest UX, v1.23.0 R1–R7 feature batch, v1.24.0 sixteen UX items, v1.24.1 History fix).
**Status:** RESEARCH ONLY. Nothing here is scoped, decided, or dispatched.

**Constraints every candidate already respects:**
- First-ever lifts are **baselines, never PRs** (ratified 2026-07-03; already encoded in `BeatTarget`/`isWeightPr`).
- Muscle balance in Stats stays **high-level** (fine muscle labels live in Recap only, per user).
- **Frugal live-API** usage; **no Waydroid/on-device testing** in pipelines.
- Backup-merge parity: any new XP source or stats field must keep `StatsRecomputer` reconciling.

Grounding notes: every "exists"/"doesn't exist" claim below was verified in the v1.24.1 tree at research time (files named where it matters).

---

## Part 1 — Backlog re-evaluation (B1–B13 from `feature-research-2026-07/BACKLOG.md`)

| ID | Verdict | Why |
|----|---------|-----|
| B1 warm-up ramp suggestions | **PROMOTE** | Its parking reason is gone: the logging screen was contended by three batches that night — all have landed and settled (rest UX, R7 chip, keypad polish). `PlateMath`/GymPreset (50 mm home bar) and the warm-up chip all exist to build on. Still needs the user's ramp philosophy (percent steps, set count). Size M, no schema, no live API. |
| B2 stall/RPE into generation | **HALF-SHIPPED — re-scope** | Verified: stalled lifts are now named in the prompt (stall block + deload context in `AiRepository`), so the stall half is done. The remaining half: logged per-set effort (`rpeLabel`) is **not** fed — history lines are `reps×weight` only. Re-scoped as candidate **N4** below. |
| B3 rep-PR and e1RM-PR types | **PROMOTE (with a decision)** | Stronger fit now than at parking time: the "beat last time" chip + live PR flash (R7) give rep-PRs an obvious home, and the PR rule lives in one clean seam (`BeatTarget` ↔ `GamificationRepository.isWeightPr`, locked by a shared test). Baseline-not-PR rule carries over unchanged. The open decision is rebalance: do rep-PRs earn full PR XP/celebration or a lighter "rep best" moment? Size M, no schema, no live API. |
| B4 more body measurements | **KEEP PARKED** | `BodyMeasurement` is still weight-only. Fine foundation, but it's schema + backup v5→v6 + chart surface for a feature the user hasn't asked for. Promote only on explicit interest. |
| B5 rest-day active recovery | **KEEP PARKED (slightly stronger fit)** | Recovery view knows soreness and the notification center now has per-type toggles (so it can be opt-in, not nagging). Still a content-source and tone question the user must want first. |
| B6 streak insurance ("freeze") | **PROMOTE (as a user decision)** | Pairs naturally with what shipped: earn one freeze per Perfect Week; it auto-covers a missed planned day. But it deliberately softens the schedule-aware streak semantics the user just approved — pure product call. Size S/M; touches `UserStats` + **StatsRecomputer parity hazard**. |
| B7 year/quarter "Wrapped" recap | **PROMOTE** | The Recap surface was just overhauled (v1.24.0), and every input exists (`WeeklySummary`, stats, sets, PRs, streak, achievements). A monthly/quarterly story is self-contained and zero-risk to mechanics. Size M, no schema, no live API (optionally one AI-written closing line — skippable). |
| B8 share cards | **ASK APPETITE, else park** | Still zero signal that the user wants social sharing. One question settles it; if yes, it composes well with B7/Wrapped and the celebration surface. |
| B9 quest chains / seasonal events | **KEEP PARKED** | Challenges 2.0 + Perfect Week shipped *tonight* — let the adaptive pool bed in before layering multi-week narratives on it. |
| B10 widget upgrade | **PROMOTE** | Verified: `TodayWorkoutWidgetProvider` still shows only today's plan — no streak flame, no challenge progress. Small, self-contained, nudges without notifications. Size S, no schema, no live API. |
| B11 level titles past 20 | **PROMOTE (cosmetic filler)** | Verified: titles still cap at "Legend" (15–19) / "Transcendent" (20+). Cheap fun; good batch filler. Size S. |
| B12 cloud backup OAuth | **KEEP (not code work)** | `data/cloud` (DriveBackupClient/GoogleDriveAuth) still gated on the unconfigured Google OAuth client ID. Needs a live console session with the user, not a worker. Candidate **N5** (auto local backup) meaningfully reduces its urgency. |
| B13 exercise demo media | **DROP — superseded** | Verified: 873 images now ship in `assets/exercise_db/` and the library detail plays the two-frame animation (v1.24.0 item 7). Only re-open if the user wants true video/GIF, which re-raises the licensing/size questions. |

---

## Part 2 — New candidates

### Theme A — Train smarter (mine the data the app already logs)

**N1 — Pre-workout "numbers to beat" on Home. (S)**
Before a workout, Home lists today's exercises but not the targets; the "beat last time" number only appears once you're inside the logging screen (R7). This surfaces the same `BeatTarget` figure — last best weight, plus last session's top set — as a small line under each exercise on Home's today card, so the user walks up to the bar already knowing the goal. Motivation-wise it converts the PR chip from a mid-set surprise into an intention. Builds entirely on the existing `BeatTarget`/`RecentPrs` seam; first-ever lifts show nothing (baseline rule). No schema, no live API. Risk: Home card real estate — needs a compact treatment.

**N2 — Real rest & pacing insights in Recap. (M)**
Every set already stores `loggedAtMs`, but nothing reads it: the app knows *actual* time between sets and how a session was paced, and shows none of it. This adds a pacing block to the session Recap — actual median rest per exercise vs the configured target (v1.22.0's manual per-category rest times), longest stall, and a session pace line. It closes the loop the rest-time feature opened: "you set 3:00 for heavy compounds; you actually took 2:10". No schema (data is already logged), no live API. Risks: legacy rows have `loggedAtMs = 0` (must degrade gracefully); derived rest is noisy (phone set down, superset-style logging) so presentation must round/hedge, not pretend precision.

**N3 — Relative strength: lifts per kg of body weight. (S/M)**
Weigh-ins and e1RM machinery (Epley) both exist, and tonight's body-weight insights made BW a first-class citizen — but the two lines never meet. This adds a relative-strength view for the big lifts: e1RM ÷ body weight as a trend, with optional classic milestones (1.0× BW squat, 0.75× BW bench…). For a home lifter tracking both weight and strength it answers the real question — "am I getting stronger, or just heavier?". Lives in Stats → Progress next to the BW chart; this is strength-vs-BW, not muscle balance, so the high-level-balance rule is untouched. No schema, no live API. Decision to flag: whether milestones also become achievements (XP rebalance) or stay chart-only.

**N4 — Effort trends into generation (B2's remaining half). (S, live-API-sensitive)**
The prompt now names stalled lifts, but the AI still can't see how *hard* the work felt: logged per-set `rpeLabel` never reaches the history block. This adds a compact effort line per trending lift (e.g. "Bench press: recent working sets mostly 'hard', trending up") so generation moderates progression on grinding lifts and pushes ones logged easy — sharper than stall-only signals, and it makes the per-muscle effort-scaled recovery story consistent end-to-end. Prompt-side only; inclusion is unit-verifiable. Risk: actual behavioral gain is live-gen-iterative — under the frugal-API rule the honest framing is "wire it verifiably, let the user judge quality on-device".

### Theme B — Motivation

**N5 — Goal targets for the big lifts. (M, small schema)**
The app measures everything and aims at nothing: there is no way to say "100 kg bench by October". This adds per-exercise goal targets (weight or e1RM, optional date) shown as a target line on the existing strength chart with a progress percentage, a Home nudge when one gets close, and a proper celebration on reach — reusing the R6 celebration surface. It complements the streak/challenge loop (which rewards showing up) with a long-arc pull (what you're training *toward*), which is exactly the layer the gamification stack still lacks. Needs a small new table (or PlannedExercise-adjacent entity) → DB migration + backup surface + StatsRecomputer awareness if goal-reach grants XP. No live API. Decisions to flag: XP on goal reach, and whether the AI prompt should see active goals (would make generation goal-aware — live-API-sensitive like N4).

*(Backlog promotions B3 — rep-PRs, B6 — streak freeze, B7 — Wrapped, B10 — widget, B11 — titles also serve this theme; see Part 1.)*

### Theme C — Utility & data safety

**N6 — Automatic local safety backup. (S/M)**
Backup v5 is solid but entirely manual, and cloud backup stays gated on the OAuth console session (B12). One forgotten export before a lost/broken phone erases months of logs — the single worst outcome this app can have. This writes a rolling automatic backup (e.g. after each completed workout, keep last N) to user-visible storage via the existing backup pipeline, with the restore picker listing them newest-first, and a Settings row showing "last auto-backup: today 18:42". No schema, no live API, reuses the proven v5 format. Risk: storage-access UX (SAF folder grant once). Also honestly reframes B12: with this in place, Drive OAuth becomes nice-to-have rather than the only safety net.

**N7 — Per-exercise setup notes ("gear memory"). (S/M, small schema)**
Session notes exist, but there is nowhere to persist per-exercise setup: bench pin height, seat position, band color, "belt on top sets". Home lifters re-derive this every week. This adds one small persistent note per exercise, editable from the logging screen and library detail, shown as a quiet single line under the exercise name while logging. Small schema addition (column on `Exercise` or a tiny table) → DB migration + backup surface. No live API, no gamification interaction. The kind of unglamorous feature that gets used every single session.

---

## Part 3 — Suggested shortlist shapes (purely to make choosing easier)

- **"Close the loops" batch (no schema, low risk):** N1 + N2 + N3 + B10 + B11 — everything tonight's features set up, finished.
- **"Long-arc motivation" batch:** N5 + B3 + B7 (+ B6 if the freeze decision goes yes) — adds the goal/aspiration layer the streak/challenge loop lacks.
- **"Quiet quality" batch:** N6 + N7 + B1 — data safety and every-session utility.
- N4 fits any batch as a small prompt-side add-on (flagged live-API-sensitive).

## Part 4 — Questions for the user (none block selection)

1. **Appetite & theme:** another multi-item batch or a couple of focused features? Which pull is strongest — training insight (A), motivation (B), or utility/safety (C)?
2. **B3 decision:** should a rep PR (more reps at the same weight) count as a *full* PR — XP, celebration, chip flash — or as a lighter "rep best" moment below weight PRs?
3. **B6 decision:** does an *earned* streak freeze (one per Perfect Week, auto-covers one missed planned day) fit your streak philosophy, or should the streak stay strict as approved?
4. **B8 appetite:** any interest at all in shareable images (PR cards, Wrapped)? A plain no parks it permanently.
5. **N5 sub-decision (only if N5 is picked):** should the AI see your active goals when generating programs?
6. **B12:** want to schedule the Google-console OAuth session sometime, or does automatic local backup (N6) cover the need for now?
