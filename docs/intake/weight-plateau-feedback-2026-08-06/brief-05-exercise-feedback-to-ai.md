# Brief 05 — Per-exercise feedback that shapes the next generated program

**Type:** New feature (largest item in this batch)
**Cluster:** B (with brief 04 — both change what the AI is told; same file)
**Source:** User item 5, plus accepted improvement D

> **Outcome-only.** This brief describes the end result and user experience. It does not prescribe
> how to build it.

---

## Context

Today the user has **no way to tell the app anything about a specific exercise**. If a generated
program hands them an exercise they cannot perform well, their only options are blunt and global:

- **Settings → Training → "Exercises to exclude"** — a free-text list, applied everywhere.
- **Per-gym "exercises to avoid"** — a **hard** deterministic filter; the exercise is stripped from
  any plan saved for that gym, whatever the AI says.
- **The injury box** — free text about body parts and movements to avoid.

All three say *"never give me this"*. None of them can say *"I can't do this **yet** — help me get
there"*, which is exactly what the user wants.

## What the user wants (end result)

**The user's own example:** they are given a dumbbell single-leg RDL and don't have the balance for
it. They want to say so, have it saved, and have the AI take it into account next time it builds a
plan — and crucially, they want the balance itself **worked on**, not the exercise silently deleted.

### Where feedback is given

- While **logging a workout**, on the exercise in front of them.
- From the **Program tab**, on an exercise they see coming up. (5f)

### What giving feedback looks like

A **quick selectable reason**, plus an **optional free-text box** to say more or suggest a change.
(5a, 5b)

Proposed reason set — put to the user and not objected to:

- Too hard — can't do it properly yet
- Too easy
- Causes pain or discomfort
- Equipment not available or always busy
- Don't enjoy it
- Love it — keep it coming

### How the AI treats it

- As a **hint**, not a rule. It is weighed alongside everything else; it does not hard-filter. (5c)
- The **default response to "can't do this yet" is to keep the exercise and program toward it** — a
  regression or a supporting movement that builds the missing ability — **rather than dropping it.**
  (5a-ii)
