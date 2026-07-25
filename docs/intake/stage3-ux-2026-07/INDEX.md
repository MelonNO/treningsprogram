# Stage-3 UX Batch — 2026-07 — INDEX

**Prepared for:** Project-lead orchestrator
**Source:** User batch of 16 numbered items sent while going to sleep, relayed verbatim via coordinator. User's numbering 1–16 preserved.
**Status:** **No confirmation round was possible** — the user pre-authorized: "i am asleep … assume and log it in your report. and if anything is too unclear skip it and note it in the report." All 16 items were groundable in the code; **none skipped**. Every judgment call is a labelled assumption (A-XXx) the user can veto in the morning.
**App state at intake:** v1.21.0 tree, but this batch is **STAGE 3 of tonight's run**: it builds against the tree AFTER release 1 (rest-ux, `docs/intake/rest-ux-batch-2026-07/`) and release 2 (feature batch, `docs/intake/feature-research-2026-07/`) have shipped, and ships as **its own version**. The full test pass is stage 4.

## Items

| ID | Title | Type | Cluster | Brief file | Status |
|----|-------|------|---------|------------|--------|
| 1 | Progress: reps graph for bodyweight exercises (+ weight when added) | Feature | H2 | `brief-01-bw-reps-graph.md` | Ready |
| 2 | Skeleton loaders on data screens | Feature (polish) | H5 (last on History surface) | `brief-02-skeleton-loaders.md` | Ready |
| 3 | Remove overview graphs from Recap | Removal | H1 | `brief-03-remove-recap-overview.md` | Ready |
| 4 | Profile PRs: last 7 days only | Feature | P | `brief-04-profile-prs-7-days.md` | Ready |
| 5 | Remove stats block from Profile | Removal | P | `brief-05-remove-profile-stats.md` | Ready |
| 6 | Settings order: App Settings on top | Reorder | T | `brief-06-settings-order-app-settings-top.md` | Ready |
| 7 | Library: two-frame exercise image animation | Feature | Standalone | `brief-07-library-image-animation.md` | Ready |
| 8 | Debug moves inside About | IA move | T | `brief-08-debug-under-about.md` | Ready |
| 9 | Recap muscles: fine labels (triceps, not arms) | Feature | H1 | `brief-09-recap-finer-muscles.md` | Ready |
| 10 | Recap visual overhaul | Visual redesign | H1 (last in H1) | `brief-10-recap-visual-overhaul.md` | Ready |
| 11 | Heatmap square → Recap with session selected | Feature | H3 | `brief-11-heatmap-tap-to-recap.md` | Ready |
| 12 | History tab: calendar date-range filter (default All) | Feature | H2 | `brief-12-history-date-range-picker.md` | Ready |
| 13 | Progress tab: same date-range filter | Feature | H2 | `brief-13-progress-date-range-picker.md` | Ready |
| 14 | Recap: achievements + PRs earned that session | Feature | H1 | `brief-14-recap-session-achievements-prs.md` | Ready |
| 15 | Remove CSV export from Stats | Removal | H3 | `brief-15-remove-csv-export.md` | Ready |
| 16 | Weight keypad: tap outside dismisses | Polish | Standalone | `brief-16-keypad-tap-outside-dismiss.md` | Ready |

**Skipped as unclear: none.** Item 2 was the vaguest ("appropriate places"); it is scoped to the History group with assumption A-02a rather than skipped, since the user's own example named that surface.

## Merge / cluster + parallelization guidance
Grounded in the files each item touches. The History bottom-nav tab hosts four sub-tabs (Recap | Stats | Progress | History) — **five clusters land on that one surface; serialize per sub-tab file as below.**

**Cluster H1 — Recap overhaul (3 → 9 → 14 → 10): ONE worker, strictly in that order.** All four rework `HistoryRecapFragment` (+ `SessionRecap`/recap-building in `WorkoutRepository` for 9/14): remove overview first, then fine muscles, then earned-this-session content, then the visual pass over whatever remains. Item 14 may need a forward-only unlock→session linkage (A-14a) — coordinate with the post-release-2 achievements shape (R5 tiers).

**Cluster H2 — time filters + BW chart (12, 13, 1): ONE worker.** 12 edits `HistoryLogFragment` + `HistoryViewModel`; 13 and 1 both edit `HistoryProgressFragment` (do not split 13/1 across workers). Shared range-picker pattern should look identical on both tabs.

