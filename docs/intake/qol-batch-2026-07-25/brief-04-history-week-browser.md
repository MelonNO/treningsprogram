# Brief 04 — History sub-tab becomes a monthly week-browser mirroring the Program tab

Type: Feature (large — the biggest item of the batch)
Cluster: standalone; coordinate lightly with item 03 inside `ui/history/` (different files: this replaces the History list, 03 touches Recap/Stats surfaces)

> Outcome-only: this brief describes the end result and user experience. The implementation approach belongs to the orchestrator/worker.

## Context

The Stats bottom-nav tab has four sub-tabs: Recap, Stats, Progress, **History**. The History sub-tab today is a flat vertical list of sessions with a search box, a date-range picker, per-session "Edit date", an expandable per-set listing with a per-set delete ×, and tap-card → that session's Recap. The Program tab, by contrast, shows a week card with a 7-day chip strip, a selectable day whose exercises are listed as cards, and (for past weeks, via swipe) a read-only mode — but the Program tab's past-week view shows the *planned* targets, not what was actually performed.

This item replaces the **History sub-tab only** (user confirmed). Recap, Stats, and Progress sub-tabs stay as they are.

## What the user wants (end result)

A **monthly overview** of training history:

- Top level: the months, each showing its **weeks**, selectable. (Exact layout — month pager with week rows vs grouped scrolling list — is **delegated to the builder**: "create what makes the most sense in terms of UI and user experience".)
- Tapping a week opens a **week view as similar to the Program tab as possible**: selectable day chips, and the selected day lists the exercises **actually performed** that day.
- Tapping an exercise shows its **details in one tap**: the performed data (each set's reps and weight, warm-ups marked, PR flags) **and** the exercise info (instructions/images) — the user wants "everything" included and reachable from that same click; how the two are arranged within what opens is delegated to the builder ("cohesiveness of the app").
- Everything is **read-only history**: no Start Workout, no swap/regenerate, no add exercise, no edit/move/delete of plan entries — any action that makes no sense for history is absent.
- Performed reality, not plan targets: what is shown per day/exercise is what was logged (sets, reps, weights), with appropriate performed-context info (sets, reps, PRs "etc." per the user's request).

## Acceptance criteria

- Done when the History sub-tab presents months → weeks → a Program-tab-like week view with selectable days.
- Done when a day shows the exercises actually performed, and tapping one reveals the per-set performed data (reps × weight per set, warm-ups distinguished, PRs marked) plus the exercise's info, all reachable from that single tap.
- Done when no mutating/plan actions appear anywhere in the history views.
- Done when **search** and **date-range filtering** still work in the new structure (must-survive, user's explicit requirement).
- Done when auto-logged REST and MISSED days are visible/distinguishable on their day chips.
- Done when history reaches back through everything ever logged.
- Done when a session's full **Recap** is still reachable from its day.
- Done when existing deep-links into Recap (e.g. the Stats heatmap drill) still work unchanged.

## Scope and constraints

- Only the History sub-tab is rebuilt. The Program tab (including its past-week swipe view) is untouched by this item.
- Weeks are Monday-based, consistent with the rest of the app.

## Decisions baked in

- Same-tap access to combined performed-details + exercise info (user: "yes include everything, but it does need to be accessed with the same click").
- Search + date-range filter must survive.

## Assumptions (user may override)

- A3 (confirmed in Q&A): full history depth; rest/missed on chips; Recap link retained.
- A4: per-set delete (red ×) and "Edit date" are NOT required to survive (user listed only search + filter). The builder may keep them where they fit naturally; dropping them is acceptable.

## Considerations for whoever builds it (surfaced, not decided)

- **Ratified project rule:** first-ever lifts are *baselines*, NOT PRs — PR marking here must follow that.
- Weeks with no training (or nothing logged at all) — how they appear at the month level is the builder's call; it just must not look broken.
- The day chips can meaningfully carry performed summaries (done/rest/missed states already exist as concepts elsewhere).
- Search semantics in the new structure (by exercise name across history vs within a week) is the builder's call — it must feel at least as capable as today's search over sessions/sets.
- Standing constraints: build via `./build.sh`; no commits/releases unless asked; no on-device UI tests — verify via unit tests.
