# Intake index — Skeleton removals + swipe fixes (2026-07-27)

**Prepared for:** Project-lead orchestrator
**Source:** 4 items brought by the user, 2026-07-27 (post-v1.27.0)
**Status:** CONFIRMED — understanding restated and the user answered "correct"

**Confirmation note:** the user's sign-off was **relayed via the coordinator**
(verbatim replies: "q1 remove / q2 remove / q3 yes… / q4 no keep it… / q5 yep…",
then "correct" to the final restatement). Relayed approval covers the *understanding*
documented here; creating these docs does not itself dispatch work to the
orchestrator — that is a separate user instruction.

## Items

| ID | Title | Type | Brief file | Status |
|----|-------|------|-----------|--------|
| 01 | Remove History list skeleton loader | Bug / removal | `brief-01-remove-history-list-skeleton.md` | Confirmed |
| 02 | Remove Progress tab skeleton loader | Bug / removal | `brief-02-remove-progress-skeleton.md` | Confirmed |
| 03 | History week swipe: change week, not sub-tab (zone-based) | Bug (v1.27.0 feature broken) | `brief-03-history-week-swipe-zoning.md` | Confirmed |
| 04 | Remove Program tab week-swipe (current week only) | Change / removal | `brief-04-remove-program-week-swipe.md` | Confirmed |

## Merge / cluster and parallelization guidance

- **Cluster A — swipe gestures: items 03 + 04.** Same gesture surface family; the swipe
  widget removed from Program (04) is the one History's week view (03) depends on. One
  worker, or 04 strictly after 03's approach is settled.
- **Cluster B — skeleton removals: items 01 + 02.** Same skeleton helper/pattern, two
  History sub-tab surfaces. Natural single small unit.
- **Cross-cluster seam (hazard):** items **01 and 03 both touch the History
  Sessions/Log surface** (`HistoryLogFragment` + its layout). Do not run them in
  parallel on separate workers against the same files — either one worker takes all
  History-side work, or serialize 01 after 03.
- **Suggested order:** 03 first (highest value — a shipped feature is currently
  unusable, and it defines the gesture routing others must not break), then 04, then
  01 + 02. Given the small total size, one worker/pass doing 03 → 04 → 01 → 02 is a
  sensible shape; parallelism buys little here.

| Group | Items | Parallel-safe? |
|-------|-------|----------------|
| A | 03, 04 | With each other: sequence 03 → 04 (shared widget) |
| B | 01, 02 | 02 is independent; 01 shares files with 03 — serialize after 03 |

## Confirmed decisions

- Skeletons: **full removal** on both named surfaces (History list, Progress) — not
  "show only while genuinely loading". Stats/Recap skeletons untouched (not requested).
- History gesture is **zone-based**: swipe on the opened week view = change week
  (right = older, left = newer, all v1.27.0 semantics); swipe anywhere else in History
  = switch sub-tabs (kept, unchanged).
- Program tab = **current week only**; week-swipe removed with no replacement
  affordance; past weeks are viewed exclusively via the History week browser.

## Assumptions applied (user may veto)

- **A-04a:** "Remove the possibility to swipe the weeks" in Program also removes the
  now-unreachable past-week viewing state (read-only banner etc.) rather than leaving
  dead UI. Follows directly from "program is only for viewing the current week", but
  flagged since the user did not name the banner explicitly.

## Cross-cutting constraints

- Build via `./build.sh` (never `./gradlew`).
- No commits, tags, or releases unless the user explicitly asks.
- No on-device / Waydroid / Maestro UI tests (standing rule); verify via build + unit
  tests; the user performs the live device check — especially critical for item 03,
  which is a gesture bug that shipped despite green tests.
