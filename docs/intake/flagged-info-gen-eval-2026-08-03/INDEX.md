# Intake batch — hide flagged-exercise info + generation evaluation (2026-08-03)

Prepared for: **Project-lead orchestrator**
Source: two user items relayed verbatim via coordinator, clarified over two Q&A rounds.
Status: **CONFIRMED** — briefs reflect the user's answers.

## Confirmation note

Sign-off was **relayed via coordinator** (not direct). The user's verbatim answers: round 1 — "1 sure / 2 show to ai notes / 3 general / 4 a implementation is not your job / 5 120 is okay / 6 no API calls to begin with"; round 2 (disambiguating the 120-minute question against the full written confirmation) — "a". The user answered only the single open question after seeing the full confirmation text; the uncontested remainder is treated as accepted. Creating these docs ≠ dispatching to the orchestrator — that is a separate, later user instruction.

## Items

| ID | Title | Type | Brief file | Status |
|----|-------|------|-----------|--------|
| 01 | Flagged exercises stop showing mismatched pictures/description | Feature (small) | `brief-01-hide-flagged-exercise-info.md` | Ready |
| 02 | Workout-generation evaluation report (report ONLY, zero API calls) | Investigation / evaluate-first | `brief-02-generation-evaluation-report.md` | Ready |

## Merge / cluster + parallelization guidance

- **No merge, no cluster.** The items are fully independent: 01 touches `ui/log/ExerciseInfoBottomSheet.kt` (+ possibly `ExerciseInfoCorrections.kt` read paths); 02 touches **nothing** — it only reads `data/repository/AiRepository.kt` and history docs, and writes a report.
- **Safe to run in parallel** (02 is read-only, so it cannot conflict with anything).
- Suggested order if serialized: 01 first (small, shippable), 02 whenever — its output is a report for the user, not code.
- **Item 02 produces no code change.** Its deliverable goes back to the user for approval; approved proposals become *future* items. Do not let an evaluator "fix things while in there."

| Item | Files touched | Parallel-safe |
|------|--------------|---------------|
| 01 | `ExerciseInfoBottomSheet.kt` (writes) | yes |
| 02 | none (read-only + report) | yes |

## Confirmed decisions

- Item 01: hide applies **everywhere** the info sheet opens, not just during a workout.
- Item 01: sheet still opens and shows name + Coach's note (user: "show the AI notes") + performed section (History) + a hidden-because-flagged explanation.
- Item 02: **general health-check**, no specific complaint driving it.
- Item 02: **report-first** — user approves before anything is built.
- Item 02: **120-minute generation ceiling is IN scope**, framed as options for a product decision.
- Item 02: **zero live Anthropic API calls**; any live run needs a separate explicit user request with a cost estimate.

## Assumptions / delegated decisions applied (user may veto)

- 01-D1: wording/layout of the hidden state = builder's choice (standing method-delegation).
- 01-D2: name-keyed static-catalog fallback for a flagged exercise = builder's judgment; safe default is hide it too.

## Cross-cutting constraints

- Build via `./build.sh`; no commits/releases unless asked; no on-device/automated UI tests unless asked.
- Frugal API use is a standing rule for this project — item 02's zero-call constraint is hard.
- Adaptive-thinking was previously tried and REJECTED — off the table for item 02 proposals.
