# Workout App — Polish & Debug Pass (instructions for a coding agent)

## Goal
Bring the app to a stable, consistent, well-polished state: fix known bugs, hunt for unknown ones systematically, and remove rough edges — without regressing existing working features. "Zero bugs" is the target, not a guarantee; where you cannot verify something, say so explicitly in the final report rather than assuming it's fine.

## How to work
- Work in **passes, in this order**: (1) data/correctness bugs, (2) state & flow bugs, (3) cross-screen consistency, (4) visual design language, (5) formatting nits. Earlier passes can change what later passes need.
- Keep each change small and verifiable. After each fix, confirm it actually resolves the issue and didn't break a neighbour.
- **Investigate root cause before changing values.** Especially for the data-integrity items below — confirm whether a symptom is a logic bug or just leftover test data before altering thresholds or formulas.
- **Flag, don't guess, on product decisions.** Where a fix implies a product choice that isn't already decided (see "Decisions to surface"), pick a sensible default, apply it consistently, and list it for the owner to confirm — don't silently invent behaviour.
- Note any new dependency you add and why.

---

## Do NOT regress (these already work — keep them intact)
- Per-exercise AI coaching rationale on Program cards.
- The "Last: …" previous-session reference during logging.
- Effort (Easy / Moderate / Hard) capture after a set.
- Distinct Home states for training day / rest day / completed day, with the actionable "View Today's Session" action.
- Week overview with per-day focus + completion counts.
- Recent Workouts content summary (focus + exercise/set counts).
- Completion modal showing real session output (exercises, sets, volume), Personal Records, and Achievement unlock.
- Rest timer with "AI suggested" value and Skip / −30s / +30s.
- "Next" being visually secondary to the primary LOG SET action.
- Time estimates rounded to whole minutes.
- Profile/Stats screen with totals, PR list, and Achievements (x/14).
- Session progress bar that fills through the workout.

---

## Part A — Known issues to fix (desired end-state + Done when)

### Data integrity (highest priority)
2. **Derived totals agree everywhere.** Workouts, sets, total volume, streak, XP/level, and PRs show the same values wherever they appear (Home, Program, Stats, Profile, completion modal). *Done when:* the same metric never shows two different numbers across screens.
3. **Week progress bar reflects real completion.** *Done when:* the bar is near-empty at 0/20 and its fill tracks the "X / Y done" figure.

### State & flow
4. **In-progress workouts survive interruption.** Phone lock, app kill, or crash mid-session must not lose logged sets; the session resumes where it was. *Done when:* force-quitting mid-workout and reopening restores the exact state.
5. **Offline operation.** Viewing and logging work with no network. *Done when:* a full workout can be logged in airplane mode.
6. **Edits/backdating recompute correctly.** Correcting, adding, or backdating a set updates all dependent totals, PRs, and charts. *Done when:* editing a past set changes the affected summaries and PRs consistently.
7. **Logging beyond the planned set count is graceful.** *Done when:* a 6th set on a 5-set target logs cleanly (e.g. "Set 6"), no error or cap.
8. **No dead-end controls.** Every button-shaped element does something; skip / back / reorder / supersets all work without trapping the user. *Done when:* every interactive element on every screen has a working action.

### Picture / exercise images (correctness)
9. **Every exercise shows a correct, present image via stable binding.** Bind images to exercises by a stable catalog ID (not runtime name-matching), and constrain the AI's exercise selection to that catalog so an image always exists and always matches. *Done when:* every prescribable exercise renders an image that depicts that exercise; no gaps, no mismatches, no busy/irrelevant photos. *(A public-domain catalog such as free-exercise-db, keyed by id with an images array, is a suitable source for now; the binding must be ID-based so the art can be swapped later without code changes.)*

### Visual consistency
10. **Progress bars distinguish filled from remaining** — accent for completed, neutral/muted for remaining; the remaining track must not read as a second value. *Done when:* no progress bar uses two saturated colours implying two metrics (check Home XP, level bar, completion modal, Profile).
14. **Weekly Challenges live in one canonical place** (currently duplicated on Home and Profile). *Done when:* the same challenge list isn't shown twice without purpose.
15. **Weekly Challenges track and self-complete** — reflect real progress and mark complete + award XP when the condition is met. *Done when:* meeting a challenge condition visibly completes it.
---

## Part B — Systematic bug hunt (find what's not listed above)
Do not stop at Part A. Audit each category below across **every** screen and flow:

- **Data integrity:** recompute every displayed total from source data and confirm it matches; look for off-by-one counts, double-counting, stale caches, and metrics that disagree across screens.
- **State management:** start/pause/resume/abandon a workout; background and foreground the app; check nothing is lost or duplicated.
- **Edge & empty states:** brand-new user with no history; a single exercise / single set; a rest day; exceeding planned sets; a very long exercise list (scrolling); a session with only warm-up sets; missing optional fields (e.g. exercises with no equipment/mechanic data).
- **Numeric & string formatting:** units, rounding, decimals, pluralisation, time formats, large/zero/negative values, very long exercise names (truncation/overflow).
- **Navigation & flow:** every tab, back button, modal dismiss, skip/reorder/superset path; confirm no screen can trap the user and the bottom nav always works (including the active workout).
- **Visual consistency:** spacing, alignment, colour language, button hierarchy, tap-target sizes, image framing across all exercises.
- **Console/log hygiene:** resolve runtime errors and warnings surfaced while exercising the app.

---

## Part C — Verification (required before declaring done)
Exercise the app and confirm:
- [ ] A full workout logged end-to-end, including warm-up, skip, back, reorder, a superset, and exceeding the planned set count.
- [ ] Force-quit mid-workout, reopen → state restored.
- [ ] A complete workout logged in airplane mode.
- [ ] First-run (no data) renders cleanly with no misleading "trends" or crashes.
- [ ] Totals reconcile across Home, Program, Stats, Profile, and the completion modal.
- [ ] Editing/backdating a past set updates all dependent values.
- [ ] Every exercise's image matches its name; no gaps.
- [ ] Every interactive element does something.
- [ ] No leftover false-precision numbers or pluralisation errors.
- [ ] No unresolved console errors during the above.

---

## Final report (deliver alongside the changes)
Provide:
1. Bugs found (known + newly discovered), each with root cause and the fix.
2. Anything changed that touches a product decision (from "Decisions to surface").
3. Anything you could **not** verify or fix, and why — residual risk, honestly stated.
4. Any new dependencies added.
