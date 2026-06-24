# B2 — "Why did the program change?" rationale

**Type:** New feature
**Cluster:** P1 (AI coaching) — one worker with B1 and B3.
**Outcome-only:** Describes the end result and user experience, not the implementation. The "how" is the orchestrator's.

---

## Context

The app's AI regenerates the user's weekly program adaptively (`AiRepository.generateAdaptedProgram`), and it already loads the previous plan as context (`buildPreviousPlanContext`, `WorkoutRepository.getLatestPlanWeekStart`). But from the user's side, the adaptive engine is a **black box**: the program changes week to week and the user is never told *why*. This undermines trust in the very feature that makes the app distinctive.

This feature surfaces a plain-language **rationale** for what changed and why, each time the program is (re)generated.

## What the user wants (end result)

When the AI generates or regenerates the program, the user can see a short, plain-language explanation of the changes and the reasoning behind them — e.g. "added rows for posterior-chain emphasis," "dropped incline press because the shoulder was flagged," "increased bench load after three sessions of progress." The user understands *why* their plan looks the way it does.

## How the "why" is produced (confirmed decision)

- The user chose: **the model emits a `rationale` field as part of the generation response it already produces.** The explanation is the model's **own stated reasoning**, captured from the same generation call — not a separately inferred local guess. This is essentially free (it piggybacks an API call already being made).

## Acceptance criteria ("Done when …")

- **Done when** after the program is generated or regenerated, the user can read a concise, plain-language rationale for the program, expressed as the model's own reasoning for its choices.
- **Done when** the rationale reflects the actual generated plan (it is produced together with that plan, not detached from it).
- **Done when** the rationale is reachable from where the user views their program, in an obvious place.
- **Done when** a generation that produces no meaningful rationale (or an older plan generated before this feature existed) shows a sensible empty/neutral state rather than an error.

## Scope and constraints

- **In scope:** capturing the model's rationale from the generation response; showing it to the user alongside their program.
- **Out of scope (unless the user later asks):** a per-exercise interactive diff UI; a separate API call dedicated to explanations (the rationale rides the existing generation call).
- **Cross-cutting constraints:** build via `./build.sh`; no git commits/releases unless the user asks; **on-device UI verification IS required for this batch** per the per-wave gate in `SEQUENCE.md`; mindful of `PreferencesManager` churn.

## Decisions baked in

- **Rationale = a field in the generation response** (the model's own reasoning), user-confirmed.

## Assumptions (user may override)

- **ASSUMPTION (persistence, question D):** the rationale **persists with the saved plan** and remains visible on the Program tab **until the next regeneration**, rather than appearing only once immediately after generation. *(If the user prefers a one-time post-generation message, that is simpler and stores nothing; flagged so they can veto.)*

## Considerations for whoever builds it (surfaced, not decided)

- **Seam with E2/B3:** this edits the generation prompt/response shape in `AiRepository`. B3 may also feed signals into that prompt, and E2 will rework generation to be program/mesocycle-aware. Per the INDEX, B2 and B3 are the **same worker**; coordination with E2 on `AiRepository` is a flagged cross-group hazard.
- **Possible schema touch (from assumption D):** if the rationale **persists with the saved plan**, that is a small storage/Room change. Per `SEQUENCE.md`, B2 sits in Wave 2 alongside B1; any Room bump from B2 must be **sequenced one version at a time** with B1's (dep #4), and then E2's in Wave 3. If the user instead chooses a one-time (non-persisted) message, B2 has no schema touch.
- The rationale is the model's self-report. It should be presented as the AI's stated reasoning, not as an audited ground-truth diff.
