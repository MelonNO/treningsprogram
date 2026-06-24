# B1 — AI weekly coach summary

**Type:** New feature
**Cluster:** P1 (AI coaching) — one worker with B2 and B3.
**Outcome-only:** This brief describes the end result and user experience. It does **not** prescribe implementation. The "how" is the orchestrator's.

---

## Context

The app already builds a rich, structured view of the user's recent training to feed the AI *program-generation* prompt — recent sessions, per-exercise strength history, and weekly volume (`AiRepository.buildSessionHistory`, `WorkoutRepository.getStrengthHistory` / `getWeeklyVolume`). Today that context only ever produces a machine-readable plan. The app never speaks to the user in plain language about how their training is going. The AI is the app's differentiator, yet it is invisible to the user as a *coach*.

This feature surfaces a short, natural-language **weekly coaching summary** — the app reading the user's own data back to them as a coach would.

## What the user wants (end result)

Once a week, automatically, the user is shown a concise plain-language summary of how their past week of training went: what went well, what slipped, and what the app is doing about it. It should feel like a coach who has looked at the user's actual logged data.

- **Cadence:** **Automatic, weekly** (confirmed by the user). The user does not have to ask for it.
- **Tone/content:** A short readout grounded in the user's real data — e.g. notable lifts that progressed, muscle groups that were under- or over-trained relative to plan, skipped sessions, streak status, and a forward-looking note ("next week I've added…"). Specific and personal, not generic motivation.

## Acceptance criteria ("Done when …")

- **Done when** the app, once per week, automatically generates and presents a short natural-language coaching summary of the user's previous week, derived from their actual logged sessions, sets, strength trends, and plan adherence.
- **Done when** the summary is specific to the user's data (names real exercises/muscle groups and real changes), not generic boilerplate.
- **Done when** the summary appears without the user having to request it, and is reachable in an obvious place in the app.
- **Done when** a user with too little data for a meaningful summary (e.g. a fresh install, no completed sessions that week) sees a sensible, non-broken state rather than an empty or erroneous summary.
- **Done when** generating the summary does not block or interfere with normal app use.

## Scope and constraints

- **In scope:** automatic weekly generation; a plain-language summary grounded in real data; a clear place to view it.
- **Out of scope (unless the user later asks):** per-session summaries (this is the weekly one); a conversational back-and-forth chat coach; push notifications to deliver it (delivery is in-app unless the user asks otherwise).
- **Uses the Claude API** — an extra weekly call is acceptable (confirmed). Reuses the existing history-context machinery rather than building a parallel one.
- **Cross-cutting constraints:** build via `./build.sh`; no git commits/releases unless the user asks; **on-device UI verification IS required for this batch** per the per-wave gate in `SEQUENCE.md` (this is the explicit ask satisfying the general no-unprompted-UI-testing rule); mindful of concurrent `PreferencesManager` churn from the in-flight backup work.

## Decisions baked in

- **Cadence = automatic weekly** (user-confirmed).
- **Extra Claude API call is acceptable** (user-confirmed).

## Assumptions (user may override)

- **ASSUMPTION (placement, question A):** where the summary surfaces (Home card vs. a Stats-tab section vs. a dedicated screen) is **left to the orchestrator** to decide during implementation — the user delegated this.
- **ASSUMPTION (persistence, question C):** weekly summaries are **persisted as a scrollable history** so the user can look back at previous weeks, rather than being regenerated and discarded each time. *(If the user prefers ephemeral, this becomes simpler and adds no new storage; flagged so they can veto. Note: a new persisted store would be a DB-schema touch — see the INDEX seam between B1 and E2.)*

## Considerations for whoever builds it (surfaced, not decided)

- If summaries are persisted (the assumption above) and the user later wants them in the cloud backup, that is a coordination point with the in-flight backup feature.
- "Once per week" needs a defined trigger boundary (e.g. ISO week, consistent with the app's existing `isoWeekKey()` convention) so a summary is generated exactly once per week, not repeatedly.
