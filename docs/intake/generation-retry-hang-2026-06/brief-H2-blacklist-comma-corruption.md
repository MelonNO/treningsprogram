# H2 — The generation prompt's exercise blacklist is corrupted by comma-splitting names

**Type:** Bug (prompt construction — data corruption in the generation prompt)
**Cluster:** AI generation seam (`AiRepository.buildPrompt` blacklist assembly) — same file as **H1**, independent change.
**Priority:** Medium — does **not** cause the H1 hang; it degrades plan quality (the model is fed junk constraints and may re-use exercises that should be blacklisted).
**Outcome-only:** describes the desired end result, not the implementation.

## Context
The generation prompt includes an **"EXERCISE BLACKLIST — DO NOT USE THESE"** section, telling the model which recently-used exercises to avoid this week. It is assembled in `AiRepository.buildPrompt` from the user's recent sessions and last week's plan. This was surfaced while investigating H1 — it is visible in the captured prompt (`evidence/attempt1_prompt.txt`).

## Current vs correct behavior
- **Current (incorrect):** Exercise **names that contain a comma** are split on the comma into meaningless fragments. In the captured prompt the blacklist contains junk tokens such as `Alternating)`, `Slow Tempo)`, `Dumbbell Push Press (Seated`, `Dumbbell Calf Raise on Step (Bilateral` — i.e. names like *"Dumbbell Push Press (Seated, Alternating)"* and *"Dumbbell Calf Raise on Step (Bilateral, Slow Tempo)"* have been shattered. In addition, at least one **non-exercise line** has been ingested into the blacklist (a stray `Mon: Barbell Bench Press`, which looks like a schedule/annotation line rather than an exercise list). The result: the blacklist contains meaningless fragments, while the real multi-word exercise names are no longer represented as whole names (so the model can fail to recognise and may re-use them).
- **Correct:** The blacklist should list the **actual, whole exercise names** — with commas inside a name preserved — and should contain **only real exercise names**, no junk fragments and no non-exercise lines.

## Diagnose first (lead grounded in the code; confirm before acting)
- The blacklist is built by extracting the text after a colon on context lines and then **splitting that text on commas** — which breaks any exercise name that itself contains a comma, and also harvests the colon-suffix of lines that are not exercise lists (e.g. a `Mon: …` schedule line). Confirm the exact source/format of the context being parsed and correct the parsing so that (1) names containing commas stay intact and (2) only genuine exercise names are collected.
- Note: the parsing involved is **not** the cause of the H1 hang (the regex is linear, runs identically on every attempt, and completes); this is purely a correctness/quality issue in **what the model is told**.

## Acceptance criteria (observable)
- **Done when** the blacklist in the generation prompt lists **whole exercise names**, including names that contain commas, with **no junk fragments** (no `Alternating)`, `Slow Tempo)`, dangling `(Seated`, etc.).
- **Done when** **non-exercise lines** (e.g. `Mon: …` schedule annotations) no longer appear as blacklist entries.
- **Done when** a user whose recent history includes a comma-containing exercise name sees that exact name blacklisted intact (so the model reliably avoids re-using it).

## Scope and constraints
- **In scope:** the construction of the exercise-blacklist text in the generation prompt only.
- **Out of scope:** the time-budget gate, the retry/hang behavior (H1), and any change to the gate's strictness or to plan-saving.
- **Standard cross-cutting constraints:** build via `./build.sh` (not `./gradlew`); no commits/releases unless asked; no on-device/automated UI tests unless asked.

## Assumptions (user may override)
- **[H2-A1]** This is treated as a quality/correctness fix bundled with H1 because it lives in the same prompt-construction code; it is independent and could ship separately if the user prefers.
