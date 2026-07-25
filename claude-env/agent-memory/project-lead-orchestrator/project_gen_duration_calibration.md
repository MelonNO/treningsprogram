---
name: gen-duration-calibration
description: Attempt-1 duration-landing calibration for AI gen — the model's self-estimate UNDER-bias vs the exact formula, measured per goal×duration; the aim-high buffer knob and what it can/can't close
metadata:
  type: project
---

Follow-up to [[long-session-fix]] (v1.17.0). Goal: raise the ATTEMPT-1 in-window landing rate so gen needs
fewer retries, WITHOUT loosening the strict ±10 gate or adding under-salvage. Change is DURATION-sizing only.

**The knob (all in `AiRepository.kt`):** `DURATION_AIM_BUFFER_MIN` (+ `durationAimMinutes(target)=target+buffer`
+ shared `durationAimPhrase(target)`). Every prompt seam that stated the per-day aim was flipped from "aim
CENTRE" to "SIZE each day to ~target+buffer; the app RE-COUNTS LOWER than your sizing, so err HIGH — OVER is
auto-trimmed (safe), UNDER is fatal." Applied in: buildPrompt TIME BUDGET (new "CALIBRATION —" line) + UNDER/
OVER/BUILD-RULES lines; the long-session block step-3 (+ worked example) — replaced the old `target+5`;
`dayDurationFeedback` long+short under branches; `buildSingleDayPrompt`. Gate/window/WorkoutTimeEstimator/
salvage/ladder all byte-for-byte UNCHANGED. Unit-tested in `DurationCalibrationTest`.

**LIVE-MEASURED (JVM harness rendering REAL buildPrompt via Unsafe+reflection, plain live POST, REAL gate;
harness DELETED after) at buffer=12, aim=target+12, Intermediate/4-day/no-history:**
- Strength@50: attempt-1 ALL-IN (42,45,40,46). CLOSED (old baseline ~34-40 under-floor).
- Hypertrophy@50: 3/4 (42,40,37,42). Day5 37 (1 under). Hypertrophy@120: ALL-IN (123,118,121,119) — SAVES.
- Endurance@50: 2/4 (39,35,51,46). Weight-Loss@50: 1/4 (34,37,39,45). Both improved vs baseline, NOT closed.
- Hypertrophy@100: 0/4 (78,68,68,60) — model UNDER-cardios the finisher on the FIRST pass.

**KEY CORRECTIONS to the prior mental model (were wrong):**
1. The 50-min self-estimate under-bias is **uniformly LARGE (~16-25 min) across ALL FOUR goals**, NOT
   goal-dependent. Hypertrophy is NOT low-bias (landed 37-42, nowhere near the 60 ceiling). The prior "120 was
   low-bias" was **cardio DILUTION** (cardio is estimated accurately by both model and formula), not goal type.
2. Buffer **pass-through is PARTIAL (~40-50%)** — moving the stated aim +12 moved real landings only ~+3-6. So
   NO single additive buffer closes the tightest short-rest days on attempt-1 (to add landing +5 needs buffer
   +10). The feasible "all-cells-in-window" band is EMPTY once bias ranges 3→28.
3. **Rest-band width gates how much the buffer helps.** Strength (long rest, up to ~300 s → big rest time-lever)
   CLOSES on attempt-1. Endurance/Weight-Loss (short rest 45-90 s → tiny rest headroom) can't reach target with
   pure strength and under-use their prescribed cardio → attempt-1 under-bias a uniform buffer can't close. The
   real closer for those would be steering their goal-cardio (out of scope here; would touch four-goal parity).
4. Long sessions (≥90): 120 closes attempt-1 (multi-modal cardio); 100 under-cardios attempt-1 → relies on the
   retry ladder (v1.17.0 reached 88-89 at the old +5 aim; the +12 feedback aims higher).

**100-min RETRY LADDER (9/20 calls total; a2/a3 fed real dayDurationFeedback back):** a1 [74,75,64,68] under
→ a2 [112,105,102,100] (day1 +2 over, others in, NONE under = clean OVER-ONLY miss) → a3 [60,54,52,49]
(model OVER-CORRECTED the "trim day1" feedback and nuked the finisher). **The win:** a2 is exactly the
`isOverOnlyDurationMiss` salvage-candidate shape → production `trimOverflowToWindow` trims day1 112→≤110 and
SAVES in-window. So the buffered feedback flipped 100-min from v1.17.0's **88-89 UNDER-floor (unsalvageable →
saved NOTHING)** to the **salvageable OVER side (saves)** — the real fix. (Caveat noted: long-session TRIM
feedback can make the model over-trim/collapse the finisher; harmless because salvage keeps the BEST over-only
candidate, not the last attempt.) The JVM ladder harness only checks the model's clean-plan allIn (not the
deterministic trim-salvage), so its saved=false UNDERSTATES production, which saves via a2+trim.

**NET:** clear improvement, no regression (every cell ≥ old baseline; 120 saves; 100 now SAVES via salvage).
Buffer 12 KEPT (verified value). Higher buffer = diminishing
returns (partial pass-through) + starts tipping long cells over the ceiling (non-fatal/trim but extra retry).
See [[long-session-fix]] [[generation-quality-overhaul]] [[reference_live_gen_harness]] [[feedback_frugal_api_testing]].