**Cluster H3 — Stats tab (11, 15): ONE worker.** Both edit `HistoryStatsFragment` (+ `VolumeHeatmapView` tap handling for 11). 11 consumes the existing Recap open/highlight mechanism — read-only dependency on H1's surface (mechanism unchanged by H1; safe in parallel, but verify after H1 lands).

**Cluster H5 — skeletons (2): ONE worker, LAST on the History surface** — it decorates the layouts 3/9/10/11/12/13/14/15 will have just reshaped.

**Cluster P — Profile (4, 5): ONE worker.** Both edit `ProfileFragment`/`ProfileViewModel`/layout. Must build against the **post-release-2 Profile** (feature batch R5 adds the achievement gallery + "next up" strip there — do not clobber it).

**Cluster T — Settings (6, 8): ONE worker.** `fragment_settings.xml` order + Debug row relocation (`SettingsBackupFragment` → About) + subtitle text.

**Item 7 — standalone** (library detail screen; the alternation pattern already exists in `ExerciseInfoBottomSheet` to mirror).

**Item 16 — standalone** (`LogWorkoutFragment` keypad; this file was rewritten by release 1 and touched by release 2's R6/R7 — build on the settled tree, smallest possible diff).

### Suggested order
1. In parallel: **T (6+8)**, **7**, **P (4+5)**, **16**, **H2 (12→13→1)** — mutually independent files.
2. **H1 (3→9→14→10)** in parallel with the above (owns the Recap file exclusively).
3. **H3 (11+15)** — after or alongside H1 (only a behavioral handshake with Recap's open mechanism).
4. **H5 (2)** strictly last.

| Group | Items | One worker? | Note |
|-------|-------|-------------|------|
| H1 | 3 → 9 → 14 → 10 | Yes | Recap file owner; order mandatory |
| H2 | 12, 13, 1 | Yes | 13+1 same file; shared picker pattern with 12 |
| H3 | 11, 15 | Yes | Stats file owner; 11 uses existing Recap-open mechanism |
| H5 | 2 | Yes | Last — skeletons over the final layouts |
| P | 4, 5 | Yes | Post-release-2 Profile compatibility |
| T | 6, 8 | Yes | Trivial |
| — | 7 | Own worker | Library detail animation |
| — | 16 | Own worker | Keypad dismiss; smallest-diff on a hot file |

## Assumptions applied (user may veto — full text in each brief)
- **A-01a/b:** reps graph = best working-set reps per session; bodyweight-ness derived from logged data.
- **A-02a/b:** skeletons scoped to the History group; shimmer style, no spinners.
- **A-03a:** only the overview section + its texts go; per-session recap untouched by item 3.
- **A-04a/b:** rolling 7 logical days; exercise + new weight shown; latest/best per exercise.
- **A-05a:** the whole 4-stat block goes, including the streak tile (streak stays on Home).
- **A-06a:** only App Settings moves; other rows keep relative order.
- **A-07a:** animation on the detail screen; list thumbnails stay static.
- **A-08a:** Debug appended last within About.
- **A-09a:** recovery-panel taxonomy + primary-emphasis weighting, whole-set rounding.
- **A-10a/b:** Auros language, no new charts.
- **A-11a:** heatmap cell → most recent session that week training that muscle, highlighted.
- **A-12a/b, A-13a:** ranges don't persist across restarts; old preset chips fully replaced.
- **A-14a/b:** timestamp-based achievement attribution, omit-when-unsure, forward-only linkage allowed; PRs fold into "earned this session".
- **A-15a:** CSV removed outright (JSON backup remains).
- **A-16a:** outside tap passes through (closes pad AND performs the tapped action), builder may exempt accident-prone cases consistently.

## Cross-cutting constraints
- Ships as **its own version after release 2**; build via `./build.sh`; verify with build + unit tests only (**no Waydroid/on-device UI tests** — stage 4 and the user handle live checks).
- **No release beyond the authority already granted for tonight's staged plan.**
- **Frugal live-API testing** — nothing in this batch needs generation calls.
- Removals (3, 5, 15) may delete newly-dead code but must not disturb shared logic (`RecapGraphs` uses elsewhere, backup JSON path, streak surfaces).
