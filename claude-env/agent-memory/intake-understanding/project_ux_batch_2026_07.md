---
name: ux-batch-2026-07
description: Confirmed post-v1.13.0 8-item UX/feature batch at docs/intake/ux-batch-2026-07/ (items 1,2,6,7,8,9,10,11); two items re-scoped mid-intake (8 polish-only, 9 calculator-pad)
metadata:
  type: project
---

Confirmed 8-item UX/feature batch, briefs at `docs/intake/ux-batch-2026-07/` (INDEX + brief-01/02/06/07/08/09/10/11). Kept the user's original numbering (3–5 intentionally absent). Understanding confirmed via coordinator relay; NOT yet dispatched to orchestrator (separate user step).

**Why:** batch of loose UX asks for the Android workout app, landing AFTER the generation-quality-overhaul (v1.13.0, which reworks AiRepository.kt injury handling / getOnboardingQuestions — plan item 11 against its post-overhaul shape).

**How to apply / item summary:**
- **1** copy ALL prompt-log entries (prompt+response) to CLIPBOARD only (no share sheet); API key is header-only so no leak.
- **2** simple "are you sure?" (not hard block) on the settings Generate/Regenerate-program action ONLY. Note default regen already preserves logged days; confirmDoToday already confirms.
- **6** full-app icon sweep: nav/functional → Lucide monochrome; celebratory/muscle/achievement/gamification → colorful (nicer colored pack preferred over emoji). SEQUENCE LAST (conflicts with layout items 2/8/9).
- **7** day boundary adjustable in Settings, DEFAULT 04:00, applies EVERYWHERE "which day is it" (log date, History, streaks, auto rest/missed, today's plan, week-start, PR/trend). Reconcile with shipped v1.12.0 auto rest/missed (RestDayBackfill/autoLogRestDays decide today on onStart). Derivation-only, no DB migration. Pre-cutoff app-open does NOT yet close out prev day.
- **8** RE-SCOPED to PRESENTATION POLISH of the EXISTING single "Last: …" line on log screen (tvLastSession). User dropped the original "add 2nd session" idea. No data change, warm-ups stay hidden.
- **9** RE-SCOPED: NOT preset increment buttons (those already exist as −/+2.5kg). It's a CALCULATOR-STYLE manual-entry pad for the WEIGHT field: type 60, then +5 → 65. **+/− ONLY, no ×÷**, kg only, floors at 0. Operates on total (not per-side).
- **10** auto-attribute ANY other-day workout to TODAY on completion + rebalance week, SILENT (no confirm, no button). Remove "Do this workout today" button requirement; direct "Start Day Workout" path (btnStartDayWorkout, passes dayOfWeek, moveFromDay=0) must get same behavior. Uses item-7's "today". Reference = P2 commitDayMove path.
- **11** AI-driven injury sufficiency check, SETUP WIZARD ONLY; insufficient → follow-up questions → REWRITE injury box (not append); EMPTY injury = skip. Input side complementing P4 (v1.13.0).

**Clusters/order for orchestrator:** A=items 8+9 (one worker, log screen). B=7→10 (7 first, defines "today"). Independent: 1, 2, 11 (11 coordinate w/ P4). Item 6 LAST. See [[project_generation_quality_overhaul_2026_07]] for the P4/v1.13.0 context. Follows [[reference_intake_doc_format]] and [[feedback_plain_language_questions]] (item 9 got misread technically first — the plain-symptom reframe surfaced it was about the entry PAD, not buttons).
