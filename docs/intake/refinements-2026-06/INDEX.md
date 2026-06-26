# Intake Index — Refinements Batch (post-v1.9.1)

**Prepared for:** Project-lead orchestrator
**Source:** User's own hands-on testing of v1.9.1 — 10 items (mix of bugs and feature requests).
**Date:** 2026-06-25
**Status:** Understood and confirmed. NOT yet dispatched to the orchestrator (dispatch is a separate, later user instruction).

## Confirmation note
The understanding below was confirmed item-by-item. The user's sign-off on the final restatement (and the three open points A/B/C) was **relayed via the coordinator**, not given to the intake agent in the user's own words. Relayed approval reflects the user's intent as conveyed, but if the orchestrator needs the gate re-affirmed, it should confirm with the user directly. Creating these documents is not the same as dispatching work.

All briefs are **outcome-only**: they describe the end result and user experience, never the implementation. The "how" belongs to the orchestrator and its workers.

## Items

| ID  | Title                                                        | Type            | Brief file                                  | Status |
|-----|-------------------------------------------------------------|-----------------|---------------------------------------------|--------|
| B01 | Enlarge in-session progress-bar tap target                  | Bug / usability | brief-B01-progress-bar-hitbox.md            | Ready  |
| B02 | New bodyweight exercise defaults to 0/"BW", not prev weight | Bug             | brief-B02-bodyweight-default-weight.md      | Ready  |
| B03 | Progress-tab exercise picker sorted by most sessions        | Improvement     | brief-B03-exercise-picker-sort.md           | Ready  |
| B04 | Achievements list defaults to collapsed                     | Improvement     | brief-B04-achievements-collapsed.md         | Ready  |
| B05 | Clean up downloaded update APKs                             | Housekeeping    | brief-B05-apk-cleanup.md                     | Ready  |
| B06 | Tap recovering muscle → highlight the exercise(s) that caused it | Feature    | brief-B06-recovery-highlight-exercise.md    | Ready  |
| B07 | Hide empty data fields instead of guidance copy            | Improvement     | brief-B07-hide-empty-data-fields.md         | Ready  |
| B08 | Choose specific rest day(s) when generating (two modes)    | Feature         | brief-B08-rest-day-selection.md             | Ready  |
| B09 | Mid-week regenerate preserves logged days                  | Feature         | brief-B09-regenerate-preserve-logged-days.md| Ready  |
| B10 | AI prose-instead-of-JSON: prevent stall + recover          | Bug / hardening | brief-B10-ai-json-reliability.md            | Ready  |

## Clusters, integration seams, and parallelization

### Cluster 1 — AI generation seam (B08, B09, B10) — ONE coordinated unit
All three touch `AiRepository` (the generation flow) and the generation prompt; B08 also touches setup/Settings preferences.
- **B10** hardens *how the AI response is handled* (reliability).
- **B08** changes *how the user specifies* training/rest days (input to generation).
- **B09** changes *what gets (re)generated* mid-week and how the existing regenerate button behaves.

**Sequencing (important):** land **B10 first, or together with B08/B09**, so the new generation paths introduced by B08/B09 inherit the robust no-JSON/truncation handling rather than reintroducing the stall. Because all three edit the same hot seam, treat them as a tightly-coordinated group (one worker, or carefully serialized workers) — not three independent parallel tasks. This is the highest-risk part of the codebase.

### Cluster 2 — Active workout / logging (B01, B02)
Both touch the in-session logging screen (`LogWorkoutFragment` / `LogWorkoutViewModel`). Independent of each other in intent, but share the file — can be one worker.

### Cluster 3 — Progress / Recap / History family (B03, B07)
Both touch the Progress/Recap/History/Stats/Trends screens. B07 is app-wide empty-state behavior; B03 is the Progress-tab picker. Related surface; can be one worker.

### Cluster 4 — Profile / Home widgets (B04, B06)
B04 = Profile achievements default state; B06 = Home recovery tap → session-view highlight. Different files; can run in parallel or as one worker.

### Standalone — B05 (update flow)
Touches the update/download path only (`MainActivity` / `SettingsAboutFragment` / `UpdateChecker`). Independent of every other item; safe to parallelize.

### Cross-group hazards
- The three AI-seam items (B08/B09/B10) must NOT be split across uncoordinated parallel workers — they edit the same generation code and prompt.
- B05's APK-version check should read the app's installed version; no overlap with other items but keep its "update completed" check honest (don't delete on a failed/partial install).

### Suggested order
1. **B10** (reliability hardening — foundation for the AI seam).
2. **B08 + B09** (build on the hardened seam; coordinate since they share the prompt and active-program logic).
3. In parallel with the above (independent): **B01/B02**, **B03/B07**, **B04/B06**, **B05**.

## Confirmed decisions
- **B02:** A bodyweight exercise *previously logged with added weight* keeps its own last weight; only the cross-exercise weight bleed is the bug.
- **B03:** Sort by session-count descending, ties broken alphabetically; order preserved while typing/filtering.
- **B04:** Always start collapsed; do not remember the user's last expand/collapse state.
- **B05:** Cleanup happens on app launch (not the exact install-finish moment) **and** only after verifying the update actually completed (installed version is the new one). Never delete on a failed/partial install.
- **B06:** Highlight **all** exercises in that session that hit the muscle; highlight lives in the session view only (no culprit name on the Home row).
- **B07:** Hide all per-field/per-chart empty data app-wide; keep **one** short top-level line on an otherwise-completely-empty screen (accepted improvement — avoids a blank "looks broken" screen).
- **B08:** Two modes via a "you choose days" checkbox. Default (off): user selects specific **rest days**, days/week derived from the selection. Alternative (on): old behavior — user picks the **number** of training days and the AI chooses which days are rest. Configurable in **both** setup and Settings.
- **B09:** Preserve any day with **≥1 logged exercise** (not "a completed workout"); per-day rule, not a today-onward boundary; the AI may **overwrite still-unlogged planned days** to rebalance around logged days; default behavior replaces the existing Program-tab regenerate button; full-fresh-week (overwrites all *planned* exercises incl. logged days' plans) moves behind Settings; **never** deletes logged sets / history / any other user data; current Monday→Sunday week; preserved day is from **this** week; no change to automatic start-of-week generation.
- **B10:** Treat no-JSON **and** truncated responses as a rejected/retryable attempt; clear user-visible error if all retries fail; do **not** strip the model's reasoning — reasoning-then-JSON is acceptable as long as the JSON is extractable. **Priority order: (1) high-quality plan, (2) fewer rejects, (3) ideally never fails — quality outranks reject-rate.**

## Assumptions applied (user may override)
- **[A1 — B07]** "Empty data fields" means the per-chart/per-section empty states across Recap, Progress, History, Stats, and the per-exercise Trends screen (these are the surfaces with "log more to see X" copy). If the user meant a narrower set, flag and narrow.
- **[A2 — B09]** "Regenerate" refers to the manual regenerate action the user invokes on the Program tab; the automatic start-of-week generation is unchanged (user confirmed "no change to auto").
- **[A3 — B10]** "Reacts as if it was a not-accepted response" maps to the existing per-attempt rejection/retry path (currently used for quality/duration failures), reusing the same attempt limit and the same kind of clear failure surfacing.

## Cross-cutting constraints (apply to every brief)
- Build via `./build.sh` (not `./gradlew` directly).
- No commits, tags, or releases unless the user explicitly asks.
- No on-device or automated UI tests unless the user explicitly asks.
- Diagnose-first for the bugs (B02, B10, and the root of B01's "finicky tap"): confirm the cause before changing behavior.
