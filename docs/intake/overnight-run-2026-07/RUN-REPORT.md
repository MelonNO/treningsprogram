# Overnight Autonomous Run — 2026-07-01 (user asleep)

**This is a LIVING, crash-resumable status file.** Updated by the coordinator after every stage.
On a Pi crash: a fresh session reads MEMORY.md → `project_overnight_autonomous_chain_2026_07.md` → THIS file → resumes at the first stage not marked ✅ SHIPPED+VERIFIED.

**Standing directives from the user:**
- Run the 3-stage chain below, each gated on the prior being build-verified + shipped. Ship each as its own version WITHOUT waking the user.
- Never block on questions — note them here and CONTINUE. Prioritize shipping what's possible without the user.
- Full authority to act as long as it follows the user's intention.
- **HARD CAP: 20 live Anthropic API calls TOTAL across ALL agents combined** (coordinator + orchestrator + workers). NOT 20 each. Coordinator reserves 0. Key: `/home/migul/treningsprogram/claude k` (gitignored).
- Surgical commits only; leave pre-existing working-tree dirt (`.gitignore`, deleted `Change docs/Archive/*`, `flows/*.yaml`) AND this `overnight-run-2026-07/` folder untouched/uncommitted unless the user wants it. Verify every ship independently (tag + main + live release asset).

---

## API CALL LEDGER (authoritative file: `api-call-ledger.txt` in this folder)
**FINAL: 17 / 20 used** (Stage ② live gen-testing; 3 in reserve, unused). Stages ③/④ need no API calls. Budget respected, never exceeded.

---

## STAGE STATUS

### ① UX batch → v1.14.0
**Status: ✅ SHIPPED + VERIFIED** — v1.14.0, commit `8b8db5f` on main, tag→8b8db5f, release live (asset `treningsprogram-v1.14.0.apk` uploaded, draft=false). 612 unit tests pass, DB unchanged v16. Independently verified by coordinator (tag+main+release API).
- 8 items all confirmed: 01 copy-all-logs, 02 confirm-regen, 06 Lucide nav icons, 07 04:00 day boundary (DayBoundary.kt + Settings), 08 last-session polish, 09 +/− calc keypad, 10 auto-attribute workout, 11 injury sufficiency check (wizard step 4).
- Done in one orchestrator pass (no workers — shared item-7 seam + shared layouts made fan-out counterproductive). Surgical commit; pre-existing dirt untouched.
- **On-device UI unverified** (Waydroid dormant) — user eyeballs on update, esp. item-6 hand-authored Lucide vector paths in bottom-nav/toolbar (light+dark), the keypad (8/9), and dialogs (2/11).

