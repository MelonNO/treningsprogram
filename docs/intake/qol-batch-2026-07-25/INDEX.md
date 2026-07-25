# Intake Index — QoL / Generation / History batch (2026-07-25)

Prepared for: **Project-lead orchestrator**
Source: user's 10-item request of 2026-07-25, clarified in one Q&A round (relayed verbatim via coordinator).
Status: **CONFIRMED** — user answered every clarifying question per item; improvements 2b (per-gym exclusion field) and 9c (touch-read on strength/reps charts) explicitly accepted.

**Confirmation note:** the user's sign-off was RELAYED verbatim via the coordinator (per-item answers to the intake questions; no items were disputed or withdrawn). Two decisions were explicitly **method-delegated** by the user ("you choose" / "make what makes the most sense") — the choices made are listed under *Decisions made under delegation* below and are veto-able. Creating these documents does NOT dispatch the orchestrator; dispatch is a separate user instruction.

## Items

| ID | Title | Type | Brief file | Status |
|----|-------|------|-----------|--------|
| 01 | Delete a logged set mid-workout (with confirm) | Feature (small) | brief-01-delete-set-midworkout.md | Confirmed |
| 02 | Per-gym "exercises to avoid" + exclude Chest-Supported DB Row at Home Gym | Feature (accepted improvement) | brief-02-per-gym-exercise-exclusions.md | Confirmed |
| 03 | Estimated calories burned (summary, Recap, Stats weekly) | Feature | brief-03-calorie-estimates.md | Confirmed |
| 04 | History sub-tab → monthly week-browser mirroring the Program tab | Feature (large) | brief-04-history-week-browser.md | Confirmed |
| 05 | Generation must survive minimizing the app | Bug | brief-05-generation-background-survival.md | Confirmed |
| 06 | Monday plan ready without opening the app + launch trigger unreliable | Bug + Feature | brief-06-monday-autogen.md | Confirmed |
| 07 | Warm-up toggle auto-clears after every logged set | UX change (small) | brief-07-warmup-toggle-autoclear.md | Confirmed |
| 08 | Rest-timer completion sound must not mute music | Bug | brief-08-rest-timer-music.md | Confirmed |
| 09 | Body-weight chart follows date range + touch-to-read on BW/strength/reps charts | Feature | brief-09-bodyweight-chart-range-touch.md | Confirmed |
| 10 | Moved-workout finish = normal finish (celebration + hop to Home) | Bug | brief-10-moved-workout-finish.md | Confirmed |

## Merge / cluster / parallelization guidance

**Cluster A — logging screen (items 01, 07, 10):** 01 and 07 both edit the log-workout screen (`ui/log/LogWorkoutFragment.kt` + ViewModel); 10 edits the completion flow in the SAME file (`startCompletionFlow`) plus `ProgramFragment.onResume`. Give these to ONE worker or serialize strictly — concurrent edits to `LogWorkoutFragment.kt` will collide. All three are small.

**Cluster B — generation reliability (items 05, 06):** one worker. Same machinery: how/where generation executes (background survival) and when it triggers (Monday readiness + launch-trigger bug). 06's outcome depends on 05's reliability; building them apart risks two half-solutions.

**Hazard — `AiRepository.kt` seam:** item 02 (prompt exclusions + enforcement) and Cluster B both touch `data/repository/AiRepository.kt`. Serialize 02 against Cluster B (or fold into the same worker).

**Hazard — completion-summary surface:** item 03 places a calories figure on the workout-complete summary, which lives in the same completion-dialog code Cluster A's item 10 touches. Land 03's summary piece after Cluster A (or same worker for that one surface).

