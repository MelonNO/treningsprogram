---
name: project-feature-batch-2026-06
description: 2026-06 exploration batch — 8 explored feature ideas, which were confirmed/briefed vs dropped/pending, and the key product decisions the user made
metadata:
  type: project
---

In June 2026 the user ran an open-ended feature exploration for treningsprogram. From a larger first round, the user selected and went deep on nine ideas, then narrowed to a batch.

**Outcome (intake at `docs/intake/feature-batch-2026-06/`):**
- **Briefed & confirmed (all 8):** B1 AI weekly coach summary; B2 program-change "why" rationale; B3 plateau detection; C1 per-exercise PR/estimated-1RM trends; C4 muscle-group recovery view; E1 manual program editing; E2 multiple-programs + periodized mesocycles; E3 exercise-library browser.
- **E2 decisions (resolved):** L→L1 AI-driven weekly progression within blocks (per-week call accepted); M→M2 deload is stall/fatigue-triggered (reuses B3) — deliberately couples E2 to B3.
- **DROPPED:** D4 Garmin integration — removed entirely.

**Key product decisions the user made (don't re-ask):**
- Extra Claude API calls are acceptable; use the model where it adds value.
- B1 cadence = automatic weekly.
- B2 "why" = model emits a `rationale` field in the generation response it already produces (its own reasoning).
- B3 stall criterion and C4 recovery windows must be **grounded in cited exercise science**, not arbitrary numbers (progressive overload / estimated-1RM / volume-load for B3; MPS time-course + training-frequency lit for C4).
- E1 kept simple: manual edits last only until next regeneration; a regen overwriting them is accepted.
- E2 = BOTH named saved programs AND periodized mesocycle blocks with deload. L→L1 (AI-driven weekly progression in blocks); M→M2 (stall/fatigue-triggered deload, reuses B3).
- C4 = fixed recovery-window coloring (recovering / ready / overdue).

**Parallelization plan baked into the INDEX:** P1 = B1+B2+B3 (one worker, share AiRepository/history-context; do B3 early); P2 = E2→E1 (one worker, sequenced, share planned_exercises + Program tab); P3 = C1+C4+E3 (parallel, read-mostly, distinct screens). **New ordering edge from M2: B3 → E2's deload piece** (E2 deload reuses B3) — rest of E2 unblocked. Other seams: `AiRepository` (P1↔P2, L1 keeps E2 in generation flow), DB schema version (B1↔E2), Program tab (P2↔C4 if C4 placed there). See [[intake-doc-format]].

**Enforced build order (`SEQUENCE.md`):** the user asked for a concrete, enforceable numbered-wave sequence the coordinator dispatches one wave at a time. Waves: W1 = C1+C4+E3 (parallel) + B3 (B3 pulled early as E2-deload prerequisite); W2 = B2 then B1; W3 = E2 internally ordered storage→generation(L1)→deload(M2); W4 = E1. Critical path B3→B2→E2→E1. Hard gates: AiRepository serialized across B2/B3/E2; DB schema bumps one at a time (B1, E2); E2-storage before E1; B3 before E2-deload. C4 placed on Home (not Program tab) to stay parallel-safe.

**Hardened per-wave verification gate (user refinement):** each wave needs an explicit evidence-backed **Wave Verification Report** + a coordinator-confirmed **PASS** before the next wave dispatches. "Fully tested" = (1) ./build.sh debug+release green; (2) ./build.sh test pass with counts incl. new tests; (3) on-device `ui-test-worker` evidence (Maestro/hierarchy/screenshots) for every UI-affecting unit mapped to acceptance criteria — analytics-only units may note "no UI surface, JVM-only" with justification; (4) AiRepository + Room schema/migrations left coherent. No confirmation → no next wave; partial/failed = rework + re-verify. This per-wave ui-test-worker requirement IS the explicit "ask" that satisfies the batch's standing no-unprompted-UI-testing rule (scoped to verifying these features; does NOT authorize commits/releases). See [[feedback-prefer-ondevice-verification]] and the no-auto-commit/release feedback.

**Final consistency-pass resolutions (locked when user delegated finalization):**
- C4 SPLIT into (a) recovery VIEW = parallel-safe Wave-1, placement LOCKED to Home (not Program tab, to avoid the P2 seam); (b) AI-NUDGE = touches AiRepository, sequenced onto the serialized seam, NOT in Wave 1. Inform-only override removes (b). This fixed a real contradiction (Wave 1 claimed "B3 is the only AiRepository touch" while C4's nudge also touched it).
- The hardened gate REQUIRES on-device ui-test-worker verification → reconciled the "no on-device tests unless asked" line in all 8 briefs to say the gate IS the explicit ask. No-commit/release rule still fully holds.
- B2's rationale, if persisted (assumption D), is a Room bump → folded into dep#4 "one schema bump at a time" alongside B1 (Wave 2) and E2 (Wave 3).
- All 11 labelled assumptions LEFT AS-IS as locked overridable defaults.

**My sign-off:** reviewed the full set for completeness + internal consistency, fixed the contradictions above, and declared FINISHED/satisfied — that sign-off is the coordinator's trigger to start Wave 1. Still NOT dispatched until the user gives the separate go.

**Status note:** docs are created (INDEX.md, 8 briefs, SEQUENCE.md) but NOT dispatched to the orchestrator — that is a separate later user instruction.
