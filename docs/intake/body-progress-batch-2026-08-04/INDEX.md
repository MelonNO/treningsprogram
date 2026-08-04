# Intake batch — Loading texts, Body progress tab + body fat, Progress default exercise (2026-08-04)

Prepared for: Project-lead orchestrator
Source: four user requests (user's own numbering 1, 2, 4, 5 — "3" was skipped by the user, nothing is missing), clarified over three rounds.
Status: CONFIRMED by the user (replies relayed verbatim via coordinator; the confirming words are the user's own). Creating these docs does NOT dispatch the orchestrator — that is a separate user instruction.

## Items

| ID | Title | Type | Brief file | Status |
|----|-------|------|-----------|--------|
| 01 | Loading-text library: 150 messages, random rotation | Improvement | brief-01-loading-texts-expansion.md | Confirmed |
| 02 | Body progress tab with built-in body-fat calculation (user items 2 + 4, MERGED) | New feature | brief-02-body-progress-tab-bodyfat.md | Confirmed |
| 03 | Progress tab opens on a random top-15 exercise | Improvement | brief-03-progress-default-exercise.md | Confirmed |

## Merge / cluster / order guidance

- **MERGED:** the user's original items 2 (body-fat calculator) and 4 (Body progress tab) are one feature — the calculator is not a separate screen; it is the automatic computation inside the new tab. Brief 02 covers both.
- **Cluster A (serialize or one worker): 02 → 03.** Both touch the Stats-screen Progress surface: 02 *removes* the body-weight card from `HistoryProgressFragment` (moving it to the new tab) and adds a tab to `HistoryFragment`/`HistoryPagerAdapter`; 03 changes `HistoryProgressFragment`'s default-selection behavior and reads from `HistoryViewModel`. Same files — do not run in parallel.
- **Item 01 is fully independent** — `ui/common/GenerationTips.kt` plus its four call sites (ProgramFragment full-gen + day-gen, SettingsAiFragment, SetupWizardFragment). Can run in parallel with Cluster A.
- **Suggested order:** 02 (largest, DB + backup implications) → 03 (small, depends on 02's card removal landing first or same worker) ; 01 anytime in parallel.
- **Cross-item hazard:** 02 adds fields to first-time setup (`SetupWizardFragment`) and 01's rotation change also touches `SetupWizardFragment`'s tip rotation — trivial overlap, but coordinate if run simultaneously.

## Confirmed decisions (user's own words)

1. Height and sex become profile settings: asked in **first-time setup** AND editable in **App Settings**.
2. If sex = woman, a **hip** measurement is also collected; if male, **hip is not shown anywhere**.
3. Body fat = **average** of Navy method and RFM.
4. Body fat is computed **only when waist and neck are logged** (fat % only — the user's earlier "use most recent weight" comment is withdrawn; neither formula uses weight and no fat-mass display is requested).
5. The existing body-weight chart card is **removed from the Progress tab** (lives in Body progress).
6. Home-screen **quick-add for weight stays**.
7. Time ranges: default **3 months**; 1M / 6M / 1Y / All; plus a **calendar-specific custom range**.
8. **150** loading messages, "a bit of it all" in tone (tips, facts, encouragement, humor).
9. Messages rotate **randomly — the sequence is different every time**.
10. Body progress tab: **primary function is graphs/progress; logging is secondary** and its input UI must not take up unnecessary space when not in use.
11. Progress tab default = random from top-15 most-logged, re-rolled once per app launch; manual switch sticks for the session.

## Assumptions applied (user may veto any)

- **A1 — Units:** height, waist, neck, hip in **cm** (app is metric throughout).
- **A2 — "Most sessions logged"** = number of distinct workout sessions containing at least one set of the exercise; fewer than 15 distinct exercises → draw from what exists. (Stated to user, not objected.)
- **A3 — Random rotation semantics:** shuffle the full list so no message repeats until all have shown, reshuffling for each new wait. (Decision made under delegation — veto-able.)
- **A4 — Women + missing hip:** for a woman, body fat computes only when waist + neck + hip are all present (Navy's female formula needs hip; averaging requires both formulas). (Delegated decision.)
- **A5 — Existing users:** users who already completed setup have no height/sex until they set them in App Settings; until then, measurement logging and charts work, body fat simply cannot compute (a gentle pointer to App Settings is acceptable). (Delegated decision.)
- **A6 — Custom calendar range** = pick an exact start date and end date.
- **A7 — Compact logging UI shape** (e.g. collapsed entry area expanded on demand) is the builder's choice, bound by decision 10. (Delegated.)

## Cross-cutting constraints

- Build via `./build.sh` (never `./gradlew` directly).
- No commits or releases unless the user asks.
- No on-device / automated UI tests (standing rule); verify via build + unit tests only.
- New measurement fields imply a Room schema change and a backup export/import version bump — exported backups must round-trip the new data, and older backups must still import.
