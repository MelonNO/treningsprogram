# Rest-timer & UX Batch — 2026-07 — INDEX

**Prepared for:** Project-lead orchestrator
**Source:** User request batch of 5 numbered items, relayed via coordinator. The user's own numbering (1–5) is preserved; **item 3 was withdrawn by the user** ("scrap this point it is wrong") and has no brief.
**Status:** Understanding CONFIRMED by the user (answers Q2a/Q2b/Q4a–Q4d/Q5 + accepted label suggestion, relayed verbatim). Briefs ready for the orchestrator.
**App state at intake:** v1.21.0, DB v18.

## Confirmation note
The user's clarifying answers and sign-off arrived **relayed via the coordinator** (the standing verbatim-relay arrangement for this intake agent). Producing these documents is **intake only** — it is **NOT** a dispatch to the orchestrator. Dispatching is a separate step the user will trigger.

## Items

| ID | Title | Type | Brief file | Status |
|----|-------|------|------------|--------|
| 1 | Remove the "globe" art from the Home tab header | Feature (cosmetic) | `brief-01-remove-home-globe.md` | Ready |
| 2 | Session-only memory of the user's +30/−30 rest adjustment (per exercise) | Feature | `brief-02-session-rest-adjustment-memory.md` | Ready |
| 3 | *(withdrawn by user — "last time" reps display; no brief)* | — | — | Dropped |
| 4 | AI rest times vs. user's own per-category rest times (wizard + Settings), generation-aware | Feature | `brief-04-manual-rest-times-mode.md` | Ready |
| 5 | Per-exercise timer resets when the app is minimized | Bug | `brief-05-exercise-timer-background-reset.md` | Ready |

## Merge / cluster + parallelization guidance
Grounded in the files each item touches in the current tree.

**Cluster A — rest-timer machinery (items 4 + 2): ONE worker, in that order.**
Both items resolve "what does the rest timer start at" and edit the same seam: `LogWorkoutViewModel.getRestSecondsForCurrentExercise()`, `LogWorkoutFragment` (the two call sites that open the sheet), `RestTimerBottomSheet` (label + adjust buttons), `PreferencesManager` (new mode + category times), plus item 4 alone reaches into `SetupWizardFragment`, `SettingsTrainingFragment`, backup models, and the generation duration math (`WorkoutTimeEstimator` / `AiRepository` budget+prompt). Build **4 first** (establishes the base-time source), then **2** (session-only adjustment layered on top of whichever base is active — the user explicitly confirmed the layering).

**Item 5 — standalone bug fix.** Lives in `LogWorkoutViewModel`'s per-exercise elapsed flow (+ possibly session-resume plumbing for the robust variant). **Hazard:** same file as Cluster A's resolver change (different regions). Either serialize (5 before or after A) or have A's worker rebase; do not run two workers blind in `LogWorkoutViewModel.kt` simultaneously.

**Item 1 — standalone, trivial.** `fragment_home.xml` only; zero overlap; safe fully in parallel.

### Suggested order
1. **Item 1** and **item 5** can start immediately (1 anywhere; 5 first if run in parallel with A, since it is the smaller `LogWorkoutViewModel` change to rebase over — or simply fold 5 into the same worker as A and do 5 → 4 → 2).
2. **Cluster A: item 4 then item 2** (one worker).

| Group | Items | One worker? | Note |
|-------|-------|-------------|------|
| A | 4 → 2 | Yes | Rest-time base source first, session adjustment on top; touches wizard/settings/prefs/timer/generation math |
| — | 5 | Own worker or folded into A | Same-file hazard with A in `LogWorkoutViewModel.kt`; serialize or same worker |
| — | 1 | Own worker | Trivial layout removal, fully parallel |

## Confirmed decisions
- **Item 1:** remove entirely, nothing in its place (implicit confirmation — no objection).
- **Item 2:** next-set rest = base ± net of all +30/−30 presses (Q2a yes; e.g. 1:30 +30 → next set 2:00); sticks for **all remaining sets of that same exercise, this session** (Q2b = option a); resets on next exercise and next session; never persisted.
- **Item 4:** two categories — **Heavy compounds** and **Accessories** (Q4a); defaults **3:00 / 1:30**, m:ss (Q4b); wizard placement on the training-schedule step (Q4c); +30/−30 session memory applies on top of manual times (Q4d); rest-sheet label reflects source — "AI suggested: …" vs "Your time: …" (accepted suggestion); **generation's session-duration math must count the user's manual rest times when manual mode is active** (Q4b addition, user-flagged as important).
- **Item 5:** robust fix — survives minimize **and** background process death (Q5 "Make it robust").

## Assumptions applied (user may veto)
- **A1 (item 4):** AI mode stays the default for fresh and existing installs; manual mode is opt-in.
- **A2 (item 4):** cardio/warm-up entries fall under Accessories if their rest timer fires at all; no third category.
- **A3 (item 4):** heavy-compound vs accessory classification is automatic (derived from the exercise), never hand-tagged by the user.

## Cross-cutting constraints
- Build via `./build.sh` (not `./gradlew`); verify with build + unit tests only.
- **No commits or releases unless the user explicitly asks** (no auto-release).
- **No Waydroid/on-device/automated UI testing** — the user verifies on-device.
- **Frugal live-API testing** for item 4's generation-math criterion: behavioral generation ACs are live-gen-only; keep any live verification minimal and decision-driven.
