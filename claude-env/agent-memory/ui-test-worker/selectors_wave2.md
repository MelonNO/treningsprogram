---
name: selectors-wave2
description: Selectors, nav, and trigger/seeding details for Wave 2 features B1 (weekly coach summary) and B2 (program-change rationale)
metadata:
  type: reference
---

Wave 2 (feature-batch-2026-06) selectors/nav verified from source + on-device run on APK md5 6be0206bba1036e150cee5bd624a155f.

## B2 — "Why your program changed" rationale (Program tab)
- View: card `card_rationale` titled "🧠 Why your program changed", text `tv_rationale`. Sits in `fragment_program.xml` directly BELOW `card_week` (the This-Week calendar card) and ABOVE the day section. Confirmed visually in that exact slot.
- Visibility logic (ProgramFragment): shown ONLY when `viewModel.weekRationale` `isNotBlank()`, else GONE. No error/empty card — just hidden.
- Storage: `planned_exercises.rationale` TEXT column (added by Room migration; identity_hash after migration = ba04bd2391065e54448a39b2ac039dc5). One rationale per week's plan; same value across that week's rows.
- Seed has-rationale: `UPDATE planned_exercises SET rationale='...' WHERE weekStart=<ms>;` then `PRAGMA wal_checkpoint(TRUNCATE);`. Natural blank/old-plan state = rationale=''.

## B1 — Weekly coach summary (Profile → Settings → "Coach Summary")
- Nav: bottom-nav `profileFragment` (ProfileFragment hub) → tap `text:"Settings"` → SettingsFragment → row `row_coach_summary` (label "Coach Summary") → `action_settings_to_weekly_summary` → WeeklySummaryFragment.
- WeeklySummaryFragment ids: list `rv_summaries`; empty container `layout_empty` with title "No weekly summary yet" + subtitle `tv_empty_subtitle` ("Keep logging workouts — your coach summary is generated automatically once a week."). Empty shown when list empty, else rv shown.
- item_weekly_summary.xml ids: `tv_week` ("Week of 2026-W26"), `tv_date` ("24 Jun 2026"), `tv_summary_text`.
- Storage: table `weekly_summaries(id, weekKey TEXT e.g. "2026-W26", createdAtMs INTEGER, summaryText TEXT)`. Newest-first by createdAtMs DESC.

## B1 launch trigger (MainActivity.checkAndGenerateWeeklySummary) — IMPORTANT for empty-state determinism
- Fires on app launch. Generates iff: apiKey set AND onboarding complete AND ≥1 completed session in lookback (getRecentSessions(12)) AND not-yet-this-ISO-week.
- BELT-AND-SUSPENDERS guard: returns early if `prefsManager.lastWeeklySummaryWeek == thisWeek` (line ~286) OR if `weeklySummaryDao.countForWeek(thisWeek) > 0`. On success sets lastWeeklySummaryWeek=thisWeek.
- To test EMPTY state deterministically WITHOUT the trigger re-populating: just DELETE FROM weekly_summaries but LEAVE the pref. Because lastWeeklySummaryWeek is already == thisWeek (a summary generated earlier this week), the launch trigger no-ops → table stays empty. Verified: table stayed at 0 after a stopApp:true relaunch. (Don't try to clear the EncryptedSharedPreferences pref — it's encrypted.)
- Non-blocking confirmed: generation runs off the UI thread; all tabs navigable immediately on launch, no ANR/FATAL in logcat.

## Live AI exercised (2026-06-24)
- The device HAD an sk-ant key configured (treningsprogram_secure_prefs.xml has the crypto keyset + encrypted entries). The weekly summary was LIVE-generated on launch: text named the real exercises (bench press, sled push, squats), real set data (9 reps@50kg, 5 reps@70kg), 48-min session, and real missed groups (back/shoulders/arms). So B1 live path is real.
- B2 rationale was SEEDED (generating a live plan would overwrite the existing plan); card render verified with seeded marker text. Live rationale wording not separately exercised this run.
