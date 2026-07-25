---
name: refinements-2026-06
description: Confirmed post-v1.9.1 intake batch (10 user-tested bugs+features) at docs/intake/refinements-2026-06/ — AI-generation cluster (8/9/10), logging fixes, UX hide-empty-states, achievements/recovery, APK cleanup
metadata:
  type: project
---

Confirmed intake batch produced 2026-06-25 at `docs/intake/refinements-2026-06/` (INDEX.md + 10 briefs B01–B10). Source: user's own hands-on testing after v1.9.1 shipped. Full sign-off relayed via coordinator (NOT the user's direct words to me — note this in INDEX). Creating docs ≠ dispatching the orchestrator (separate later instruction).

**The 10 items:**
- B01 (bug/UX): enlarge hit box on the in-session "Exercise X/Y" progress bar (taps open the quick-jump menu).
- B02 (bug): new BW exercise with no history must default weight to 0/"BW", not bleed the previous exercise's weight. BW exercise previously logged WITH added weight keeps its own last weight.
- B03 (improvement): Progress-tab exercise picker sorted by #sessions desc (ties alphabetical), order preserved while typing.
- B04 (improvement): Profile achievements list defaults COLLAPSED (flip `achievementsExpanded` default; no remembered state).
- B05 (housekeeping): delete downloaded `treningsprogram-*.apk` from Downloads — cleanup ON LAUNCH, only AFTER verifying the update completed (installed version == new). Never block on exact install-finish moment.
- B06 (feature): tap recovering muscle on Home still opens last session, now scrolls to + highlights ALL exercises that hit that muscle (highlight in session view only, no Home culprit-name).
- B07 (UX): hide empty per-field/per-chart data app-wide (Recap/Progress/History/Stats/Trends) — remove "log more to see X" copy. ACCEPTED IMPROVEMENT: keep ONE top-level line on a fully-empty screen so it doesn't look broken.
- B08 (feature, AI seam): two-mode rest-day selection via "you choose days" checkbox. DEFAULT (off): user picks specific REST DAYS, days/week derived. ALT (on): old count-only mode, AI picks rest days. Configurable in BOTH setup and Settings.
- B09 (feature, AI seam, highest risk): mid-week regenerate = preserve any day with ≥1 LOGGED exercise (NOT "completed workout"); per-day rule (not a today-onward boundary); AI may OVERWRITE still-unlogged planned days to rebalance around logged days. Default = the existing Program-tab regenerate button. Full-fresh-week (overwrites all planned incl. logged days' PLANS) moves behind Settings; NEVER deletes logged sets/history/user data. Current Mon→Sun week; preserved day is from THIS week. No change to auto start-of-week generation.
- B10 (bug+hardening, AI seam): AI emits reasoning prose, runs long, gets truncated before JSON → `extractJson`→`extractJsonOrThrow` THROWS "No JSON found" which aborts the whole `generateAdaptedProgram` (it's outside the per-attempt rejection path). FIX: treat no-JSON AND truncated responses as a REJECTED ATTEMPT (retry up to limit), then clear user error if all fail. PREVENT: do NOT strip reasoning — reasoning-then-JSON is fine as long as JSON is extractable. PRIORITY (explicit): (1) high-quality plan, (2) fewer rejects, (3) ideally never fails. Quality outranks reject-rate. Same failure class as prior S3 parse-failure work.

**Clusters / sequencing (in INDEX):**
- AI-generation cluster = B08 + B09 + B10 on `AiRepository`/generation flow. SEQUENCING: B10 (reliability hardening) lands before/with B08+B09 so new paths inherit robust handling.
- Logging/active-workout: B01 + B02 (LogWorkoutFragment/ViewModel).
- Progress/Recap/History family: B03 + B07.
- Profile/Home widgets: B04 + B06.
- Update flow: B05 standalone.

**Code seams confirmed (2026-06-25, for the builder's context — verify before acting, may drift):**
- AI request: `claude-sonnet-4-6`, `max_tokens=8192`, no reasoning/verbosity suppression; prompt invites "work through this systematically".
- B10 mechanism: generation loop calls `extractJson()` (line ~427) → `extractJsonOrThrow` which THROWS on prose-only; throw escapes the per-attempt loop. Contrast: quality/duration failures go through `rejectionReasons` + retry.
- B02 prefill: `LogWorkoutFragment` ~line 397-407 prefills from `lastSets.last().weightKg`; the cross-exercise bleed is the bug.
- B01: `binding.progressSession.setOnClickListener(openQuickAccess)` in LogWorkoutFragment (~line 161).
- B04: `ProfileFragment.achievementsExpanded = true` (~line 28), toggled by header.
- B06: `HomeFragment.renderRecovery` tap → `recapTarget.request(item.lastSessionId)` (~line 411).
- B07 copy examples: HistoryRecapFragment "Log a couple of weeks to see your volume trend.", RecapTrendsFragment empty, StrengthChartView "Not enough data yet", HistoryStatsFragment tvStatsEmpty.

**How to apply:** if the user revisits any of these, this batch is authoritative. NOT yet dispatched to orchestrator. See [[intake-doc-format]] for the brief/INDEX shape; [[project-bug-sweep-ux-batch-2026-06]] for the prior batch (S3 parse-hardening + F3 timeout/retry are the predecessors B10 builds on).
