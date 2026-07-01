# UX Batch — 2026-07 — INDEX

**Prepared for:** Project-lead orchestrator
**Source:** User feature/UX request batch, relayed via coordinator (items originally numbered 1, 2, 6, 7, 8, 9, 10, 11 — the user's own numbering; 3–5 were intentionally omitted).
**Status:** Understanding CONFIRMED by the user. Briefs ready for the orchestrator.

## Confirmation note
The user's clarifying answers and final sign-off arrived **relayed via the coordinator** (the standing verbatim-relay arrangement for this intake agent). Two items were **re-scoped during clarification** and re-confirmed:
- **Item 8** dropped its original "show a second past session" ask → it is now a **presentation-polish** item on the existing last-session line only.
- **Item 9** was corrected from "preset increment buttons" → a **calculator-style manual-entry pad** supporting **+ / − only**.

Producing these documents is **intake only**. It is NOT a dispatch to the orchestrator — dispatching is a separate step the user will trigger.

## Items

| ID | Title | Type | Brief file | Status |
|----|-------|------|------------|--------|
| 1 | Copy all prompt logs to clipboard | Feature | `brief-01-copy-prompt-logs.md` | Ready |
| 2 | "Are you sure?" before regenerating the program | Safety feature | `brief-02-confirm-regenerate.md` | Ready |
| 6 | Icon overhaul — Lucide monochrome + colorful pack (full sweep) | Feature | `brief-06-icon-overhaul.md` | Ready |
| 7 | Configurable day boundary (default 04:00) | Feature (wide-reaching) | `brief-07-day-boundary.md` | Ready |
| 8 | Polish the "Last session" line on the logging screen | Feature (polish) | `brief-08-last-session-polish.md` | Ready |
| 9 | Calculator keypad (+/−) for manual weight entry | Feature | `brief-09-weight-calculator-keypad.md` | Ready |
| 10 | Auto-attribute any other-day workout to today | Refinement / bug | `brief-10-auto-attribute-workout.md` | Ready |
| 11 | AI injury-text sufficiency check in the setup wizard | Feature | `brief-11-injury-sufficiency-check.md` | Ready |

## Merge / cluster + parallelization guidance
Grounded in the files each item touches in the current tree (expect the generation-related files to be in their **v1.13.0 post-overhaul** shape by build time — see Cross-cutting constraints).

**Cluster A — Log-workout screen (items 8 + 9).** Both edit the same screen (`ui/log/LogWorkoutFragment.kt` + `res/layout/fragment_log_workout.xml`). Give them to **one worker** to avoid layout collisions. Small, self-contained.

**Cluster B — "What day is it" seam (items 7 → 10, ordered).** Item 7 redefines the day boundary that item 10 relies on when it decides which day a finished workout attributes to. **Do item 7 first** (or one worker owns both) so item 10 attributes to the correct 04:00-defined "today." Item 7 is the largest item in the batch (touches ~80 date derivations across ViewModels, the repository, History, streaks, PR/trend, and the auto rest/missed logic) and warrants its own focused pass.

**Cross-group hazard — Item 6 (icon full sweep) vs every layout-touching item.** The icon sweep edits drawables *and* many layout/menu files; items 2 (dialogs), 8 and 9 (log layout) also edit layouts. **Sequence item 6 LAST** (after 2/8/9 land) or have its worker rebase onto their changes — otherwise expect merge conflicts on the same layout files.

**Independent / parallelizable:** item 1 (Settings/Profile + PromptLog read), item 2 (Program regenerate action), item 11 (setup wizard + AiRepository). Item 11 must **coordinate** with the P4 injury work now landing in v1.13.0 (see below) — do not duplicate or contradict it.

**Suggested order:**
1. **Item 7** (day boundary) — foundational; unblocks item 10.
2. **Item 10** (auto-attribute) — consumes item 7's day rule.
3. **Cluster A: items 8 + 9** (one worker, log screen).
4. **Items 1, 2, 11** — independent, run in parallel.
5. **Item 6** (icon full sweep) — last, after the layout-touching items settle.

| Group | Items | One worker? | Note |
|-------|-------|-------------|------|
| A | 8, 9 | Yes | Same log screen + layout |
| B | 7, 10 | 7 first, then 10 (or one worker) | Shared "today" definition; 7 is large |
| — | 1 | Independent | PromptLog read + clipboard |
| — | 2 | Independent | Confirm dialog on regenerate |
| — | 11 | Independent, coordinate with P4 | Setup wizard + AiRepository (post-overhaul) |
| — | 6 | Independent but sequence LAST | Full-app layout/drawable sweep; conflicts with 2/8/9 |

## Confirmed decisions (baked into the briefs)
- **1** — Copy the **full set** of prompt-log entries (each prompt + its AI response), to **clipboard only** (no share sheet). No API-key concern: the key is an HTTP header, never in the prompt text.
- **2** — A **simple confirmation** (not a hard block) on the **Generate/Regenerate-program** action in AI/Program settings only.
- **6** — Navigation/functional icons → **Lucide monochrome**; celebratory/muscle/achievement/gamification glyphs → **colorful** (preferably a nicer colored icon pack, not required to remain emoji). **Full-app sweep.**
- **7** — Day boundary is **adjustable in Settings, default 04:00**, and applies **everywhere** the app derives "which day it is."
- **8** — **Presentation polish only** of the existing last-session line. No data change; one session; warm-ups stay hidden.
- **9** — Manual weight entry gains **+ / − arithmetic** on the current value (add/subtract a number). **No × ÷.** kg only.
- **10** — Finishing **any** non-current-day workout **auto-attributes to today** and **rebalances the week**, **silently** (no confirmation, no button). The "Do this workout today" button requirement is removed; the direct "Start Day Workout" path gets the same behavior.
- **11** — **AI-driven** injury-sufficiency check, **setup wizard only**; insufficient text triggers follow-up questions whose answers **rewrite** the injury box into one clean description; **empty injury = skip** the check.

## Assumptions applied (user may override)
- **[A-1] (item 1)** The button lives near the existing Prompt Log (Profile → Settings). Placement is not load-bearing to the user; adjust if a better home exists.
- **[A-2] (item 6)** "Functional/navigation" includes bottom-nav, top-menu/toolbar, and in-screen action icons; "colorful/gamification" includes muscle badges, the workout-result dialog, achievements, and level/XP glyphs. The exact per-icon split is the builder's judgment within this principle.
- **[A-3] (item 7)** The adjustable cutoff is a whole-hour setting (e.g. 0–6). No DB migration or data backfill is expected — the boundary is a *derivation* over each session's existing timestamp, so historical sessions are simply reinterpreted, not rewritten.
- **[A-4] (item 9)** The +/− pad operates on the **total** weight value shown in the field (not per-side plate math — per-side was explicitly not requested).
- **[A-5] (item 11)** "Sufficient" is judged by the AI (has a body part / restriction it can actually plan around), not a hard character count.

## Cross-cutting constraints
- **Item 7 must reconcile with the shipped auto rest/missed-day feature (v1.12.0).** That feature decides "today" and backfills empty past days as REST/MISSED on app open, keyed off `LocalDate.now()` / epoch-day math (`RestDayBackfill`, `WorkoutRepository.autoLogRestDays`). Under the new boundary, "today"/"yesterday" for that logic must use the same cutoff, or rest/missed days will be mis-dated (e.g. a 02:00 launch must not yet close out the previous day).
- **Item 11 is the INPUT side complementing P4.** The generation-quality-overhaul batch (**v1.13.0**, feature branch, tests green, mid-ship) already reworks injury *handling* (empty-injury = no-op; severity-scaled selection) inside `AiRepository.kt`, and reworks `getOnboardingQuestions`. Item 11 feeds a clean injury description *into* that flow. **Plan against the post-overhaul shape of `AiRepository.kt` / `getOnboardingQuestions`, not the current code.** Do not duplicate or contradict P4.
- **Build** with `./build.sh` (not `./gradlew`). **No commits or releases** unless the user asks. **No on-device / automated UI tests** unless the user asks — verify via build + unit tests only.
