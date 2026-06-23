# Batch 2 — Coordination Index (for a coding agent)

Six self-contained briefs, **one agent per item**. Native Android (Kotlin/Java). Read this first.

## Items (renumbered; old → new mapping)
| New | Item | Type | From |
|---|---|---|---|
| 1 | Workout-session persistence (resume keeps all sets & user values) | Bug | merged old 5 + 6 |
| 2 | Calisthenics swap must fully update the exercise | Bug | old 2 |
| 3 | No PR on first performance | Bug | old 8 |
| 4 | Remove the redundant Skip button | Change | old 3 |
| 5 | Exercise explanation window: add AI note alongside DB info | Enhancement | old 1 |
| 6 | Quick-access exercise menu (inspect / jump / add exercise) | Feature | merged old 7 + 9 |

Files: `fix2-01-persistence`, `fix2-02-swap-update`, `fix2-03-pr-baseline`, `fix2-04-remove-skip`, `fix2-05-explanation-ai-note`, `fix2-06-quick-access`.

## Coordination
- **Item 1 (persistence) and Item 6 (quick-access) both touch active-session state & navigation.** Confirm file overlap; if they share code, sequence them (do **1 first**) or assign to the same agent — don't run them as blind parallel agents.
- **Item 1 also relates to the earlier resume-button routing fix** (Home "resume" must open the live session) — don't regress it.
- **Item 2** builds on the existing easier/harder swap feature.
- Items 3, 4, 5 are independent and can run in parallel.

## Suggested order (by impact / dependency)
1. **Item 1 (persistence)** — data integrity; also the foundation Item 6 relies on.
2. **Item 6 (quick-access feature).**
3. **Items 2, 3, 4, 5** — independent, parallelizable.

## Global constraints
- **Diagnose before patching** — especially Item 1 (the exercise-1-vs-later asymmetry is a clue).
- **Do not regress** previously-fixed behavior (resume-button routing, the swap feature, etc.).
- Where a fix implies an undecided design choice (e.g. the quick-access visual style), pick a sensible default, apply it consistently, and report it.
- Each agent reports: root cause, fix, how verified, residual risk, any new dependency.

## Testing
- **Logic (Item 3 PR rule):** JVM / Robolectric (`./gradlew test`).
- **Persistence / navigation / UI (Items 1, 2, 4, 5, 6):** on-device — AVD + KVM driven by Maestro. Item 1 specifically must be verified by **fully killing the app and reopening**, on exercise 1 and on a later exercise.
