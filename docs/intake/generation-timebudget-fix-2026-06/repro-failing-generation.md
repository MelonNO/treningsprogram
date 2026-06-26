# G1 — Canonical failing-generation repro (verbatim)

This is the user's captured failing case for brief **G1**. Preserve verbatim. It is the canonical regression fixture: a **complete, well-formed, rule-compliant 5-day plan** that the app **discarded, saving nothing**.

- **Profile:** 5-day program, rest days Mon + Thu (trains Tue/Wed/Fri/Sat/Sun = dayOfWeek 2,3,5,6,7), 50-min session target, Hypertrophy, Intermediate, priority Arms.
- **Constraints in play:** mild weak-ankle injury (rehab), equipment (pull-up bar / bench / barbell / dumbbells / ab roller), ~30-exercise blacklist, machine/cable-focus variation directive (substituted to free weights), low-ceiling/no-rack forbidden list.
- **Outcome observed:** plan trains exactly the required days → rest-day check passes; it is rejected by the deterministic ±10-min per-day time-budget gate; the LLM review step is never reached ("validation never happened"); after the attempt limit it throws and nothing is saved ("plan not used").

> **Relay note (from the coordinator):** the prompt below is the real in-app prompt; the **middle sections were condensed in the relay for length**, but the orchestrator can regenerate the exact prompt from `AiRepository.buildPrompt` with these inputs. The **section list and the TIME BUDGET wording are the load-bearing parts**. The **model's response is the user's verbatim paste, unabridged.**

---

## THE PROMPT (verbatim as relayed; middle sections condensed by the coordinator)

```
You are an expert strength & conditioning coach. Design a 5-day weekly training program tailored to the user below.

CRITICAL: Do NOT follow gym-culture day conventions (e.g., chest on Monday, back on Tuesday, arms on Friday). Assign muscle groups to days based purely on recovery logic. A good coach rotates exercises every single week — the same muscle can be trained with completely different exercises each week.

[HARD SAFETY RULES, INJURY HARD-CONSTRAINTS (Mild — weak ankle), FIXED WEEKLY SCHEDULE (TRAINING days Tue,Wed,Fri,Sat,Sun = dayOfWeek 2,3,5,6,7; REST Mon,Thu), EXERCISE BLACKLIST (~30 exercises from recent/last-week), VARIATION DIRECTIVE (machine & cable focus), WORKOUT HISTORY (3 recent sessions), USER PROFILE (Goal Hypertrophy, Intermediate, 5 days/week, 50-min session target, equipment: pull-up bar/bench/barbell/dumbbells/ab roller, weak-ankle injury, priority Arms), FORBIDDEN EXERCISES (no rack/cables/machines/bands; low ceiling — no standing OH barbell press), GOAL→REP RANGE table, WEEKLY STRUCTURE rules, TIME BUDGET (50 min ±10 PER training day, accept 40–60 min, with the sets×(reps×3s+rest)+60s estimate formula), SESSION DESIGN rules, PROGRESSION rules, last week's plan]

… OUTPUT — valid JSON only, no prose, no markdown fences. "Your FIRST output character must be the opening { …". Template shown:
{
  "rationale": "Short plain-language explanation of what changed in this plan and why.",
  "days": [ { "dayOfWeek": 1, "name": "Day name", "exercises": [ { "name": "Exercise Name", "sets": 4, "targetReps": "8-10", "targetWeightKg": 80.0, "notes": "RPE 8 (~2 RIR). Double progression: build to 10 reps across all sets…" } ] } ]
}
```

---

## THE MODEL'S RESPONSE (verbatim, unabridged)