- If the free text asks for a change or a swap, the AI may act on that. (5a: *"but make it be a text
  window where change can be suggested"*)

### Scope of a piece of feedback

- It applies to **that exercise only**. Not to similar or related movements. (5e)
- It applies **at every gym**. It is about the user, not the venue.

### Living with it afterwards (improvement D, accepted)

Feedback gets a **review-and-undo screen**, so the user can always see what is steering the AI and
take it back. Without this, notes accumulate invisibly and something said in one bad week shapes
programs forever.

## Acceptance criteria

- **Done when** the user can leave feedback on a specific exercise from **the workout logging
  screen**.
- **Done when** the user can leave feedback on a specific exercise from the **Program tab**.
- **Done when** leaving feedback takes a **single reason tap**, with the free-text box genuinely
  optional.
- **Done when** feedback **persists** across app restarts and is included in a backup export, and
  older backups still import.
- **Done when** feedback for an exercise **reaches the AI** the next time a program is generated.
- **Done when** feedback about not yet being able to do an exercise results in the AI being asked to
  **keep working toward it**, not to silently drop it.
- **Done when** feedback is a **hint** — it never hard-filters an exercise out the way the per-gym
  avoid list does.
- **Done when** feedback applies to that exercise alone and does not suppress or alter similar
  movements.
- **Done when** the user can **see every piece of feedback they have given, and remove any of it**,
  from one place.
- **Done when** the AI is told **when** each piece of feedback was given.
- **Done when** giving feedback never interrupts or slows down logging a set — it is opt-in and out
  of the way.

## Scope and constraints

**In scope**
- Capturing feedback, storing it, showing it back, removing it, and handing it to the generator.

**Out of scope**
- Changing or replacing the existing "Exercises to exclude" list, the per-gym avoid list, or the
  injury box. They keep working exactly as they do. (See D1.)
- Any hard guarantee that a given exercise will or will not appear. This is a hint by design.

**Hard constraints**
- **Feedback must never become a hard filter.** The user chose "hint" deliberately over "never
  program this again" (5c).
- **The default is keep-and-build-toward, not remove.** This is the core of the request and the
  thing that distinguishes it from the three mechanisms that already exist.

## Decisions baked in (confirmed by the user)

1. Given from the **logging screen and the Program tab**. (5f)
2. **Quick reason + optional free text.** (5b)
3. Treated as a **hint**. (5c)
4. Default response is **keep it and program toward it**. (5a-ii)
5. Applies to **that exercise only**. (5e)

## Decisions made under delegation — **veto-able**

The user answered 5d (*how this relates to the existing exclusion lists*) with **"u choose the best
solution"**. Per standing practice these were decided and are recorded here for the user to
overturn. They were shown all three and raised no objection.

- **D1 — Feedback is its own thing, not folded into the existing exclusion lists.**
  Rationale: those lists are hard filters; this is a nuanced hint. Merging them would turn *"this is
  hard for me"* into *"never show me this"*, which is the opposite of what the user asked for.
- **D2 — But it gets its review screen in Settings → Training, right beside "Exercises to
  exclude"**, so everything steering the AI is visible and undoable in one place. This is how
  improvement D is delivered.
- **D3 — Feedback persists until the user removes it, and the AI is told when it was given**, so
  a six-month-old *"I can't balance on this"* is treated as old news once the user has been training
  the ability. This follows directly from choosing "work up to it" — the whole point is that it
  stops being true.

## Assumptions applied (user may override)

- **A1 — The reason list above** is the proposed set. It was shown to the user and not objected to,
  but never explicitly ratified item by item.
- **A2 — One piece of feedback per exercise**, replaced if the user gives new feedback on the same
  exercise, rather than an accumulating log. Never put to the user.
- **A3 — The exact affordance** (how the user reaches "leave feedback" from each screen) is left to
  the builder to fit the app's existing patterns. The logging screen currently has **no
  exercise-level menu** at all, so something new is needed there. Bound only by the criterion that
  it must not slow down logging.

## Considerations for whoever builds it (surfaced, not decided)

- There is an existing **"this exercise keeps getting skipped"** prompt that offers to replace
  never-performed exercises. It overlaps conceptually with "too hard — can't do it yet". These two
  should not fire contradictory advice at the user, or push the AI in opposite directions on the
  same exercise.
- **Four** separate channels will now shape exercise selection (per-gym avoid, global exclude list,
  injury notes, and this). Their combined effect on the prompt is worth sanity-checking — including
  prompt length, given this app's history of generation timeouts and duration-gate problems.
- "Keep it and program toward it" is a genuinely hard instruction for a model to honour well. It is
  worth deciding what a good outcome actually looks like before building, and being able to tell
  whether the AI complied.
- Feedback naturally attaches to an **exercise name**, and names in this app come partly from the
  AI. Matching feedback to a slightly reworded exercise is the same problem the per-gym avoid list
  already solved with its name-matching contract — worth reusing that thinking rather than
  re-deriving it.
- New persisted data implies a schema change and a backup format bump.

## Grounded facts (verified 2026-08-06, for orientation only)

- **No per-exercise feedback channel exists today.** This is genuinely new.
- Per-gym avoid list: `GymPreset.avoidExercisesJson`, with matching and the hard post-parse filter in
  `/home/migul/treningsprogram/app/src/main/java/com/migul/treningsprogram/domain/GymExclusions.kt`
  (its name-matching contract is documented in that file's header).
- Global exclude list and injury box: Settings → Training
  (`/home/migul/treningsprogram/app/src/main/res/layout/fragment_settings_training.xml`, lines 354
  and 426).
- The generator's prompt assembly, including where the trend and stalled-lift blocks are built, is in
  `/home/migul/treningsprogram/app/src/main/java/com/migul/treningsprogram/data/repository/AiRepository.kt`
  (around lines 1844–1881). **Brief 04 edits this same region — coordinate.**
- Never-performed replacement logic:
  `/home/migul/treningsprogram/app/src/main/java/com/migul/treningsprogram/domain/NeverPerformedExercises.kt`.
- On the logging screen today: tapping an exercise name opens an info sheet; long-pressing opens a
  setup-note dialog. **There is no exercise-level menu.**

## Standing constraints

- Build via `./build.sh`, never `./gradlew` directly.
- No commits or releases unless the user asks.
- No on-device or automated UI tests; verify via build + unit tests. The user does the device check.
- Live Anthropic API calls must stay minimal and decision-driven (standing rule — a past sweep
  drained the API credits and the monthly spend limit).