**Standalone:** 08 (only `RestTimerService.kt`), 09 (Progress sub-tab charts), 04 (History sub-tab overhaul — largest item; different files from 03's Recap/Stats surfaces, so 03 ∥ 04 is acceptable with light coordination inside `ui/history/`).

**Suggested order:** Cluster A → 08 → Cluster B → 02 → 09 → 03 → 04 (largest last).

| Group | Items | Parallel with |
|-------|-------|---------------|
| A (one worker) | 01, 07, 10 | B, 08, 09 |
| B (one worker) | 05, 06 | A, 08, 09 |
| 02 | 02 | anything except B (AiRepository seam) |
| 08 | 08 | anything |
| 09 | 09 | anything |
| 03 | 03 | anything except A's item-10 window (completion dialog) |
| 04 | 04 | anything (watch `ui/history/` overlap with 03) |

## Confirmed decisions (user's own words / explicit answers)

- 01: **delete only** — no in-place edit of reps/weight; deletion asks "are you sure".
- 02: exercise stays allowed at other gyms; the **per-gym "exercises to avoid" improvement** was chosen over a one-off hard-code; scope is **just this row** (and obvious name variants), not other prone-on-bench exercises.
- 03: calories shown on the **workout-complete summary, the session Recap, and Stats (weekly total)**; rough estimate accepted; **75 kg fallback** when no body-weight data exists.
- 04: it is the **History sub-tab** (flat list) being replaced; **search and date-range filter MUST survive**; exercise details = **performed data AND exercise info, reachable from the same tap**; top-level layout and detail presentation are delegated to the builder ("what makes the most sense in terms of UI/UX and cohesiveness").
- 05: symptom = **no plan saved, silently**; screen-off with app in foreground works fine; desired outcome = generation finishes unattended + existing "plan ready" notification.
- 06: user chose **(b) plan ready on Monday without opening the app** AND **(c) the existing open-the-app trigger is not firing for them** ("On Mondays a new week generation is not automatically started"). Not (a) — no generate-on-last-workout trigger.
- 07: toggle clears **right after each logged set** (option a).
- 08: desired = **notification sound plays above the music; music keeps playing** (today music is muted while the sound plays).
- 09: vertical line snaps to the **nearest actual weigh-in** (date + weight); chart follows the existing range picker (no range = all-time); **same touch-to-read added to strength and reps charts** (accepted improvement).
- 10: moved-workout finish behaves like a normal finish; **the rebalancing itself stays exactly the same** (same trigger, silent).

## Decisions made under delegation (veto-able)

- **02 strictness (user said "you choose"):** chosen = **hard guarantee** — while a gym with an exclusion is selected, the excluded exercise must never appear in a saved plan, even if the AI ignores the instruction (deterministic enforcement, not prompt-text-only). Rationale: prompt-only exclusion is advisory and this app's philosophy elsewhere is strict gating.

## Assumptions applied (each veto-able)

- A1 (03): calorie figures also appear for **past** sessions (derived from stored data), not only new ones.
- A2 (03): body weight used = most recent weigh-in **at or before** the session date; 75 kg only when none exists at all.
- A3 (04): history depth = everything ever logged; rest/missed days appear on their day chips; a path from a day/session to its full **Recap** is retained. (User confirmed these in Q&A — listed here for visibility.)
- A4 (04): per-set delete (red ×) and "Edit date" are **not required** to survive the History rebuild (user listed only search + filter as must-survive); the builder may keep them where natural, and dropping them is acceptable.
- A5 (06): generation may run late Sunday night / early Monday — "ready by Monday morning" is the outcome; exact schedule is the builder's choice. If the phone couldn't do it unattended (off/no network), opening the app must reliably trigger it.
- A6 (08): a momentary automatic volume dip by the OS is tolerable, but the music must never pause/mute — it keeps playing audibly through and after the chime.
- A7 (10): the rebalance may briefly alter the visible week during/after the celebration animation; end state must be correct, no visual breakage.

## Cross-cutting constraints

- Build via `./build.sh` (never bare `./gradlew`).
- No commits or releases unless the user explicitly asks.
- No Waydroid / Maestro / on-device UI tests (standing instruction) — verify via build + unit tests.
- Frugal live-API use: any live generation check must be minimal and decision-driven (standing rule).
- Schema/prefs additions (item 02's new per-gym field; item 03 if anything is persisted) must survive **backup/restore** (bump backup version as the project convention requires).
