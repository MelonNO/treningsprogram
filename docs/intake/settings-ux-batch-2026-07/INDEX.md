# Settings UX Batch — 2026-07 — INDEX

**Prepared for:** Project-lead orchestrator
**Source:** User bug/feature request batch of 12 items, relayed via coordinator (the user's own numbering 1–12 is preserved).
**Status:** Understanding CONFIRMED by the user. Briefs ready for the orchestrator.

## Confirmation note
The user's clarifying answers and final sign-off ("go ahead, write the briefs") arrived **relayed via the coordinator** (the standing verbatim-relay arrangement for this intake agent). Producing these documents is **intake only** — it is **NOT** a dispatch to the orchestrator. Dispatching is a separate step the user will trigger.

## Items

| ID | Title | Type | Brief file | Status |
|----|-------|------|------------|--------|
| 1 | Remove AM/PM from day-reset labels (24-hour) | Feature (polish) | `brief-01-remove-ampm.md` | Ready |
| 2 | Auto-rebalance default ON | Feature (default) | `brief-02-auto-rebalance-default-on.md` | Ready |
| 3 | Profile button returns to Profile root from a sub-setting | Bug / nav | `brief-03-profile-tab-return-to-root.md` | Ready |
| 4 | New "App Settings" screen (day-reset + auto-rebalance) | Feature (IA) | `brief-04-app-settings-screen.md` | Ready |
| 5 | Debug becomes a sub-menu under Backup & Data | Feature (IA) | `brief-05-debug-under-backup.md` | Ready |
| 6 | Move Coach Summary into "AI & Program" | Feature (IA) | `brief-06-coach-summary-into-ai-program.md` | Ready |
| 7 | Reorder top-level Settings rows | Feature (IA) | `brief-07-settings-row-order.md` | Ready |
| 8 | Generation loading animation present on the Program tab | Feature | `brief-08-generation-loading-on-program-tab.md` | Ready |
| 9 | "Generate now" from Settings must preserve logged days | Bug | `brief-09-generate-preserve-logged-days.md` | Ready |
| 10 | Merge "Do this workout today" into "Start Workout" (+ append-to-logged) | Feature / refactor (broadening) | `brief-10-merge-start-workout-do-today.md` | Ready |
| 11 | Collapse "Why your program changed" by default | Feature (polish) | `brief-11-collapse-rationale-card.md` | Ready |
| 12 | Per-muscle recovery times, scaled by logged effort | Feature | `brief-12-per-muscle-recovery.md` | Ready |

## Merge / cluster + parallelization guidance
Grounded in the files each item touches in the current tree.

**Cluster A — Settings information architecture + auto-rebalance (items 1, 2, 3, 4, 5, 6, 7): ONE worker.**
These all edit the same seam: the Settings row list (`SettingsFragment` + `fragment_settings.xml`), the navigation graph (`nav_graph.xml`), the tab-mapping / Profile-reselect wiring (`MainActivity.kt`), plus `PreferencesManager` (rebalance default), the source screens the two controls move out of (`SettingsTrainingFragment` for day-reset; the Program-options dialog for the toggle), and the new App Settings screen. They will collide badly if split. Internal build order within the worker:
1. Create the **App Settings** destination + row (4); move the **day-reset** control in (with item 1's 24-hour labels) and the **auto-rebalance** toggle in; apply item 2's new default to the moved toggle.
2. Restructure the top-level rows — nest Debug under Backup & Data (5), nest Coach Summary under AI & Program (6), and apply the final order (7).
3. Fix the **Profile-button-returns-to-root** behavior (3).

**Cluster B — generation launched from Settings (items 8, 9): one worker.**
Item 9 makes both Settings generate entry points preserve logged days (Settings-side). Item 8 surfaces the full-generation loading animation on the Program tab. Shared concern: the generation launched from Settings and its in-progress signal.

**Item 10 — standalone, HIGHEST RISK. Own worker.** Merges the two Program-tab buttons and adds a new append-into-today's-logged-session capability + rebalance. Touches `ProgramFragment.kt`, `fragment_program.xml`, the log/complete flow, and the repository move/append commit.

**Item 11 — standalone.** Collapse the rationale card (`fragment_program.xml` + `ProgramFragment.kt`).

**Item 12 — standalone, fully independent.** Recovery domain model + Home recovery card. No overlap with any other item — safe to run in parallel from the start.

### Cross-group hazard — the Program-tab surface (`ProgramFragment.kt` + `fragment_program.xml`)
**Four items edit this surface:** item **4** (removes the auto-rebalance line from the program-options dialog in `ProgramFragment.kt`), item **8** (adds a full-generation loading view), item **10** (removes the second button + reworks Start Workout), item **11** (collapse the rationale card). Assign these so they do **not** collide — either serialize them or give the Program-tab layout/fragment edits a single coordinating owner while the others rebase. Item 4's touch here is tiny (one dialog line); items 8/10/11 are the substantive Program-tab edits.

### Suggested order
1. **In parallel from the start:** Cluster A (items 1–7) and item 12 (recovery) — largely independent of the Program-tab surface (A's only Program-tab touch is the one dialog line in item 4).
2. **Item 10** (the large Program-tab behavior change) — land it before the other Program-tab edits so they rebase onto the settled layout.
3. **Item 11** (collapse card) — small Program-tab edit, after 10.
4. **Cluster B (items 8 + 9)** — item 9 is Settings-side; item 8's Program-tab loading view rebases onto the settled Program-tab surface from steps 2–3.

| Group | Items | One worker? | Note |
|-------|-------|-------------|------|
| A | 1, 2, 3, 4, 5, 6, 7 | Yes | Settings IA + nav + auto-rebalance default; heavy shared-file overlap |
| B | 8, 9 | Yes | Generation launched from Settings; 8 also touches the Program tab |
| — | 10 | Independent worker | Highest risk; big Program-tab + log/repo change |
| — | 11 | Independent worker | Small Program-tab polish |
| — | 12 | Independent worker | Recovery model + Home card; fully isolated |

**Program-tab serialization:** items 4 (tiny), 8, 10, 11 all touch `ProgramFragment.kt`/`fragment_program.xml` — coordinate to avoid conflicts (recommended landing order on that surface: 10 → 11 → 8, with 4's dialog line rebased in).

## Confirmed decisions (baked into the briefs)
- **1** — 24-hour labels for the day-reset control; no AM/PM anywhere.
- **2** — Auto-rebalance new default **ON** for anyone who hasn't explicitly set it; never override an explicit prior choice.
- **3** — From any Settings sub-screen, a single tap on the Profile button returns to the **Profile tab's root/main menu**.
- **4** — New screen named **"App Settings"** (intake's pick, user-delegated), placed **between Backup & Data and About**, holding **exactly** the day-reset + auto-rebalance controls.
- **5** — Debug nested as a row **inside Backup & Data** (not top-level).
- **6** — Coach Summary nested as a row **inside AI & Program** (not top-level).
- **7** — Final top-level order: **Training Profile → AI & Program → Exercise Library → Backup & Data → App Settings → About.**
- **8** — Full-generation loading animation **present on the Program tab** when generation is launched from Settings; **no auto-switch**; Settings screen **keeps** its own status (additive).
- **9** — **Both** Settings generate entry points (Training Profile "Generate now" and AI & Program) **preserve already-logged days** and rebalance the rest of the week — matching the Program tab's existing "Regenerate (keep logged days)."
- **10** — Remove "Do this workout today"; **"Start Workout" on another day** moves the workout into today + rebalances. Today's **planned** workout is **replaced**; if today **already has logged activity**, the moved-in workout is **appended** to that session (one continuous session). The move is now allowed **even when today is already logged**.
- **11** — "Why your program changed" **collapsed by default**, expands on tap, **always starts collapsed** each visit (no persistence).
- **12** — **Deterministic on-device** per-muscle-group **base-recovery table**, scaled by the **logged effort** (RIR/RPE), not by set count/volume/load. No API call.

## Assumptions applied (user may override)
- **[A1-1] (item 1)** Keep the "(midnight)" annotation on 00:00 and the "— default" tag on the default hour.
- **[A4-1] (item 4)** The day-reset control keeps instant-apply (no regenerate); **[A4-2]** the auto-rebalance toggle's semantics are unchanged apart from its location and default.
- **[A8-1] (item 8)** The Program-tab animation reflects the same generation as the Settings status (one shared in-progress signal).
- **[A10-1/2/3] (item 10)** Today's own already-logged day continues its session (not a move); an other day that is itself already logged is not a redo candidate (confirm if wrong); rest days keep no Start Workout button.
- **[A11-1] (item 11)** A visible affordance (chevron/tap hint) signals the card is expandable.
- **[A12-1] (item 12)** Effort = the existing per-set RPE/RIR label; no new logging field. **[A12-2]** The existing primary/synergist weighting may be kept, adjusted, or replaced as long as the per-muscle-base + effort-scaling outcome holds.

## Flagged build-time decisions (not blocking dispatch)
- **[12a — effort fallback]** Blank/absent logged effort needs a defined default for scaling. **Recommended:** treat blank as **medium** effort (no lengthen/shorten). User did **not** pin this — final call to the builder. (In `brief-12`.)
- **[12b — table granularity]** Base-recovery values may be defined at the fine-grained taxonomy or the coarse 7-group level; the user's intent holds either way. (In `brief-12`.)

## Cross-cutting constraints
- **"Today" must respect the configured day-boundary cutoff.** Item 10's "attribute to today" and any day math must use the app's logical-day definition (the day-reset setting), not raw midnight.
- **Item 9 must reuse the existing keep-logged-days + rebalance mechanism** — do not build a second preservation path.
- **Program-tab surface** (`ProgramFragment.kt` / `fragment_program.xml`) is a shared integration point for items 4, 8, 10, 11 — serialize or single-owner (see hazard note).
- **Build** with `./build.sh` (not `./gradlew`). **No commits or releases** unless the user asks. **No on-device / automated UI tests** unless the user asks — verify via build + unit tests only.
