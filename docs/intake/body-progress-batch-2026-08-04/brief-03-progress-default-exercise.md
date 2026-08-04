# Brief 03 — Progress tab opens on a random top-15 exercise

Type: Improvement
Cluster: A (shares `HistoryProgressFragment` / `HistoryViewModel` with brief 02 — serialize after 02 or same worker)

> Outcome-only: this brief describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context

The Progress tab (inside the Stats screen) currently opens with **no exercise selected** — charts are empty until the user picks one from the exercise dropdown (`selectedExercise` starts blank).

## What the user wants (end result)

- On opening the Progress tab, an exercise is **already selected and charted**.
- The default is drawn **at random from the user's 15 most-logged exercises** (ranked by how many workout sessions each appears in).
- The random pick is made **once per app launch**: leaving and returning to the tab within the same app session shows the same exercise (or whatever the user manually switched to — manual choices stick for the rest of the session). The next app launch re-rolls.
- If fewer than 15 distinct exercises have been logged, the pool is simply whatever exists; with no logged exercises at all, today's empty state remains.

## Acceptance criteria

- Done when opening the Progress tab immediately shows charts for an exercise from the top-15-by-session-count pool, with the dropdown reflecting that selection.
- Done when re-entering the tab in the same app session preserves the current selection (default or manual).
- Done when successive app launches can yield different default exercises (randomness observable across launches).
- Done when users with 1–14 logged exercises get a default from their actual pool, and users with none see the existing empty state unchanged.

## Scope and constraints

- **In scope:** default-selection behavior of the Progress tab only.
- **Out of scope:** any change to the charts themselves, the dropdown, or ranking exposure in the UI (the top-15 list is internal, never displayed).

## Decisions baked in

- Random from top 15, per app launch, manual switch sticks for the session (user decisions 11).

## Assumptions (user may override)

- **A2:** "most sessions logged" = number of distinct workout sessions containing at least one set of the exercise (warm-up or working — any logged set counts).

## Considerations for whoever builds it

- Brief 02 removes the body-weight card from this same fragment — land 02 first or use one worker for both to avoid conflicting edits.
- "Per app launch" implies process-lifetime persistence of the pick, not disk persistence.
