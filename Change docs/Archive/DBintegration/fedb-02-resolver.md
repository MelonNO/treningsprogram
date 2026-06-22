# fedb-02 · U-RESOLVE — name→id resolver (with attribute disambiguation)

**Role:** Map an arbitrary exercise name to a DB `id`, or return null. This is the core, and the place the wrong-image risk lives.

**Depends on:** `fedb-01` (normalized catalog + token sets).

## The non-negotiable principle
**The name identifies the exercise. Muscle group / equipment / mechanic only narrow the field.** Attributes are used to **disambiguate among name-similar candidates** and to **confirm/gate** a match — they are **never** used to pick an image on their own. If the name cannot be confidently resolved, **return null** (→ placeholder). Do **not** substitute the "closest muscle-group exercise."

## Algorithm (run in order; stop at first confident hit)
1. **Normalize** the input: lowercase, strip punctuation/extra whitespace, **expand abbreviations** via a dictionary (seed: DB→dumbbell, BB→barbell, KB→kettlebell, OHP→overhead press, RDL→romanian deadlift, SLDL→stiff-leg deadlift, CG→close grip, SA→single arm, alt→alternate, ext→extension, BW→bodyweight…). Canonicalize equipment/grip synonyms.
2. **Exact match** on normalized name → `{exact, confidence: 1.0}`.
3. **Alias table** (curated variant→id, seeded by `fedb-06`) → `{alias}`.
4. **Gated fuzzy score** over a candidate shortlist (token overlap / edit distance). Apply the **discriminator gate** (below). Among surviving candidates, **break ties using attribute hints** (`hints.muscle`, `hints.equipment`) and the entry's `primaryMuscles`/`equipment` — but only to choose between *name-similar* options, and only when the field is populated. Accept only a clear winner above threshold → `{fuzzy}`.
5. **Constrained LLM fallback — ON whenever steps 1–4 find no match** (it runs before returning null). Build a shortlist of the top-N plausible candidates (token/embedding similarity, optionally narrowed by the `muscle`/`equipment` hints) — **never the full catalog**. The model must return **one shortlisted id or "none"** — never an invented id. **Validate the chosen id against the discriminator gate**; on conflict, reject it. Mark `matchSource: "llm"` and emit it to `fedb-06` for audit (these are the highest-risk matches).
   - **Offline / model unavailable:** return null now (→ placeholder) and **queue the name for LLM resolution when connectivity returns**, then re-bind on success. Offline-first behavior is preserved — the image just fills in later.
6. If the LLM returns "none" (or its pick is gated out) → **return null**.

## Discriminator gate — a conflict on ANY of these disqualifies the candidate (disqualify, do not down-weight)
`incline / decline / flat` · `close / wide / neutral / reverse grip` · `barbell / dumbbell / cable / machine / smith / bodyweight / band` · `single-arm / unilateral / alternate` · `seated / standing` · `front / back` · `pronated / supinated / hammer`.

## Acceptance
- [ ] Positive set (abbreviated & reworded variants, e.g. "DB Bicep Curl", "BB Bent-Over Row", "RDL") resolves to the correct ids.
- [ ] **Hard-negative set never cross-resolves:** Incline vs Decline, Close- vs Wide-grip, Barbell vs Dumbbell variants of the same lift each return the correct id or null — never the wrong sibling.
- [ ] When a `muscle`/`equipment` hint is supplied, it only ever **breaks ties among name-similar candidates** — it never causes a match where the name alone wouldn't.
- [ ] An unrecognizable name returns null (not a muscle-group guess).
- [ ] When deterministic steps (1–4) miss, the LLM fallback runs against a **shortlist** and returns a shortlisted id or null — never an invented id; its pick passes the discriminator gate or is rejected.
- [ ] Offline, an unmatched name returns a placeholder immediately and is **queued** for LLM resolution when back online.
- [ ] Resolution emits unmatched / low-confidence / LLM events to `fedb-06`; the deterministic steps are pure, the LLM step is an explicit async fallback.

## Constraints
- Prefer null over a wrong match, always.
- Tolerate null `equipment`/`mechanic` on entries — a null attribute is "unknown", never a disqualifier.
- LLM-sourced matches are the **highest-risk** category — always log them for audit (`fedb-06`), keep the LLM constrained to the shortlist with "none" allowed, and never let it return an id outside the shortlist.
