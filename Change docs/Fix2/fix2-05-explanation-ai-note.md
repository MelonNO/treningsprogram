# fix2-05 · Exercise explanation window: add AI note alongside DB info

**Type:** Enhancement.

## Context
Native Android app. During an exercise, the explanation window shows information for the matched **local DB** exercise. Separately, the AI generates a **short, exercise-specific explanation** when it creates the exercise (this text already exists per exercise).

## Current behavior
The explanation window shows the DB information but **not** the AI's short explanation.

## Correct behavior
The explanation window shows **both**: the AI's short, exercise-specific explanation **in addition to** the DB information. (No-DB-match behavior is already handled — show what's available; no change needed there.)

## Acceptance
- [ ] The explanation window includes the AI's short explanation for the exercise, alongside the DB info.
- [ ] Both are clearly presented and sensibly arranged (the AI note distinguishable from the DB content).
- [ ] No-DB-match case is unchanged (AI note still shows where present).

## Constraints
- The AI note already exists per exercise — surface it here; don't regenerate or duplicate it.
