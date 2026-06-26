# S3 — Sweep: AI generation, adaptation & coaching outputs

**Type:** Bug sweep (diagnose-first)
**Phase:** 1 (bug sweep & fixes)
**Cluster:** SW-B (program & generation) — see INDEX.
**Outcome-only:** Defines *where to look* and *the bar to hold*. Workers diagnose and fix.

---

## Context

`data/repository/AiRepository.kt` — `buildPrompt`, `generateAdaptedProgram`, `buildSessionHistory`, `parseProgram`, `extractJson`. Calls `claude-sonnet-4-6` via Retrofit/OkHttp. Plus the v1.8.0 AI-derived outputs: **B1 weekly coach summary** (`WeeklySummary` entity, `weeklySummaryFragment`, `WeeklySummaryTrigger`), **B2 program-change rationale**, and the recovery/stall signals fed into the prompt. This is the app's defining feature and its most failure-prone surface (network + model + JSON parsing).

> **Note:** the AI 120s timeout / silent-hang resilience is its own first-class fix brief — **F3 (P-1)**. This sweep covers correctness/robustness of generation, parsing, and the AI-derived readouts; it should **coordinate with F3** rather than duplicate it.

## The hunt (where to look)

- **Generation & parsing:** malformed/partial JSON, markdown-fence stripping, model returning prose or extra fields, empty plan, unknown exercise names; failure surfaced clearly vs silent failure.
- **Adaptation:** the weekly auto-adapt path, history-context assembly (`buildSessionHistory`, strength history, weekly volume) — correctness of what's sent and how the result is applied.
- **Coach summary (B1):** generation cadence/trigger, persistence, the `weeklySummaryFragment` readout, empty/first-week state.
- **Rationale (B2):** the "why did the program change" text appearing with the saved plan; correctness when absent or stale.
- **Prompt inputs:** recovery (C4) and stall (B3) signals feeding the prompt — sane behavior when those signals are empty/edge.

## Acceptance bar (ALL tiers apply)

Full severity bar: crashes & data-loss, functional correctness (wrong/lost plan, mis-parsed response), visual/layout of readouts, minor polish.

## Adversarial / edge states to exercise (on-device)

No-network; no-API-key; invalid API key; slow/timed-out response (coordinate with F3); model returns junk/empty; first-run with no history; generation interrupted by backgrounding; very long history.

## Acceptance criteria ("Done when …")

- **Done when** generation, adaptation, B1 summary, and B2 rationale have been exercised on-device through the adversarial states **and** code-reviewed, with findings recorded against the all-tiers bar.
- **Done when** every failure mode (no key, no network, junk response, timeout) ends in a **clear user-visible state, never a silent hang or silent data loss** (timeout/retry handled by F3; this brief verifies the *outcome*).
- **Done when** each confirmed defect is **diagnosed to root cause before any fix**, then fixed and re-verified.
- **Done when** B1/B2 readouts show sensible empty/first-run states.

## Scope & constraints

- **Diagnose first.**
- **Coordination:** the **hottest seam in the batch** — `AiRepository` is also touched by F3 (P-1). S3 and S2 form cluster SW-B (one worker / coordinated) to prevent blind concurrent edits to `AiRepository`/`WorkoutRepository`. **F3's timeout/retry change should land first or be coordinated**, since this sweep relies on the timeout behavior being defined.
- **Cross-cutting:** build via `./build.sh`; no commits/releases unless asked; on-device verification required (SEQUENCE.md).