```json
{
  "rationale": "This week shifts to a machine-and-cable-focus variation directive — but since you only have free weights, I've leaned heavily on dumbbell isolation movements (cable substitutes) while keeping barbells for true compounds. All blacklisted exercises are replaced: pulls now use barbell bent-over row and chest-supported dumbbell row instead of Pendlay/single-arm knee row; pushing uses barbell floor press and dumbbell variations not used last week; legs swap to barbell deadlift, dumbbell goblet squat, and hip thrusts. Arms (your priority) get direct bicep and tricep work twice across the week with fresh exercises. Ankle rehab continues with standing calf raises, single-leg balance (hand-supported wall touch), and tibialis work staged as light progression.",
  "days": [
    { "dayOfWeek": 2, "name": "Tuesday — Pull (Back & Biceps)", "exercises": [
      {"name":"Barbell Bent-Over Row (Underhand Grip)","sets":4,"targetReps":"6-8","targetWeightKg":45.0,"notes":"Primary compound. ~2 RIR. Double progression: 8 reps×4 then +2.5kg reset to 6.","recommendedRestSeconds":120},
      {"name":"Chest-Supported Dumbbell Row (Incline Bench)","sets":4,"targetReps":"8-12","targetWeightKg":20.0,"notes":"Accessory pull. ~2 RIR. To 12 then +2kg reset 8.","recommendedRestSeconds":90},
      {"name":"Dumbbell Hammer Curl","sets":4,"targetReps":"10-12","targetWeightKg":10.0,"notes":"Isolation. ~1-2 RIR. To 12 then +1kg reset 10. Priority arms.","recommendedRestSeconds":60},
      {"name":"Dumbbell Spider Curl (Chest on Incline Bench)","sets":3,"targetReps":"10-13","targetWeightKg":8.0,"notes":"Isolation. ~1-2 RIR. To 13 then +1kg reset 10.","recommendedRestSeconds":60},
      {"name":"Standing Dumbbell Calf Raise (Bilateral, Flat Floor)","sets":3,"targetReps":"15-20","targetWeightKg":16.0,"notes":"Ankle rehab/prehab. ~2 RIR. To 20 then +2kg.","recommendedRestSeconds":60},
      {"name":"Hand-Supported Single-Leg Balance Hold (Wall Touch)","sets":3,"targetReps":"30-40 s","targetWeightKg":0.0,"notes":"Ankle stability rehab. Reduce hand contact each session.","recommendedRestSeconds":45}
    ]},
    { "dayOfWeek": 3, "name": "Wednesday — Push (Chest, Shoulders & Triceps)", "exercises": [
      {"name":"Barbell Floor Press","sets":4,"targetReps":"6-8","targetWeightKg":55.0,"notes":"Primary push. ~2 RIR. To 8×4 then +2.5kg reset 6.","recommendedRestSeconds":120},
      {"name":"Dumbbell Overhead Press (Seated, Pronated Grip)","sets":3,"targetReps":"8-10","targetWeightKg":14.0,"notes":"Accessory vertical push (seated, low ceiling). ~2 RIR. To 10 then +2kg reset 8.","recommendedRestSeconds":90},
      {"name":"Dumbbell Lateral Raise","sets":4,"targetReps":"12-15","targetWeightKg":8.0,"notes":"Isolation lateral delt. ~1-2 RIR. To 15 then +1kg reset 12.","recommendedRestSeconds":60},
      {"name":"Dumbbell Skull Crusher (Flat Bench)","sets":4,"targetReps":"10-12","targetWeightKg":10.0,"notes":"Isolation tricep. ~1-2 RIR. Priority arms. To 12 then +1kg reset 10.","recommendedRestSeconds":60},
      {"name":"Dumbbell Bent-Over Rear Delt Fly","sets":3,"targetReps":"12-15","targetWeightKg":7.0,"notes":"Isolation rear delt. ~1-2 RIR. To 15 then +1kg reset 12.","recommendedRestSeconds":60}
    ]},
    { "dayOfWeek": 5, "name": "Friday — Legs (Quad & Posterior Chain)", "exercises": [
      {"name":"Barbell Deadlift","sets":4,"targetReps":"5-8","targetWeightKg":70.0,"notes":"Primary hinge. ≤8 cap. ≥2 RIR. To 8×4 then +2.5kg reset 5.","recommendedRestSeconds":120},
      {"name":"Dumbbell Goblet Squat","sets":4,"targetReps":"8-10","targetWeightKg":24.0,"notes":"Accessory knee-dominant, bilateral (ankle-safe). ~2 RIR. To 10 then +2kg reset 8.","recommendedRestSeconds":90},
      {"name":"Barbell Hip Thrust (Floor/Bench-Supported)","sets":4,"targetReps":"12-15","targetWeightKg":50.0,"notes":"Glute/hip-ext, exempt from hinge cap. ~2 RIR. To 15 then +5kg reset 12.","recommendedRestSeconds":90},
      {"name":"Dumbbell Romanian Deadlift","sets":3,"targetReps":"10-12","targetWeightKg":20.0,"notes":"Loaded DB hinge ≤12 cap. ~2 RIR. To 12 then +2kg reset 10.","recommendedRestSeconds":90},
      {"name":"Tibialis Raise (Seated, Heels on Floor, Toes Lift)","sets":3,"targetReps":"15-20","targetWeightKg":0.0,"notes":"Ankle rehab. Add light plate once 20 easy.","recommendedRestSeconds":45}
    ]},
    { "dayOfWeek": 6, "name": "Saturday — Upper (Arms Priority + Rear Delt)", "exercises": [
      {"name":"Barbell Curl","sets":4,"targetReps":"8-10","targetWeightKg":22.0,"notes":"Primary arm. ~2 RIR. To 10×4 then +2.5kg reset 8. Priority arms.","recommendedRestSeconds":90},
      {"name":"Dumbbell Overhead Tricep Extension (Seated, Two-Hand)","sets":4,"targetReps":"10-12","targetWeightKg":16.0,"notes":"Accessory tricep. ~2 RIR. To 12 then +2kg reset 10. Priority arms.","recommendedRestSeconds":90},
      {"name":"Dumbbell Concentration Curl","sets":3,"targetReps":"10-13","targetWeightKg":10.0,"notes":"Isolation bicep. ~1-2 RIR. To 13 then +1kg reset 10.","recommendedRestSeconds":60},
      {"name":"Diamond Push-Up (Close-Grip, Bodyweight)","sets":3,"targetReps":"10-15","targetWeightKg":0.0,"notes":"Isolation tricep BW. ~1-2 RIR. Add plate when 15×3 easy.","recommendedRestSeconds":60},
      {"name":"Dumbbell Reverse Fly (Lying Face-Down on Flat Bench)","sets":3,"targetReps":"12-15","targetWeightKg":7.0,"notes":"Isolation rear delt. ~1-2 RIR. To 15 then +1kg reset 12.","recommendedRestSeconds":60}
    ]},
    { "dayOfWeek": 7, "name": "Sunday — Upper (Chest & Back Volume)", "exercises": [
      {"name":"Dumbbell Flat Bench Press (Pronated Grip)","sets":4,"targetReps":"8-10","targetWeightKg":22.0,"notes":"Primary chest. ~2 RIR. To 10×4 then +2kg reset 8.","recommendedRestSeconds":120},
      {"name":"Dumbbell Chest-Supported Rear Delt Row (Wide Elbows)","sets":4,"targetReps":"8-12","targetWeightKg":14.0,"notes":"Accessory upper-back. ~2 RIR. To 12 then +2kg reset 8.","recommendedRestSeconds":90},
      {"name":"Ab Roller Rollout (Kneeling)","sets":3,"targetReps":"8-12","targetWeightKg":0.0,"notes":"Core. ~2 RIR. Increase ROM weekly.","recommendedRestSeconds":60},
      {"name":"Dumbbell Incline Lateral Raise","sets":3,"targetReps":"12-15","targetWeightKg":7.0,"notes":"Isolation lateral delt. ~1-2 RIR. To 15 then +1kg reset 12.","recommendedRestSeconds":60},
      {"name":"Standing Bilateral Calf Raise (Bodyweight, Slow Tempo)","sets":3,"targetReps":"20-25","targetWeightKg":0.0,"notes":"Ankle prehab. Hold DBs once 25 easy ×3 sessions.","recommendedRestSeconds":45}
    ]}
  ]
}
```

---

## Diagnostic lead (coordinator-relayed — a STARTING POINT for the orchestrator, NOT a prescription)
Running the app's own estimator over this response, **4 of 5 training days estimate UNDER the 40-min floor**: Wed ~33, Fri ~36, Sat ~31, Sun ~33; only Tue ~42 is in-window. Every attempt misses the deterministic ±10-min per-day gate, which short-circuits before the LLM review ("validation never happened"), and after the attempt limit the flow throws and saves nothing ("plan not used").

Per the confirmed scope, the fix stays **prompt-side with the strict gate intact** — i.e. steer the model so its days reliably land **inside** the window (here, stop them coming in under the floor). The orchestrator owns confirming this on the fixture and deciding the exact prompt change.
