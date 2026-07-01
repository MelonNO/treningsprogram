# Brief 11 — AI injury-text sufficiency check in the setup wizard

**Type:** Feature
**Cluster:** Independent, but **coordinate with P4** (injury handling landing in v1.13.0).
**Outcome-only:** This brief describes the end result and user experience. It deliberately does not prescribe implementation.

## Context
During setup, the user can describe injuries/limitations in a free-text box; the generator plans around that text. A terse or vague description ("bad knee") gives the generator too little to work with. The app already has an AI "clarifying questions" pattern used elsewhere in onboarding (`getOnboardingQuestions`), which is the natural model for this.

This is the **input** side of injury handling. The **consumption** side (how the generator uses injury text — empty = no-op, severity-scaled selection) is being reworked by **P4** in the generation-quality-overhaul batch now landing as **v1.13.0**. Item 11 feeds a clean description *into* that flow; it must complement, not duplicate or contradict, P4.

## What the user wants (end result)
In the **setup wizard**, after the user writes their injury description, an **AI-driven** check judges whether the description is detailed enough to plan a workout around. If it is **not** sufficient, the user is asked **follow-up questions**; their answers are used to **rewrite the injury box into one clean, sufficient description** (a rewrite of the whole box, not appended snippets). If the injury box is **empty**, the check is **skipped entirely** (empty = no-op).

## Acceptance criteria (Done when …)
- In the **setup wizard**, when the injury box is **non-empty**, an **AI-driven** sufficiency check runs before the description is finalised for generation.
- If the description is judged **insufficient**, the user is presented with **follow-up questions** (AI-generated, targeting what's missing).
- The user's answers result in the injury box being **rewritten into a single clean description** that incorporates the new detail (rewrite, not append).
- If the description is judged **sufficient**, no questions are asked and the flow proceeds.
- If the injury box is **empty**, the check does **not** run (no questions, no-op) — consistent with the P4 empty-injury = no-op decision.
- The check fires **only in the setup wizard** — not on every Generate action, and not live on field-blur elsewhere.

## Scope and constraints
- **In scope:** the setup-wizard injury free-text: AI sufficiency judgment → follow-up questions → rewrite the box.
- **Out of scope:** how the generator *uses* the injury text (that's P4/v1.13.0); running this check outside the setup wizard; a heuristic/character-count check (the user chose AI-driven).
- **Coordinate with P4:** plan against the **post-overhaul** shape of `AiRepository.kt` / `getOnboardingQuestions` (v1.13.0), not the current code. Reuse the existing AI clarifying-questions machinery rather than inventing a parallel one where possible.

## Decisions baked in
- **AI-driven** sufficiency judgment.
- **Setup wizard only.**
- Insufficient → follow-up questions → **rewrite** the injury box (not append).
- **Empty injury = skip** the check.

## Assumptions (user may override)
- **[A-5]** "Sufficient" = the AI can identify a concrete body part / movement restriction it can actually plan around (not a fixed character count).
- **[A-11a]** If the user prefers not to answer the follow-up questions, they can proceed with what they wrote (the check informs, it doesn't hard-block setup). Confirm with the user if they'd rather require a sufficient description before continuing.

## Considerations for whoever builds it
- This adds an AI round-trip inside the wizard — keep the wait friendly and non-blocking, consistent with the app's existing generation-wait UX.
- Since `AiRepository.kt` is being heavily edited by v1.13.0, sequence/rebase to land on top of it and reuse P4's injury conventions (severity, empty-handling) so the input and consumption sides stay consistent.
- The rewrite should preserve the user's original meaning while adding the elicited detail — surface to the user what it rewrote (so it doesn't silently distort their description).

## Standing constraints
- Build with `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.
