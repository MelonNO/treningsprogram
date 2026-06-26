# Intake Index — Generation time-budget fix (post-v1.10.0)

**Prepared for:** Project-lead orchestrator
**Source:** User-reported bug after v1.10.0 shipped, with a strong user-supplied repro (exact built prompt + exact model response for one real failing case). Single item.
**Date:** 2026-06-26
**Status:** Understood and confirmed. NOT yet dispatched to the orchestrator (dispatch is a separate, later user instruction).

## Confirmation note
The bug understanding **and** the fix-direction decision were **relayed via the coordinator**, not given to the intake agent in the user's own words. The user was offered the alternatives (loosen the gate / add a never-discard salvage fallback) and explicitly chose **"keep the time-budget check strict — fix the prompt."** Relayed approval reflects the user's intent as conveyed; if the orchestrator needs the gate re-affirmed, confirm with the user directly. Creating this document is not the same as dispatching work.

The brief is **outcome-only**: it describes the desired end result and acceptance, never the implementation. The diagnosis and the exact prompt changes belong to the orchestrator and its workers.

## Items

| ID | Title                                                                              | Type                    | Brief file                                    | Status |
|----|------------------------------------------------------------------------------------|-------------------------|-----------------------------------------------|--------|
| G1 | Generation reliably yields a usable plan; strict time-budget gate kept, fix on prompt | Bug (generation reliability) | brief-G1-generation-timebudget-prompt-fix.md | Ready  |

## Cluster / seam notes
- **Single item, no clustering.** G1 is one focused change on the **AI generation seam** (`AiRepository` + the generation prompt).
- **Shared-seam hazard:** this is the **same hot seam** that B08 (rest-day scheduling), B09 (mid-week regenerate), and B10 (no-JSON/truncation hardening) just changed for v1.10.0. The failing repro is itself a rest-day-scheduled (B08) program. Re-verify the current code before acting; the prior batch's seam notes may have drifted. If any future AI-seam work is scheduled alongside this, coordinate — do not run uncoordinated parallel edits on this file/prompt.
- Because the cause is in the shared generation flow, the prompt-side fix benefits **all** generation entry points (the two the user named — training-profile popup and AI menu — plus first-run setup and the Program-tab regenerate paths).

## Confirmed decisions (from the user, relayed)
- Keep the deterministic per-day time-budget gate **strict**: do **not** widen/loosen the accepted duration window.
- Do **not** add a "save the best attempt anyway" / never-discard / salvage fallback.
- Fix on the **prompt side**: steer the AI so its training days reliably land inside the strict window and the gate passes.
- The lived behavior to eliminate: the AI produced a good plan but the app discarded it and the user got nothing.

## Assumptions applied (user may veto — see brief for detail)
- **[G1-A1]** "Reliably" is judged against the existing confirmed AI priority ordering (quality > fewer rejects > never fails); the bar is "the repro now succeeds and the rejection class is materially reduced," not "the gate never rejects again."
- **[G1-A2]** The fix lands once at the shared generation seam and benefits all entry points, though only two were named.
- **[G1-A3]** The "silent, no plan" complaint is the symptom of the rejection, not a separate request for failure-state messaging; the genuine all-retries-fail user-error path (shipped in B10) is out of scope and must not become a salvage path.

## Cross-cutting constraints
- Build via `./build.sh` (not `./gradlew`).
- No commits or releases unless the user explicitly asks.
- No on-device / automated UI tests unless the user explicitly asks.

## Files in this batch
- `INDEX.md` — this index.
- `brief-G1-generation-timebudget-prompt-fix.md` — the outcome-only brief.
- `repro-failing-generation.md` — the user's verbatim captured prompt + full model response for the failing case, plus the coordinator's diagnostic lead. Canonical regression fixture.

## Note for the orchestrator
- The verbatim repro is already saved (`repro-failing-generation.md`). The coordinator's **diagnostic lead** (4 of 5 days estimate **under** the 40-min floor → deterministic time-budget gate rejects every attempt → review step skipped → throws, nothing saved) is a **starting point to confirm on the fixture**, not a conclusion or an implementation.
