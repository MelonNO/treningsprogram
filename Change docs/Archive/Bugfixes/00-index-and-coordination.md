# Index & Coordination — 15 issue briefs (one agent per issue)

This folder contains 15 self-contained issue documents, one per bug/feature, for a separate coding agent each. This index exists because **some issues share the same code and must not be run as fully independent parallel agents** — doing so causes merge conflicts and half-fixes.

## The 15 issues
1. Resume-workout button: wrong route + stale label — `issue-01-resume-button.md`
2. Rest timer resets on rotation — `issue-02-rotation-timer-reset.md`
3. Rotation advances to next exercise — `issue-03-rotation-next-skip.md`
4. Entered weight reverts; future prefill from last actual — `issue-04-weight-persistence-prefill.md`
5. Swipe hides timer but must keep counting — `issue-05-swipe-hide-not-stop.md`
6. Swipe-up to manually start a timer — `issue-06-swipe-up-manual-timer.md`
7. Rest timer as a live notification — `issue-07-timer-notification.md`
8. Crash when rest timer finishes — `issue-08-crash-timer-finish.md`
9. Time estimates ignore admin time — `issue-09-time-estimate-admin.md`
10. No exercise images render — `issue-10-images-missing.md`
11. Exercise name → external explanation link — `issue-11-exercise-explanation-link.md`
12. Mid-exercise easier/harder calisthenics swap — `issue-12-easier-harder-swap.md`
13. AI ignores rubber-band equipment constraint — `issue-13-equipment-rubber-bands.md`
14. Full user-data export/import — `issue-14-data-export-import.md`
15. Crash on "Awesome!" — `issue-15-crash-awesome-button.md`

## Shared root causes — do NOT parallelize these
- **Rotation cluster: 02 + 03.** Almost certainly one bug (active-session screen recreated on rotation, losing/re-triggering state). **Assign 02 and 03 to the same agent** (or do as one fix). Note 04's in-session persistence touches the same state area.
- **Rest-timer lifecycle cluster: 05 + 06 + 07 + 08.** All touch the timer subsystem. **Assign to one agent** that builds a single background-capable, notification-backed countdown; 05/06 are gestures on top, 08's crash most likely lives in the finish path that 07 formalizes.
- **Crash pair: 08 + 15.** Possibly a shared completion/teardown path — have whoever fixes one check the other's stack trace.
- **Catalog cluster: 10 + 11 + 12 + 13.** All depend on the exercise catalog and its identity (id/name/equipment/level). **Decide the catalog source once, first**; then these can proceed (10 is the unblocker for visuals).

## Suggested order (by user impact)
1. **Crashes & broken core use first:** 15, 08 (then 07 as the proper timer fix), 01, 10.
2. **State correctness:** 02+03 (together), 04.
3. **Timer UX:** 05, 06 (on top of 07).
4. **Estimates & catalog features:** 09, 11, 12, 13.
5. **Data portability:** 14.

## Global constraints for every agent
- **Diagnose before patching** — for crashes (08, 15) capture the stack trace; for data symptoms confirm logic vs. test data before changing thresholds.
- **Do not regress working features** — coaching notes, "Last:" reference, Effort capture, distinct Home states, completion-modal summary/PRs/achievements, rest-timer AI-suggested default + adjust, "Next" as secondary, rounded time estimates, Profile stats.
- **Stay in scope** — touch only your issue's area; if a fix forces a product decision (flagged as "Decision" in the briefs), pick a sensible default and report it, don't silently invent behavior.
- **Confirm the target platform** before doing notification/background work (Issues 07/08) — the notification requirement implies a mobile OS.
- Each agent reports: root cause found, fix made, anything it couldn't verify, and any new dependency added.