### ② Live-test generation fixes → fix → ship
**Status: ✅ COMPLETE — VERIFIED, NOTHING SHIPPED** (17/20 API calls; HEAD unchanged at v1.14.0; tree clean; harness removed). Coordinator-verified (no v1.15.0 tag, no source touched).
- **PASS:** headline continuity+weights (anchors kept & progressed, weights from logged history, plateaued lifts held — user's #1 concern WORKS) · four-goal parity · injury no-op (empty=zero change; real injury changes selection + adds rehab) · 20-min sizing · hypertrophy@50 sizing · plan structure/quality · V1 peer-review does NOT false-reject.
- **PARTIAL:** low-volume goals (strength/endurance/weight-loss) @50 under-fill on attempt 1 (~34–40 vs 40 floor) but the retry ladder CONVERGES and saves (e.g. 38→44→48 on attempt 3) — self-heals, costs extra wait.
- 🔴 **CLEAR FAILURE (handed back, not fixed): 120-min targets produce NOTHING.** Model caps sessions ~55–60 min, added only ~5 min across 3 attempts, "add more" retry TRUNCATED → nothing saved. Reproduced on no-history controls (genuine, not a test artifact). NOT safely fixable overnight (needs a product decision + live human iteration; blind prompt change risks regressing the working headline fix + reopening truncation). See NEEDS ATTENTION.
- Test harness: JVM/Robolectric driving the REAL buildPrompt/parse/validate/gates (only HTTP transport simplified). Saved to memory as `reference_live_gen_harness`.

### ③ Simplify / dead-code cleanup → ship (v1.15.0)
**Status: ✅ SHIPPED + VERIFIED** — v1.15.0, commit `c65735d` on main, tag→c65735d, release live (asset uploaded, draft=false). 612 tests, DB v16, lint UnusedResources 21→0. Coordinator-verified.
- Removed 13 files / ~660 LOC provably-dead: 2 unused RecyclerView adapters + their 4 layouts, dead menu/2 drawables, 15 unused strings + 1 color, 2 dead private vals. Conservative — KEPT all Hilt/Room/Gson/reflection-wired code (proven near-misses). Zero behavior change (aapt/build self-verifies resource refs).
- Latent items flagged (NOT fixed, out of scope): (1) redundant `dbId != null` check in `ExerciseInfoBottomSheet.kt:83`; (2) cloud backup uses the DEPRECATED Google Sign-In API (`GoogleDriveAuth.kt`) — future migration candidate.

### ④ Exercise-DB matching improvements → ship (v1.16.0)  [user-added mid-run]
**Status: ✅ SHIPPED + VERIFIED** — v1.16.0, commit `51dceeb` on main, tag→51dceeb, release live (asset uploaded, draft=false). DB **bumped v16→v17** (data-only backfill `MIGRATION_16_17` re-derives stored muscleGroup, reps/weight untouched). 617 tests (612+5). Coordinator-verified.
- Root cause: `ExerciseDbResolver.resolve()` returned null for all 128 verbose names (fuzzy ties, fly/flye mismatch, qualifier dilution, whole families with no alias); classifier ran on raw name so parenthetical setup phrases ("on Bench", "Chest-Supported") hit the Chest catch-all first. Fix: pattern-level `familyResolve` net (after fuzzy, zero regression) + classifier reordered by specificity + ankle/foot-rehab bucket. **128/128 now resolve (100%).**
- New `R2VerboseExerciseRecognitionTest` (all 128, regression-proof) + `R2BackfillMigrationTest`. Decisions: pure ankle mobility → excluded from muscle-volume but resolver-matched (leaves the unrecognized list); loaded carries → Core; good morning/sumo → Legs. Residual: real-device migration-open path unverified (no aarch64 Robolectric SQLite; near-nil risk for data-only); a few family fallbacks are "closest sensible" (may show a nearby variant's image).

### ④ Exercise-DB matching improvements → ship (~v1.17.0)  [ADDED by user 2026-07-01, mid-run]
**Status: ⏳ BLOCKED on ③**
- Improve exercise recognition/matching so the ~130 currently-unrecognized exercises in `unrecognized-exercises.txt` (this folder) map correctly (muscle group + library/DB match). Diagnose-first: is it MuscleClassifier, the exercise-DB matcher, or both? Builds on [[project_exercise_recognition_fix]] (v1.10.2, pattern-level). Many are verbose names with parentheticals (strip them), rear-delt/chest-supported/flye-spelling variants, and a LARGE ankle/tibialis/calf REHAB+PREHAB cluster (dorsiflexion, ankle alphabet, towel toe scrunch, single-leg balance, calf-raise variants) — likely needs a rehab/ankle recognition category. Add a unit test asserting each listed name now resolves. If a data-backfill migration is used (re-derive stored muscleGroup like v1.10.2), bump DB version. One orchestrator pass, diagnose-first, no intake (well-specified). Ship as its own version.

---

## OPEN QUESTIONS / NEEDS ATTENTION
- **🔴 [②] HIGH-END DURATION PRODUCES NOTHING (top item).** Targets ≈100–130 min → the model caps sessions ~55–60 min and the retry can truncate → nothing saved. This is the "generates nothing" failure at the high end you said must work. NOT a blind prompt patch — it's a product decision + needs live human iteration (you have the key). Options the tester recommends: (a) cap the practical max session length lower; (b) widen the ±10 window at duration extremes; (c) instruct long-session STRUCTURE (extended warm-up + conditioning/carries + more accessories) rather than "add exercises". Note: 4-day × 120-min = 480 min/week is the far corner of the parameter space — worth checking if real users hit it before over-investing.
- **[②] Low-volume-goal mid-range (strength/endurance/weight-loss @~50 min) under-fills on attempt 1**, self-heals over the 3-attempt ladder (extra user wait). Minor; a carefully-verified nudge could cut round-trips but risks the same regressions.
- **[②] Mechanism note for any future fix:** model declares `dayEstimateMinutes` as the aspirational target, not a sum of its (accurate) per-exercise `estimatedMinutes` — partly because the OUTPUT section forbids per-day arithmetic (a v1.10.6 anti-truncation measure). The deterministic gate correctly ignores the declared value.
- **[①/item 6] Colored icon-pack upgrade — DEFERRED (optional).** Shipped Lucide monochrome for nav/functional icons; kept EMOJI for the colorful/gamification glyphs (sanctioned fallback). User "preferred" a nicer colored pack but it's a heavy, subjective, app-wide change — left as an optional dedicated pass. Not blocking. Needs user say-so.
- **[①/item 11] Live wizard AI round-trip unverified** — plumbing reuses the proven getOnboardingQuestions path; not live-tested (spend limits). User verifies on first wizard run with an injury (e.g. "bad knee").
- **[①] On-device UI unverified** across the batch (Waydroid dormant) — normal model; user does device check.

## DECISIONS & FINDINGS LOG
- 2026-07-01 04:33: Chain set up; UX orchestrator dispatched; API budget ledger initialized at 0/20.
- 2026-07-01 ~05:3x: ① SHIPPED v1.14.0 (commit 8b8db5f), coordinator-verified live. 612 tests. Item-6 colored-pack upgrade deferred (see open questions). Advancing to ②.
- 2026-07-01 ~06:2x: ② tester dispatched; stall-prone (ends turn on async watchers). Coordinator nudged it once to run live calls SYNCHRONOUSLY. Ledger at 3/20.
- 2026-07-01 ~06:2x PRELIMINARY (now superseded): attempt-1 gate rejections at 50 min — RESOLVED: those self-heal over the retry ladder; the real failure is at 120 min (nothing saves).
- 2026-07-01 ~06:45: ② COMPLETE. 17/20 calls. VERDICT: headline continuity+weights + four goals + injury + 20-min all PASS; 120-min = CLEAR failure (produces nothing) handed back (not safely fixable overnight); mid-range under-fill self-heals. Coordinator accepted verdict (aligns with safety policy + "note needs-attention, ship what's safe"). Nothing shipped. Advancing to ③.

## FINAL SUMMARY — overnight run COMPLETE (2026-07-01)

**Outcome: all 4 stages done. 3 new versions shipped + verified live (v1.14.0, v1.15.0, v1.16.0); 1 stage was live-verification only (v1.13.0 gen fixes, nothing shipped). 17/20 API calls used. Every ship independently coordinator-verified (tag + main + live release asset). No user action was required overnight; the chain never had to halt.**

### What was DONE (shipped)
1. **v1.14.0 — UX batch (8 items):** `04:00` configurable day boundary applied app-wide (new `DayBoundary.kt`); auto-attribute any off-day workout to today (silent + rebalance); +/− calculator keypad for manual weight entry; "copy all prompt logs" button; "are you sure?" on the settings regenerate; prettified last-session line; AI injury-sufficiency check in the setup wizard; Lucide monochrome nav/toolbar icons. 612 tests, DB v16.
2. **v1.15.0 — codebase simplification:** removed 13 dead files / ~660 LOC (2 unused adapters + their layouts, dead menu/drawables, 15 unused strings, dead vals). Max-conservative — kept all Hilt/Room/Gson-wired code. Zero behavior change. 612 tests, DB v16.
3. **v1.16.0 — exercise recognition:** the 128 verbose AI-generated names that the app failed to recognize now ALL resolve (100%), via a pattern-level resolver family-net + a specificity-ordered classifier + an ankle/foot-rehab bucket. Data-only backfill migration DB v16→v17. 617 tests.

### What was FIGURED OUT (key findings)
- **The v1.13.0 generation fixes were live-tested against the real API and the HEADLINE fix WORKS:** anchor lifts are kept & progressed week-to-week, weights are anchored to logged history (plateaued lifts correctly held), ≤2 swaps/week — the user's #1 complaint ("exercises/weights off") is genuinely fixed. Four-goal parity, injury no-op, 20-min & hypertrophy@50 sizing, and plan quality/structure all PASS. The V1 peer-review does not false-reject.
- **Root cause of the unrecognized exercises:** the DB resolver (not the muscle classifier) was returning null on verbose names; the classifier separately mis-bucketed them to "Chest" because it read the raw parenthetical name. Both fixed at the pattern level.
- **Dead code inventory:** the app carried ~660 LOC of provably-dead adapters/layouts/resources from its 14-release history; now removed.

### ⚠️ What NEEDS ATTENTION / was deliberately NOT done (see OPEN QUESTIONS above for detail)
1. **🔴 120-minute sessions produce NOTHING** (top priority). The generation model caps sessions at ~55–60 min; a 100–130 min target fails all attempts and the retry can truncate → nothing saved. This is a real failure at the high end you said must work, but it needs a **product decision + live human iteration** (you have the key), not a blind overnight prompt patch to the regression-prone reliability path — so it was handed back, not "fixed." Recommended directions: cap the practical max lower, widen ±10 at duration extremes, or instruct long-session STRUCTURE (warm-up + conditioning/carries + accessories). Check whether real users actually hit 4-day × 120-min before over-investing.
2. **Low-volume goals @~50 min under-fill on attempt 1** but self-heal over the retry ladder (extra wait). Minor.
3. **Item-6 colored icon-pack upgrade — DEFERRED (optional).** Nav icons are now Lucide monochrome; the celebratory/gamification glyphs stayed emoji (sanctioned fallback). You "preferred" a nicer colored pack — a dedicated pass whenever you want it.
4. **Latent items flagged during cleanup (not fixed, out of scope):** redundant `dbId != null` check (`ExerciseInfoBottomSheet.kt:83`); cloud backup uses the DEPRECATED Google Sign-In API (`GoogleDriveAuth.kt`) — future migration candidate.

### On-device checks for YOU (Waydroid dormant; unit tests can't cover these)
- First launch after updating: confirm the v17 migration opens cleanly (data-only, near-nil risk).
- Eyeball the new Lucide nav/toolbar icons (light + dark), the +/− calculator keypad, the regenerate confirm dialog, and the wizard injury follow-up.
- Do a real generation to feel the churn-reduction + history-anchored weights in your own program; and confirm your previously-unrecognized exercises now show up recognized.

### Budget & scope hygiene
17/20 live API calls used (3 reserve, unused). Every commit was surgical; all pre-existing working-tree dirt and this `overnight-run-2026-07/` folder were left untouched across all 4 stages.

