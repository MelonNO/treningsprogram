---
name: feature-research-2026-07
description: Post-v1.21.0 research-mandate batch at docs/intake/feature-research-2026-07/ (R1-R7 + BACKLOG B1-B13) — schedule-aware streak, notification center, body-weight insights, challenges 2.0, achievement gallery, celebration upgrade, beat-last-time
metadata:
  type: project
---

Confirmed 2026-07-02 (post-v1.21.0, DB v18), docs at `docs/intake/feature-research-2026-07/`. Unusual intake mode: user going to bed **pre-authorized document creation without a final confirmation round** — direction settled via 4 questions (relayed verbatim), all remaining product calls written as labelled vetoable assumptions. Ships as the SECOND release of a two-release night (after [[rest-ux-batch-2026-07]]).

**Why:** user mandated a thorough app research sweep (training-optimal + motivating + other features) plus a review-and-improve of the gamification layer.

**Settled directions (don't re-ask):**
- **Streak = plan adherence** (Q1 "good"): planned/auto REST days never break the streak; auto-logged MISSED planned day does; display must be fresh without a workout. Forward-only migration (A-R1).
- **Body-weight expansion approved** (Q2): trend on Home, chart in Stats→Progress, AI prompt gets weight+trend. NOT approved: weight in volume/XP math.
- **Notifications approved with a hard requirement** (Q3): EVERY notification type individually toggleable (user example: only streak warning on). Build on v1.20.0 reminder machinery (ReminderScheduler/WorkoutReminderReceiver).
- **Complete creative freedom on gamification** (Q4) — standing grant; user keeps the layer deliberately vivid.

**Gamification audit findings (reusable):** streak was consecutive-calendar-day (broken motivator, ~23 streak achievements unreachable at 3-4 days/wk); challenges = 3/ISO-week from only 12 static templates, nothing data-driven, no full-week reward; ~200 achievements shown as flat collapsed list, no rarity/progress-to-next; workout-complete = stock MaterialAlertDialog while surrounding animations are good; PRs detected only at completion, numberless. XP formula/level curve healthy (titles cap at 20+ "Transcendent" — backlog B11).

**Clusters:** G-mechanics R1→R4 (GamificationRepository + StatsRecomputer + DailyChallengeManager; recompute-parity AC), G-presentation R5→R6 (tier model before celebration; WorkoutResult shape coordination w/ mechanics), R2/R3 own workers, R7 LAST (logging screen, after batch-1 AND R6).

**Key hazard recorded:** any new XP source must keep backup-merge StatsRecomputer reconciling (it replays sessions+sets only today).
