# Brief 04 — Adding reps at a lower weight must not be called a plateau

**Type:** Bug — **DIAGNOSE FIRST**
**Cluster:** B (with brief 05 — both change what the AI is told; same file)
**Source:** User item 4, plus accepted improvement C

> **Outcome-only.** This brief describes the end result and user experience. It does not prescribe
> how to build it. The candidate causes below are orientation for the diagnosis, **not** a
> prescribed fix.

---

## Context

The **Stats → Progress** screen shows a "Plateau detected" card naming a specific lift and
suggesting a deload, a rep-scheme change, or a variation.

Separately, the AI program generator is told about plateaus when it builds the next week's plan.

## Current (incorrect) behaviour — the user's actual case

The user was told **dumbbell incline bench press** had plateaued, while they were in fact
progressing:

1. One session at **28 kg** — too heavy, one set only.
2. Next session, backed off to **26 kg**.
3. Session after that, **26 kg again with more reps**.

Backing off to a weight you can control and then building reps is the textbook correct response to a
too-heavy session. The app called it a plateau.

The user does not remember the exact rep counts (*"i dont know"*), so **the shape of the scenario is
the specification**, not a specific set of numbers.

## Correct behaviour

**Adding reps at the same weight across sessions is progress, and must never be reported as a
plateau — even when an older, heavier set still scores a higher estimated 1RM.** Confirmed verbatim
by the user (4f: *"yes"*).

## Where the user saw it

The **"Plateau detected" card on Stats → Progress** (4a-i). That is the confirmed reported surface.
Improvement C extends the fix to the AI side as well — see below.

## Improvement C (accepted) — the two plateau rules must agree

There are **two different, independent plateau definitions** in the app today, and they can
disagree:

- One is **estimated-1RM based** and rep-aware in principle. It drives the Progress card and one
  block of the AI prompt.
- The other is used to label each lift's trend for the AI and is **purely weight-based** — it
  compares the first and last weight in the window and calls anything within ±2.5 kg "PLATEAUED".
  It cannot see reps at all.

So a lift can be labelled plateaued in the AI's data while not appearing as stalled on the Progress
screen — and the AI then plans against a plateau the user is not actually having.

The user accepted improvement C: **both must use the same rep-aware rule**, so the screen and the AI
stop contradicting each other.

## Acceptance criteria

- **Done when** the following sequence does **not** produce a plateau report anywhere: a session at a
  higher weight, followed by two sessions at a lower weight with the rep count increasing between
  them.
- **Done when** a run of sessions at an unchanged weight with **rising reps** is treated as progress,
  not a plateau.
- **Done when** a genuine plateau — weight flat **and** reps flat or falling across the window —
  **is still reported**. This fix must not simply disable the feature.
- **Done when** a deliberate back-off to a lighter weight does not, by itself, make the app report a
  plateau.
- **Done when** the Progress screen's plateau card and the information handed to the AI agree with
  each other for the same lift and the same history — no lift is plateaued in one place and
  progressing in the other.
- **Done when** the user's own case is covered by a regression test built from the described shape
  (heavier session → two lighter sessions with reps climbing → not flagged).
- **Done when** warm-up sets remain excluded from the judgement, as they are today.

## Scope and constraints

**In scope**
- What counts as a plateau, wherever that judgement is made and shown.
- The trend label handed to the AI (improvement C).

**Out of scope**
- **Bodyweight exercises are explicitly unchanged.** The user was asked whether pull-ups and dips
  should become eligible for plateau detection (they currently never can be, because they carry no
  weight) and answered **"no change needed"** (4d).
- The wording and layout of the plateau card.
- The deload feature that consumes plateau signals — behaviour changes only through the corrected
  input.

**Hard constraints**
- Do not fix this by weakening detection until nothing is ever flagged. A real plateau must still
  surface.

## Decisions baked in (confirmed by the user)

1. Reps count as progress even when an older heavier set scores higher. (4f)
2. The reported surface is the Stats → Progress card. (4a-i)
3. The extra reps were on the **lower** weight, not the top-weight session. (4c)
4. Bodyweight lifts: no change. (4d)
5. The AI's separate weight-only plateau label is brought in line. (Improvement C, accepted)

## Candidate causes for the diagnosis (orientation only — verify before acting)

Two independent suspects were found while grounding this brief. **Both should be checked; either
alone could produce the user's report, and they may compound.**

**Suspect 1 — the comparison is anchored to the first session in the window.**
`StallDetector.isStalled` takes the last three sessions, seeds its running best from the **first** of
them, and declares a stall unless a later session beats that seed. In the user's case the first
session in the window **is the 28 kg one**. Their two genuinely-progressing 26 kg sessions never beat
it, so the lift is flagged. Under this rule, a back-off followed by a rebuild is structurally
guaranteed to read as a plateau for the whole window.
File: `/home/migul/treningsprogram/app/src/main/java/com/migul/treningsprogram/domain/StallDetector.kt`
(window logic lines 73–85; `STALL_WINDOW = 3`, `IMPROVEMENT_EPSILON_KG = 0.5`).

**Suspect 2 — the reps taken from each session may not be the best ones.**
Each session is reduced to a single `maxWeight` / `bestReps` pair. When several sets share the same
weight — exactly the user's 26 kg sessions — it is worth verifying that `bestReps` is genuinely the
**highest** rep count at that weight and not an arbitrary set's. If it is arbitrary, rep progress at
a constant weight can be invisible regardless of Suspect 1.
Source: `WorkoutSetDao.getStrengthHistory` → `StrengthPoint`.

**The weight-only AI label (improvement C's target):**
`/home/migul/treningsprogram/app/src/main/java/com/migul/treningsprogram/data/repository/AiRepository.kt`
line 1858 — `"PLATEAUED (… sessions at ~…kg)"`, derived from first-vs-last `maxWeight` with a
±2.5 kg band. The separate, rep-aware STALLED LIFTS block is at lines 1864–1881.

## Considerations for whoever builds it (surfaced, not decided)

- Changing what counts as a plateau also changes **what the AI is told**, which affects generated
  programs. Generation quality has been a repeated pain point in this app — this needs a careful
  eye, and it shares a file with brief 05.
- Plateau signals also feed the **deload** feature. Fewer false plateaus means fewer spurious
  deloads, which is desirable, but the interaction should be consciously checked rather than
  discovered later.
- There is a question of principle worth being explicit about in the fix: after a deliberate
  back-off, what should progress be measured *against*? The user's answer implies "against where you
  are now", not "against your best-ever heavy single". The chosen answer should be documented.

## Standing constraints

- Build via `./build.sh`, never `./gradlew` directly.
- No commits or releases unless the user asks.
- No on-device or automated UI tests; verify via build + unit tests. The user does the device check.
- Live Anthropic API calls must stay minimal and decision-driven (standing rule).
