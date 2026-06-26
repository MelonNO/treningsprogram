# Fixture — Expected Classification (R1)

Concrete expected outputs for the recognition fix. The 24 user-listed names are the primary examples; the pattern-guard rows below them lock the general behaviour and guard against regressions. **Names are verbatim — preserve exactly.** "Today" = behaviour observed in the current classifier at intake (v1.10.1).

## The 24 listed exercises

| # | Exercise (verbatim) | Today | Expected | Change? |
|---|---------------------|-------|----------|---------|
| 1 | Incline Walk | blank | **Cardio** | yes |
| 2 | Chest-Supported Dumbbell Row (Incline Bench) | Chest | **Back** | yes |
| 3 | Dumbbell Seated Arnold Press | blank | **Shoulders** | yes |
| 4 | Dumbbell Bent-Over Rear Delt Fly | Chest | **Shoulders** | yes |
| 5 | Dumbbell Flat Neutral-Grip Press (Chest Focus) | Chest | **Chest** | no (guard) |
| 6 | Dumbbell Chest-Supported Face Pull Alternative — Prone DB Rear Delt Fly | Chest | **Shoulders** | yes |
| 7 | Hand-Supported Single-Leg Balance Hold (Wall Touch) | blank | **Un-grouped** (non-muscle; excluded from volume & recovery) | no (preserve) |
| 8 | Dumbbell Overhead Press (Seated, Pronated Grip) | Shoulders | **Shoulders** | no (guard) |
| 9 | Dumbbell Face Pull Substitute — Dumbbell Prone Y-Raise (on Incline Bench) | Chest | **Shoulders** | yes |
| 10 | Dumbbell Squeeze Press (Flat Bench) | Chest | **Chest** | no (guard) |
| 11 | Ab Roller Roll-Out | Core | **Core** | no (guard) |
| 12 | Standing Ankle Balance Hold (Ankle Prehab) | blank | **Un-grouped** (non-muscle; excluded) | no (preserve) |
| 13 | Barbell Drag Curl | Arms | **Arms** | no (guard) |
| 14 | Dumbbell Renegade Row | Back | **Back** | no (guard) |
| 15 | High Knees | Cardio | **Cardio** | no (guard) |
| 16 | Tibialis Raise (Seated, Heels on Floor, Toes Lift) | blank | **Legs** | yes |
| 17 | Dumbbell Bent-Over Lateral Raise (Rear Delt) | Shoulders | **Shoulders** | no (guard) |
| 18 | Dumbbell Reverse Fly (Lying Face-Down on Flat Bench) | Chest | **Shoulders** | yes |
| 19 | Wall-Supported Single-Leg Calf Raise (Ankle Rehab) | Legs (Calves) | **Legs (Calves)** | no (guard) |
| 20 | Dumbbell Seated Neutral-Grip Overhead Press | Shoulders | **Shoulders** | no (guard) |
| 21 | Calf Raise on Step — Hand-Supported Single-Leg (Ankle Rehab) | Legs (Calves) | **Legs (Calves)** | no (guard) |
| 22 | Dumbbell Chest-Supported Rear Delt Row (Wide Elbows) | Chest | **Shoulders** (rear delt) | yes |
| 23 | Standing Single-Leg Ankle Balance Hold (Hand on Wall) | blank | **Un-grouped** (non-muscle; excluded) | no (preserve) |
| 24 | Ankle Alphabet / Foot Circles (Seated) | blank | **Un-grouped** (non-muscle; excluded) | no (preserve) |

Summary: **9 corrected**, **11 already-correct (locked as guards)**, **4 pure-balance preserved as un-grouped + must display gracefully**.

## Pattern regression guards (general behaviour, beyond the 24)

These representative names must classify as shown so the pattern fix does not over-reach. The exact strings are illustrative; the intent is that the *pattern* holds.

| Example name | Expected | Why it's a guard |
|--------------|----------|------------------|
| Chest Fly | Chest | plain "fly" stays Chest |
| Incline Dumbbell Fly | Chest | "incline … fly" stays Chest |
| Cable Fly | Chest | plain "fly" stays Chest |
| Rear Delt Fly | Shoulders | "rear delt"/"…fly" → Shoulders |
| Reverse Fly | Shoulders | "reverse fly" → Shoulders |
| Bench Press | Chest | press must NOT be pulled to Back by the row fix |
| Incline Bench Press | Chest | "incline bench" press stays Chest |
| Chest-Supported T-Bar Row | Back | "…-supported row" → Back (a row), not Chest |
| Seated Cable Row | Back | rows stay Back |
| Outdoor Run / Easy Jog | Cardio | genuine cardio stays Cardio |
| Stationary Bike / Treadmill | Cardio | genuine cardio stays Cardio |
| High Knees | Cardio | genuine cardio stays Cardio |
| Tempo Squat | Legs | "tempo" must NOT force Cardio; classify by movement |
| Interval Lunge | Legs | "interval" must NOT force Cardio; classify by movement |
| Tempo Bench Press | Chest | "tempo" must NOT force Cardio; classify by movement |

## Notes

- "Un-grouped" = the existing neutral "Training" treatment (blank stored muscle group; excluded by the `muscleGroup != ''` stat filtering). It is **not** a new visible category.
- A strength move mis-read as Cardio is also fed to the workout time estimate, so the tempo/interval rows above double as time-estimate guards: those names must produce a strength-style time estimate, not a cardio duration.
