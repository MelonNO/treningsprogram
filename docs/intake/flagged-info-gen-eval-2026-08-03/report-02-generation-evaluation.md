# Generation Pipeline Health-Check — Findings & Proposals (2026-08-03)

Deliverable for **brief 02** (`brief-02-generation-evaluation-report.md`). Report ONLY — nothing in here has been implemented; every proposal awaits your approval and would become a future intake/build item.

**Method & constraints honored:**
- Code-, prompt-, and history-review only. **Zero live Anthropic API calls were made.** Proposals that would ideally want a live run to validate are explicitly marked *"would need a live run (your decision, has cost)"* — never assumed.
- **Zero application code changed** under this item.
- Adaptive-thinking is NOT proposed anywhere (previously tried and rejected).
- Evidence base: `data/repository/AiRepository.kt` (2,416 lines) plus `WorkoutTimeEstimator.kt`, `GenerationRunner.kt`, `MuscleClassifier.kt`, `NetworkModule.kt`, `ClaudeRequest.kt`, `WorkoutRepository.kt`, `StatsRecomputer.kt`, `SettingsTrainingFragment.kt`, and the historical rationale docs under `docs/intake/` (`generation-*`, `long-session-fix-2026-07`, `gen-calibration-2026-07`, `generation-quality-overhaul-2026-07`). Deliberate past trade-offs (strict duration gate, no-retry-on-generation-timeout, JSON-first prompt, REST-first trim, aim buffer +12, neutered validator #13, multi-modal ≥90 min, anti-churn continuity) are respected — proposals engage with those reasons rather than undoing them.
- Findings were produced by a dedicated read-only evaluation pass; the headline factual claims were then **independently re-verified line-by-line against the code** by the project lead before this report was written.

Each finding gives: (a) the finding with file/line evidence, (b) a concrete proposal, (c) expected benefit, (d) risk/effort (S/M/L).

---

## Axis 1 — Plan-quality levers (prompt rules that fight, duplicate, or dead-letter)

### 1.1 The CARDIO naming section contradicts the long-session modality list
(a) `AiRepository.kt:1930–1933`: *"For cardio exercises, use these name conventions so the app can identify them: Easy Jog, Outdoor Run, Tempo Run, Interval Run."* — running only. The LONG SESSION block (`:1679`) allows *"stationary bike / cycling, incline treadmill walk … jump rope, HIIT"*, and the injury blocks (`:1767`, `:1772`, `:1777`, `:2169`) steer lower-limb-injury users to *"low-impact cardio (bike, **row**, incline walk) over jogging"* — but `MuscleClassifier` maps rowing to Back, so it is **not duration-timed**, and the long-session block itself forbids rowing as the timed entry (`:1681`). The injury text recommends a modality the estimator cannot time; the base CARDIO section gives no bike/walk naming guidance even when the injury text forbids running. For short/mid endurance and weight-loss plans (goal blocks require 2–3 cardio sessions) this is a real path to silently un-timed cardio entries.
(b) **Proposal:** one shared "timeable cardio modalities" constant (names + JSON shape) referenced by the CARDIO section, the long-session block, and the injury blocks; drop "row" from the injury cardio suggestions.
(c) **Benefit:** removes a silent under-count path for injured endurance/weight-loss users; fewer contradictory instructions.
(d) **Risk/effort:** S code; prompt behavior change — would need a live run to confirm no regression (your decision, has cost).

### 1.2 The "err HIGH / app recounts lower" calibration instruction is stated five times
(a) Appears in `durationAimPhrase()` (`:213–223`, injected at `:1879`), the UNDER-FILL section (`:1884`), OVER-FILL (`:1891`), BUILD RULES (`:1943`), the long-session block (`:1675`), and the retry feedback (`:504–546`). The loaded-hinge rep caps likewise appear in full three times (`:1743–1747`, `:1812`, `:1944`) plus the validator (`:1419`) and the single-day prompt (`:2159`).
(b) **Proposal:** one authoritative statement per rule plus at most one short cross-reference; the shared-phrase pattern (`durationAimPhrase`) already exists — extend it to the hinge caps and calibration text.
(c) **Benefit:** several hundred input tokens per attempt × up to 3 attempts; a smaller prompt is faster to first-JSON, directly relieving the 360 s deadline.
(d) **Risk/effort:** M. The repetition may be *why* compliance improved across fix rounds — this is exactly the kind of change that **would need a live run** to validate (your decision, has cost). Do not do it blind.

### 1.3 Weight-increment rules disagree between weekly and single-day prompts
(a) Weekly (`:1925`): *"barbell-upper +1.25–2.5 kg / barbell-lower +2.5–5 kg / dumbbell +1–2 kg each (beginner may use the top of each range)"*. Single-day (`:2152`): *"add 2.5 kg (intermediate/advanced) or 5 kg (beginner) to the last weight"* — a flat rule that tells a beginner to add +5 kg to *any* lift, including a dumbbell curl the weekly rule caps at +1–2 kg.
(b) **Proposal:** reuse the weekly increment text in `buildSingleDayPrompt`.
(c) **Benefit:** consistent, safer progression on the regen path; removes an easy source of "weird weight jump" complaints.
(d) **Risk/effort:** S; low risk (aligns to already-validated weekly wording).

### 1.4 Single-day regen quietly bypasses the P1 anti-churn continuity contract
(a) Weekly prompt: *"at most TWO fully-swapped main lifts this week"* (`:1713`, `:1948`). The single-day prompt says the opposite: *"Do NOT just re-use the exercises this day already had or recent sessions used — pick DIFFERENT movements"* (`:2195`), blacklisting recent-session names — repeated regens force churn of that day's anchors, whose replacements then rely on the weaker "closely-related lift" weight estimation. Partly deliberate (P4 made regen a variety feature), but the two paths embody opposite philosophies with no cap on anchor swaps in the regen path.
(b) **Proposal:** carry the variation-hierarchy language (keep+progress anchors; vary grip/angle/accessories; swaps rare) into the single-day prompt, scoped so a user-initiated "give me variety" regen still varies accessories freely.
(c) **Benefit:** protects progression tracking (the stated rationale of v1.13.0/P1) on the path most likely to be used repeatedly.
(d) **Risk/effort:** S–M; product nuance — worth your quick confirmation since P4 deliberately chose variety here.

### 1.5 The prompt hard-codes "4 s" instead of interpolating `WORK_SECONDS_PER_REP`
(a) `WorkoutTimeEstimator.kt:17–25` declares itself the single source of truth and says the prompt *must quote the same number* — but the prompts hard-code the literal "4 s" (`AiRepository.kt:1873`, `:2138`). A future calibration change to the constant would silently desynchronize the stated formula from the enforced one — the exact bug class P2 existed to fix.
(b) **Proposal:** interpolate `${WorkoutTimeEstimator.WORK_SECONDS_PER_REP}` in both prompts.
(c) **Benefit:** eliminates a latent drift trap; byte-identical output today.
(d) **Risk/effort:** S; effectively zero risk.

### 1.6 OVER-FILL paragraph gives two targets in one breath
(a) `:1891`: *"TRIM — remove an accessory … until it estimates close to $target … Err HIGH toward ~${target+12} min"* — an over-ceiling day is told simultaneously to trim toward `target` and to aim at `target+12` (2 min *above the ceiling*). The live ladder log shows the over-correction this invites: `docs/intake/gen-calibration-2026-07/live-run-log.txt` — a2 `[112,105,102,100]` → a3 collapsed to `[60,54,52,49]`.
(b) **Proposal:** make the OVER branch's target unambiguous ("trim to between $target and $high; do not go below $target"); see 4.4 for the oscillation itself.
(c) **Benefit:** fewer wasted attempt-3 over-corrections (a measured failure mode).
(d) **Risk/effort:** S; prompt change — flag for a live run (your decision, has cost).

---

## Axis 2 — Reliability

### 2.1 Deadline expiry silently discards a viable salvage candidate (highest-value reliability finding)
(a) `withGenerationDeadline` (`:234–241`) converts the 360 s timeout into a thrown `IllegalStateException` that cancels the whole block — `finalizeOrSalvage` (`:1103`, called at `:1223`/`:1321` *inside* the deadline) never runs. If attempt 2 produced an OVER-only candidate (the exact pattern the calibration run proved for 100-min targets) and attempt 3 hits the wall-clock deadline mid-call, the user gets "Generation took too long" and **nothing is saved**, even though a deterministically-trimmable plan is in memory. The G2 brief documents exactly this shape historically. At long targets (slow attempts, 24576-token budget) this is plausibly a contributor to the 120-min hard failures.
(b) **Proposal:** wrap the attempt loop (not the whole flow) in the deadline; on timeout, if `salvageCandidate != null`, run `trimOverflowToWindow` (pure, no network) and either (i) attempt one bounded `validateProgram` call outside the deadline (it has its own 300 s callTimeout), or (ii) save without LLM review with a rationale note — (i) vs (ii) is your product decision.
(c) **Benefit:** converts a class of total failures into saves; no change to gate/window/thresholds; still terminal (honors the H1 "never hang" guarantee).
(d) **Risk/effort:** M; the review-on-timeout variant extends worst-case wall clock by one call — needs explicit sign-off since G2 froze the deadline. Ideally confirmed with one live long-target run (your decision, has cost).

### 2.2 `validateProgram` fails open and logs nothing on failure
(a) `:1456–1458`: `.getOrElse { ValidationResult(accepted = true) }` — a network error, 401, or unparseable review response silently accepts the plan. The FIX-C "log even on failure" pattern was not applied here: a thrown review call leaves no `promptLog` entry (`promptLog.add("validate", …)` at `:1450` is only reached on success).
(b) **Proposal:** keep fail-open (defensible — don't block a gate-passing plan on review infra), but log the failure and record "review skipped (network)" in the result/rationale so you can see the plan was unreviewed.
(c) **Benefit:** observability; today a flaky network makes the quality layer vanish invisibly.
(d) **Risk/effort:** S; no change to acceptance behavior.

### 2.3 Stale timeout arithmetic in safety-critical comments
(a) `AiRepository.kt:56–57`, `:114–115`, `:128` all reason from "OkHttp callTimeout (240s)", but `NetworkModule.kt:40` is now `callTimeout(300 s)` (raise acknowledged at `NetworkModule.kt:37`). The `GENERATION_OVERALL_DEADLINE_MS` justification is built on numbers that no longer hold: one stalled call burns 300 of the 360 s budget, leaving 60 s — not enough for a second attempt plus review.
(b) **Proposal:** update the comments and re-derive the 360 s number explicitly from 300 s (or define the deadline as `callTimeout + margin` from one shared constant).
(c) **Benefit:** the next person tuning the three coupled timeouts (read 180 / call 300 / deadline 360) sees true arithmetic.
(d) **Risk/effort:** S; comment-only unless the deadline value is revisited (interacts with 2.1 and Axis 7).

### 2.4 A transient network blip mid-ladder aborts the whole generation
(a) The `try/catch` around the generate call (`:1163–1183`) rethrows after `withAiRetry` exhausts — an `IOException` on attempt 2 discards attempts 1–2's progress *and any salvage candidate*. Retry policy is per-call, not per-attempt.
(b) **Proposal:** on a transient failure of attempt N (N < max), record it as a rejected attempt ("network error — retrying") and continue the ladder; keep fail-fast for `SocketTimeoutException` (H1's deliberate choice) and for the final attempt.
(c) **Benefit:** the ladder survives one blip; salvage candidate preserved.
(d) **Risk/effort:** M; interacts with the deadline budget — keep H1 semantics intact.

### 2.5 `withAiRetry` retries 429 immediately with no backoff
(a) `:93–109` — immediate re-issue; a 429 will almost certainly 429 again. Given the household history of drained API spend limits, a small delay is cheap insurance.
(b) **Proposal:** a fixed 2–5 s delay before retrying specifically on `HttpException 429` (bounded, inside the deadline).
(c) **Benefit:** the one retry actually has a chance of succeeding.
(d) **Risk/effort:** S.

### 2.6 Terminal failure messages can be enormous
(a) The thrown message concatenates every attempt's full duration feedback (`:1137–1138`); a long-session UNDER paragraph is ~1,400 characters *per day per attempt* (`:504–524`), so a 4-day × 3-attempt failure can push a ~15,000-character string into the UI error path.
(b) **Proposal:** keep full text in `rejectionLog`/`promptLog`; surface a one-line summary per attempt to the user.
(c) **Benefit:** readable failure UX.
(d) **Risk/effort:** S.

### 2.7 Latent estimator hazard: rep-style cardio falls back to 30 minutes
(a) `WorkoutTimeEstimator.parseCardioSeconds` (`WorkoutTimeEstimator.kt:52–58`) returns **1800 s** when `targetReps` has no min/km — intended for "6×400m", but it also fires when a Cardio-classified exercise carries rep-style reps. `MuscleClassifier` maps "burpee", "mountain climber", "high knee", "jump rope" to Cardio (`MuscleClassifier.kt:85–88`); a model emitting "Mountain Climbers 3×20" (common in weight-loss circuits) is counted as ~31 min, corrupting the day estimate in either direction (false OVER rejection, or masking real underfill). The 2026-07-01 live log shows the historical cousin of this bug class ("Cable Crunch=15-20" counted as cardio, day inflated to 62 min).
(b) **Proposal:** in `parseCardioSeconds`, if `targetReps` matches a pure rep/rep-range shape (`\d+(-\d+)?` with no unit), fall back to the strength formula (or a small constant), keeping 1800 only for genuinely unparseable distance/interval strings.
(c) **Benefit:** closes a silent gate-corruption path without touching `MuscleClassifier` (whose changes ripple into stats and need a v1.10.2-style backfill).
(d) **Risk/effort:** S–M. **Cross-system flag:** changes the Program-screen "~Xm" label for affected entries (the estimator is shared) — acceptable, but noted; no DB/backup impact.

---

## Axis 3 — Speed / cost

### 3.1 P5 auditable metadata is the dominant output-token cost and is never consumed
(a) The output contract (`:1959–1997`) demands 10 extra fields per exercise plus per-day and per-week metadata. `parseProgram` (`:2342–2375`) maps only the six core fields; `ExJson`'s P5 fields (`:734–743`) are parsed and discarded; the validator never reads declared numbers (the V1 "declared-number checks" never materialized — the deterministic gate stays authoritative, `:1435`). Metadata roughly doubles-to-triples per-exercise JSON on the output side, and output length drives per-attempt latency — the same latency squeezed by the 360 s deadline, and the reason `GENERATION_MAX_TOKENS` was raised to 24576 (`:138–154`).
(b) **Proposal (two-tier, per the P5 brief's own escape hatch):** `docs/intake/generation-quality-overhaul-2026-07/brief-P5-auditable-metadata.md` explicitly pre-authorizes reducing to *"the time components — work/rest/setup/estimated minutes — plus day estimate + within-window + block state"* if size causes trouble. Cut to that subset. **Caution:** the per-exercise `estimatedMinutes` arithmetic may function as beneficial structured self-checking — the calibration A/B that chose buffer +12 was measured *with* full metadata, so removing it could shift the under-bias.
(c) **Benefit:** est. 40–60% output-token cut on the largest call, faster attempts, less truncation risk, less deadline pressure.
(d) **Risk/effort:** S code / **would need a live run** to confirm duration accuracy doesn't regress (your decision, has cost). Alternative that avoids the gamble: keep the schema but *consume* it (see 4.2), so the cost buys something.

### 3.2 No prompt caching, and the retry-varying block sits mid-prompt
(a) Each of up to 3 attempts resends the full prompt at full price. The only part that changes between attempts is `rejectionBlock`, assembled *mid-prompt* (`:1784`), before the history, profile, time-budget, and output-schema sections — so even with Anthropic prompt caching enabled, attempts 2–3 would share only a small header prefix. The minimum cacheable prefix (1024 tokens) and cache TTL (5 min) comfortably fit the ≤360 s ladder; cache reads bill ~0.1×.
(b) **Proposal:** (i) move `rejectionBlock` to the end of the prompt; (ii) add `cache_control` support to `ClaudeRequest` (content-block message shape) and mark the stable prefix (`variationTheme`/history are chosen once per generation, so the prefix is stable across the ladder).
(c) **Benefit:** attempts 2–3 read ~90% of input at ~0.1×; likely a small latency win too.
(d) **Risk/effort:** M (request-model change + prompt reorder). Moving the rejection block from top to bottom is itself a prompt change — **flag for a live run** (your decision, has cost).

### 3.3 The weekly review call sends the raw model JSON (with all P5 metadata) as input
(a) `:1304` passes `cleanJson` to `validateProgram`, so the review pays input tokens for metadata the validator is told not to use. The salvage and single-day paths already re-serialize lean via `buildProgramJsonForValidation` (`:2387–2408`).
(b) **Proposal:** pass `buildProgramJsonForValidation(exercises, rationale)` in the weekly accept path too — this simultaneously fixes finding 4.1.
(c) **Benefit:** smaller review input; reviewed plan == saved plan.
(d) **Risk/effort:** S; the validator loses sight of day names/metadata (cosmetic; it never gated on them).

### 3.4 Attempt 3 is burned even when attempt 2 already produced a trimmable plan
(a) Salvage defers trimming until all attempts fail (`:1098–1102`, "the model's own clean plan is still preferred"). The calibration ladder measured that at 100-min targets a2 is a clean OVER-only candidate and **a3 over-corrects and is wasted** (`live-run-log.txt`: a2 `[112,105,102,100]` → a3 `[60,54,52,49]`).
(b) **Proposal (product decision):** when an attempt is OVER-only and `trimOverflowToWindow` succeeds, trim + review immediately; continue the ladder only if the trimmed plan fails review. Preserves the gate byte-for-byte.
(c) **Benefit:** saves one full generation call (~30–40% of ladder latency/cost) in the *measured common path* for long targets; reduces deadline exposure.
(d) **Risk/effort:** M; deliberately reverses the "prefer the model's own clean plan" ordering — needs your sign-off and ideally one live confirmation (your decision, has cost).

### 3.5 History block size
`buildSessionHistory` (`:1461–1539`) renders 12 sessions set-by-set plus a trends summary over the same data. The set-by-set detail is the anchor for P1 weight-anchoring, so cutting it blind is NOT recommended; if input cost becomes a concern, the candidate is capping per-session lines (e.g. top-set-per-exercise beyond the most recent 6 sessions) — prompt-effect flagged, live run required (your decision, has cost).

---

## Axis 4 — Prompt & validator design

### 4.1 Reviewed plan ≠ saved plan on the weekly path (gym exclusions)
(a) Item 02's comment (`:1199–1201`) claims "the plan the gates approve is exactly the plan that gets saved" — true for the deterministic gates (run on the stripped `exercises`, `:1202–1206`) but **false for the LLM review**, which receives the un-stripped `cleanJson` (`:1304`). If the model included an excluded exercise, the reviewer approves a plan containing it while a different plan is saved. The single-day path does this correctly (`:2318`).
(b) **Proposal:** same fix as 3.3 — review the lean re-serialization of the stripped plan.
(c) **Benefit:** restores the stated invariant; the review verdict applies to what ships.
(d) **Risk/effort:** S.

### 4.2 Validator checks that could/should be deterministic
(a) Validator #2 (*"exactly $daysPerWeek training days"*, `:1420`) is a trivially deterministic check that today costs an LLM round-trip and yields nondeterministic feedback. #11 (exercise-count caps, `:1433`) is deterministic *if* roles are known — and the P5 `role` field the app already requests (and currently discards, finding 3.1) is exactly the needed input, giving the metadata a real consumer. Note #2's interaction with B08 rest-day mode: the prompt demands `training.size` days (`:2024`) while the validator is told `daysPerWeek` — callers should be verified to keep these equal.
(b) **Proposal:** add Kotlin gates for day-count (and optionally the strength-slot cap using declared roles with a name-based fallback), keeping the LLM review for genuine judgment items (#3–#5, #7–#10, #12).
(c) **Benefit:** cheaper, deterministic rejections with precise feedback; shrinks the review's job.
(d) **Risk/effort:** M; a new deterministic gate changes the accept path — add tests mirroring the existing gate tests.

### 4.3 Validator #13 (neutered time-budget) — correctly kept
The item's premise (*"the deterministic check … has PASSED before this review runs"*, `:1435`) holds on every current call site, including the salvage path (which re-checks the trimmed plan's windows inside `trimOverflowToWindow` before review). **No change proposed; do not delete the item** — it exists to suppress a measured over-rejection mode.

### 4.4 Retry feedback carries only the last rejection → measured oscillation
(a) `:1150` (`rejectionReasons.lastOrNull()`) and `:2260`. The model never sees "attempt 1 was 20 min under, attempt 2 was 5 min over," so it over-corrects (calibration ladder a3, quoted in 3.4).
(b) **Proposal:** feed a compact history line per prior attempt ("A1: days 74–75 min, UNDER; A2: 100–112, slightly OVER — the correct size is between these") alongside the current feedback.
(c) **Benefit:** damps oscillation; raises the chance attempt 3 is useful.
(d) **Risk/effort:** S code; prompt change — **flag for a live run** (your decision, has cost).

### 4.5 Per-day feedback duplication
Multiple under-floor long days each emit the full ~1,400-char paragraph, joined with spaces (`:1251–1264`). Aggregate to one paragraph listing the days and per-day minute deficits. S; prompt-effect flag.

### 4.6 Settings allow 15–180 min but the prompt promises 20–120
(a) `SettingsTrainingFragment.kt:327` clamps to `coerceIn(15, 180)`; the TIME BUDGET section says *"across the FULL range the user might pick (20–120 min)"* (`:1872`). 121–180 is uncharted territory where 120 already hard-fails; 15–19 is below the documented range.
(b) **Proposal:** clamp the setting to the supported range (decision-dependent on Axis 7; at minimum 20..120).
(c) **Benefit:** users cannot select a target the pipeline is known to fail at.
(d) **Risk/effort:** S; UI-only, no DB/backup impact.

---

## Axis 5 — Maintainability

**Finding:** `AiRepository.kt` is 2,416 lines mixing seven concerns: retry/timeout policy (`:31–241`), SSE parsing (`:243–308`), JSON extraction/repair (`:310–465`), duration policy + trim salvage (`:467–682`), three prompt builders (`:1567–2006`, `:2127–2217`, `:1388–1441`), two near-duplicate attempt-ladder loops (`:1141–1330`, `:2257–2340`), and misc AI features (onboarding/injury/summary). The saving grace — and the pattern to preserve — is that almost every policy is a package-level pure function with dedicated JVM tests (the F3/S3/B10/G1 seam pattern).

**Proposal (mechanical, low-risk first):** split into same-package files with zero signature changes — `GenerationTransport.kt` (retry classifiers, deadline, SSE parse/consume), `GenerationJson.kt` (fence/brace/comma/truncation helpers + Gson models), `DurationPolicy.kt` (aim buffer, `durationAimPhrase`, `dayDurationFeedback`, `trimOverflowToWindow`), `GenerationPrompts.kt` (the three prompt builders), leaving `AiRepository.kt` as the orchestration loops. Existing tests import by package, so the split is compile-checked and test-neutral. **Second tier (higher risk, defer):** extracting a shared "attempt ladder" runner from the weekly/single-day duplication — real duplication (salvage capture, finalize, parse-rejection handling appear twice) but the loops differ subtly (locked days, full-week review context); do this only with the suite green and never concurrently with prompt changes. **Risk/effort:** file split S–M / ladder extraction L. Do any decomposition on a clean tree.

---

## Axis 6 — Cross-system invariants constraining change

- **StatsRecomputer merge parity** (`data/backup/StatsRecomputer.kt`): replays sessions/sets/planned rows with the live app's formulas. No proposal above alters logged data or XP formulas; salvage/extension edits flow through `savePlan` like any plan, so parity is unaffected.
- **Backup versioning:** now at v8. **Flag:** any proposal that *persists* P5 metadata on `PlannedExercise` would mean DB v20→v21 + a backup bump + merger changes — 4.2 deliberately proposes consuming metadata in-memory only to avoid this.
- **DB schema:** no proposal above touches Room entities.
- **MuscleClassifier:** shared by stats, the estimator, and the trim-salvage coverage guard; changing it requires a v1.10.2-style data-only backfill. Proposal 2.7 was shaped to avoid touching it.
- **WorkoutTimeEstimator ↔ prompt coupling:** the constant/prose sync trap (1.5) is the invariant to fix, not to work around.
- **savePlan paths** (`WorkoutRepository.kt:565`, `:586`): plan-table-only with `backupScheduler.requestBackup()` — any new save path added by the Axis 7 options must route through these, never a new DAO write.

---

## Axis 7 — The 120-minute ceiling: options (product decision — your call)

**Evidence recap.** `long-session-fix-2026-07/live-run-log.txt`: strength@120 failed all 3 attempts with days at **106–114 min vs a [110,130] window** — the misses are small (2–4 min) *under-floor* days that already contain a 42–47-min bike finisher. `gen-calibration-2026-07`: with buffer +12, hypertrophy@120 passed all-in on attempt 1; 100-min reaches an OVER-only attempt-2 landing that trim-salvage saves; buffer pass-through is only ~40–50% and larger buffers gave diminishing returns. The 360 s deadline (finding 2.1) compounds failures at long targets. (Adaptive-thinking is NOT an option — previously live-proven a regression and rejected.)

### Option A — Deterministic finisher-extension salvage (long sessions only). [Recommended]
Mirror of the already-accepted `trimOverflowToWindow`: after all attempts, for a non-locked day UNDER the floor **that contains a cardio-classified, duration-timed entry**, deterministically extend that entry's duration ("44 min" → "48 min") until the day re-estimates ≥ floor (cap at the aim). Only for `isLongSession` targets; never adds strength volume, so "under-fill stays the model's job" survives for short/mid sessions; layered strictly after the gate like the trim.
- *Pros:* directly fixes the measured 2–6-min under-floor misses; pure and unit-testable offline against the logged failure data; no extra API calls; fewer retries → less deadline pressure; symmetric to a salvage mechanism already accepted.
- *Cons:* extends the "we never auto-add" doctrine to conditioning duration (a documented stance reversal — your sign-off needed); a day without any timed cardio entry stays unsalvageable (arguably correct: the model ignored the multi-modal instruction).
- *Effort:* M. *Risk:* Low–Med. A confirming 120-min live run **would need a live run (your decision, has cost)**, but the mechanism itself is provable offline.

### Option B — Larger aim buffer for long targets only (prompt-only)
Raise the aim for ≥90-min targets from +12 to ~+18 given the ~40–50% pass-through; over-ceiling landings are now *safe* because trim-salvage exists — the original "tips long cells over the ceiling" objection was measured before OVER became salvageable.
- *Pros:* one-constant change; exploits the deliberate asymmetry (OVER salvageable, UNDER fatal).
- *Cons:* more salvage churn (extra review calls, attempt burn); pass-through variance may still leave a day 1–2 min under.
- *Effort:* S. *Risk:* Med. **Requires a live A/B (your decision, has cost).** Combines well with A.

### Option C — App-owned finisher sizing (two-phase, deterministic)
For long targets, prompt for the *strength block only* (~60 min, inside the model's demonstrated competence) plus a modality preference; the app computes `finisher = target − estimatedStrengthMinutes` and appends the timed entry itself (bike default; low-impact enforced for lower-limb injuries).
- *Pros:* eliminates model self-estimation from the long path by construction; single API call; fastest and cheapest at long targets.
- *Cons:* biggest change; plan authorship shifts (finisher becomes formulaic); validator context and day metadata become partially app-authored.
- *Effort:* L. *Risk:* Med. Live validation of the strength-only prompt shape needed (your decision, has cost).

### Option D — Cap the setting
Clamp session duration to ≤100 (where the salvage path is proven) or ≤90. Honest, zero API risk, S effort — but it removes the promise rather than delivering it. Regardless of the choice, fix the 15–180 clamp (finding 4.6).

*(Widening the window to ±15 for ≥90-min targets only would also pass the logged failures, but it relaxes the strict gate defended in every prior round — listed for completeness, not proposed.)*

**Recommendation (your call):** **Option A + the clamp half of D**, optionally with B as a cheap complement if you fund one live A/B. A targets the exact measured failure mode with a mechanism class already accepted, needs no prompt-regression gamble, and reduces reliance on the retry ladder the 360 s deadline squeezes — and pairing it with finding 2.1 (salvage survives deadline expiry) covers the two ways a good long plan currently dies.

---

## Priority shortlist (if only a few things get done)

1. **2.1** — salvage candidate survives deadline expiry (converts total failures into saves; M).
2. **4.1 / 3.3** — review the stripped, lean plan on the weekly path (correctness + cost; S).
3. **Axis 7 Option A + duration clamp** — the 120-min decision (yours; M).
4. **2.3 + 1.5** — fix stale 240 s comments and interpolate `WORK_SECONDS_PER_REP` (drift traps; S, zero risk).
5. **2.7** — rep-style cardio 1800 s fallback (silent gate corruption; S–M).
6. **3.1** — P5 metadata subset cut *or* start consuming roles deterministically (largest cost lever; needs one live run — your decision, has cost).
