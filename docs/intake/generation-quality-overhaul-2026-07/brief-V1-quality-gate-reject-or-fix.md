# V1 — Quality gate that rejects-or-fixes, re-verifies, and never loops

**Type:** Feature (new quality gate) + reliability requirement
**Cluster:** Validator + salvage/finalize path + generation loop (same file as all briefs). Do LAST so it validates against the final gate shape and can consume P5's declared metadata — see INDEX.
**Covers:** the entire "Validation feedback" document (goal-, experience-, injury-, focus-muscle-, and movement-balance rejection rules) plus the user's two added requirements ("either completely reject or modify" and "make sure the modified workout sessions are actually verified").

> **Outcome-only.** Describes the target behavior, not the implementation.

## Context
After the objective gates (safety, schedule, equipment, injury, blacklist) pass, the app should judge whether the plan is *genuinely good training* for the user's goal, experience, days, duration, focus muscles, equipment, and injuries — and act on a poor plan rather than silently keeping it.

**Critical history (this app has been bitten twice):** an over-strict peer-review previously **false-rejected valid plans** and caused a no-save failure (fixed in v1.10.7 by neutering the time-budget review item because the deterministic gate already enforced duration). The generation loop already has anti-stall machinery: a capped number of attempts, an overall wall-clock deadline, and a deterministic REST-first **auto-trim salvage** that **re-runs the peer review on the trimmed plan before saving**. The new gate must build on this, not reopen the false-reject / infinite-loop wound.

## What the user wants (end result)
A quality gate that, on a genuinely poor plan, **actively corrects it** — never merely warns and keeps it, and never loops forever:
- **Reject-then-fix flow:** first try to get a better plan by **regenerating**, capped at a small number of attempts; if it still isn't good, the app **deterministically modifies/fixes** the plan into shape (like the existing REST-first auto-trim) or, if correction still isn't enough, **fails cleanly rather than saving an imperfect plan** (see the terminal ladder below).
- **Verify the fix:** after any deterministic modification, the modified plan must be **re-checked against the same validity/quality gates** (duration window, safety, movement balance, goal-fit, etc.) **before it is saved**. The fallback must produce a plan that *actually passes*, not just any plan.
- **No infinite loops, no false-reject stalls, no silently-kept bad plan.**

**Terminal ladder (user-confirmed):**
1. Generate, then validate against the gates.
2. If it fails, **regenerate** — capped at a small number of attempts.
3. If still failing, run the **deterministic auto-fix/modify** (e.g. the existing REST-first trim/adjust).
4. **Re-verify the modified plan against the same gates** (duration window, safety, movement balance, goal-fit, etc.) — the user's explicit requirement.
5. If the modified plan **passes**, **save it.**
6. If it **still doesn't pass**, **do NOT save anything** — show a clear, plain-language *"couldn't build a good plan"* failure (never a silent hang, never a partial/unclean plan, never an infinite loop).

The outcome is therefore always exactly one of two things, reached in **bounded time**: a **saved plan that passed every gate**, or a **clean, honest failure message**. The app must **never** save a best-available / imperfect plan.

The gate evaluates the "Validation feedback" rules — goal-specific, experience-specific, injury-quality, focus-muscle priority, and movement-balance — as the quality criteria.

## Acceptance criteria — Done when…
- A plan that is **clearly poor for the selected goal/experience** (per the Validation-feedback rules) is **not silently kept** — it is regenerated or auto-fixed.
- Regeneration is **capped**; after the cap, a **deterministic fix runs**, and the fixed plan is **re-validated against the same duration + safety + quality gates before saving**.
- The **movement-balance / coverage** checks **scale with days × duration** (§3.5) — a short or low-frequency week is **not** rejected for missing patterns it cannot fit.
- **Soft numeric ranges (§5) never cause a hard rejection** — no false-reject loop driven by a plan being slightly outside a guideline band.
- The flow **always terminates in bounded time** with exactly one of: a **saved plan that passed every gate**, or a **clear "couldn't build a good plan" failure message with nothing saved.** It **never** saves a best-available/imperfect plan, never hangs, and never loops.
- The **focus-muscle** rule is satisfied by at least one of: higher weekly direct volume, earlier placement, higher frequency, better progression detail, or more recoverable fatigue distribution (not all of them).
- The **injury-quality** rule agrees with P4: it does **not** demand rehab when there is no injury, and does not reject a MILD plan for not changing selection absent a named aggravator.

## Scope and constraints
- Build ON the existing capped-attempts + overall-deadline + REST-first auto-trim-then-re-review machinery; do not replace the deterministic duration gate (it is authoritative and must keep running first).
- The quality judgments that are **objective and checkable** (movement-balance presence scaled to days×duration, focus-muscle volume floor, goal-appropriate rep bias, coverage) should be enforced deterministically where practical; the **subjective** ones ("junk volume," "unclear progression," "not enough stimulus for advanced") must still drive a **correction (regenerate or modify)** per the user's "reject or modify" instruction — NOT be left as advisory-only warnings.
- Must not reintroduce the false-reject behavior: err toward **fixing** over rejecting when the only issue is a soft-band deviation.

## Decisions baked in (user-confirmed)
- "Either completely reject or modify" — the gate must *act*, not just warn.
- Any deterministically-modified plan must be **re-verified against the same gates before saving**.
- Capped reject→regenerate attempts, then deterministic fix, then re-verify — terminating in bounded time in either a saved passing plan or a clean failure; never loops.
- **If, after the capped regenerate attempts AND the deterministic auto-fix + re-verify, the plan still cannot pass every gate → surface a clear "couldn't build a good plan" failure and save NOTHING.** The app must not save a best-available/imperfect plan with a note. ("Always usable" now means "a plan that passed, OR a clean honest failure" — not "save an imperfect plan.")

## Assumptions applied (user may override)
- **[ASSUMPTION]** The existing MAX-attempts / overall-deadline values are retained unless the user wants them changed.

*(The earlier open decision — save-best-with-note vs fail-clearly — is now RESOLVED by the user: fail clearly, save nothing. It is recorded under "Decisions baked in" above.)*

## Considerations for whoever builds it
- The Validation-feedback rules are largely **subjective quality judgments**; encoding them as hard LLM rejections is exactly what caused prior false-reject pain. Favor: deterministic checks for the objective parts + a **bounded** regenerate-then-deterministically-fix loop for the rest, with the fix always re-verified.
- The movement-balance list in the doc (squat, hinge, horizontal push/pull, vertical push/pull, core/carry, rehab/prehab) must be **coverage-scaled** (§3.5) or it will false-reject short/low-frequency weeks.
- V1 depends on P5's declared metadata (sum from declared numbers instead of name-parsing) — sequence V1 after P5.
- Keep V1 consistent with P4 (injury) and P3 (soft bands) so the gate and the generator never contradict each other.
