# Issue 12 — Mid-exercise easier/harder swap for calisthenics, with suggestions

**One-liner:** Let the user swap a calisthenics exercise mid-workout for an easier or harder variant, with suggested alternatives.

## App context
The active "Log" session presents one exercise at a time. Some prescribed exercises are calisthenics (bodyweight), which have natural easier/harder progressions (e.g. close-grip push-up ↔ push-up ↔ harder variants).

## Current (incorrect) behavior
- There is no way to switch a calisthenics exercise to an easier or harder variant during a workout. If the user can't perform the prescribed variant, they're stuck.

## Correct behavior (target)
- During a workout, on a calisthenics exercise, the user can open a swap option that **suggests** alternatives: at least one **easier** (regression) and one **harder** (progression) variant. Example: can't do a close-grip push-up → suggest a normal push-up.
- Selecting a suggestion replaces the current exercise for this session cleanly (logging continues against the chosen variant).
- The AI's plan generation should also be progression/regression-aware for calisthenics.

## Acceptance criteria
- [ ] On a calisthenics exercise mid-session, a swap UI offers ≥1 easier and ≥1 harder suggested alternative.
- [ ] Selecting one replaces the exercise without losing session state.
- [ ] Non-calisthenics exercises are unaffected (or handled per a separately confirmed rule).

## Coordination / related issues
- **Catalog-dependent (shared with Issues 10, 11, 13).**

## Constraints & scope
- Requires a notion of **difficulty ordering within calisthenics families** (progression ladders). The catalog has a coarse `level` (beginner/intermediate/expert) but not fine-grained progression chains — **flag that a progression mapping for calisthenics may need to be authored**, and treat its source as a decision rather than inventing arbitrary chains.
- Scope to calisthenics for now.
