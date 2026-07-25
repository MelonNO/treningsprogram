---
name: feature-research-2026-07-03
description: Confirmed post-v1.24.1 10-item batch at docs/intake/feature-research-2026-07-03/ (N1,N3,N4,N5,N7,B1,B5,B7,B10,B11); B3/B6/N6 NOT picked; N2 was already shipped in v1.24.0 Recap
metadata:
  type: project
---

Research round → confirmed batch, 2026-07-03 (post-v1.24.1, main=3b05561, 840 tests, DB v18, backup v5). `RESEARCH.md` + `INDEX.md` + 10 briefs at `docs/intake/feature-research-2026-07-03/`. Follows [[feature-research-2026-07]].

**Selection (user verbatim, relayed):** "do N1 N3 N4 N5 N7 B1 B7 b10 b11 b5". **NOT picked: B3 rep-PRs, B6 streak freeze, N6 auto local backup** — don't re-pitch these soon; the data-safety pitch (N6) didn't land this round.

**N2 lesson:** user asked "is n2 not already implemented?" — it WAS (v1.24.0 Recap: `SessionRecap.SessionPacing` from loggedAtMs gaps, idle>5min ceiling, target = plan's recommendedRestSeconds averaged; REST & PACING + DURATION rows in Recap). My research grep missed the WorkoutRepository pacing code. **How to apply:** before pitching a data-mining candidate, grep for consumers of the field across repository/domain/model, not just domain/. Only remaining N2 delta: per-exercise rest breakdown (offered, not requested).

**Batch shape (in INDEX):** Cluster L = B1→N7 one worker (logging screen; PlateMath at `ui/log/PlateMath.kt`); Cluster S = N5+N7 ONE coordinated migration DB v18→v19 + backup v5→v6; Cluster P = N3→N5 chart on HistoryProgressFragment; Cluster H = Home stack N1 first then B5/B7-card/N5-nudge serialized; standalones N4 (sole AiRepository item), B10 widget, B11 titles.

**Deferred decisions (defaults applied, user may flip):** A-Y3 = no Wrapped share/export v1 (Q4 share-appetite unanswered); A-G3 = AI does NOT see goals v1 (Q5 unanswered); Q6 (B12 OAuth session) unanswered.

**Design calls to keep consistent in follow-ups:** batch adds ZERO new XP sources by design (N5 goals = celebration, no XP; N3 milestones chart-only — StatsRecomputer untouched); B5 = built-in static catalog, no AI, per-day dismiss + permanent off-switch in App Settings; B1 ramp = 40/60/80% default, applicability shares the heavy-compound classification with rest-time categories.

**Backlog verdicts standing for future rounds:** B4/B9/B12 parked (B5 now done via this batch); B13 dropped (873 images shipped). Verified-exists (don't re-propose): in-workout swap (`LogWorkoutViewModel.swapCurrentExercise`), plate readout, last-session line, per-muscle effort recovery, 4 notification types w/ per-type toggles, session-level rest/pacing in Recap.
