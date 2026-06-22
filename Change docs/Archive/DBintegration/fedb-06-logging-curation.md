# fedb-06 · U-LOG — logging, metrics & curation loop

**Role:** Make image coverage improve over time **without** constraining exercise selection. Because the app does not limit exercises to the DB, misses are expected and normal — this loop is how they get fixed.

**Cross-cutting** (consumes events from `fedb-02`/`fedb-03`, feeds `fedb-05`).

## Scope / deliver
- Capture every **unmatched name** and every **low-confidence match** to a place the user can review, with occurrence counts (so the most common misses surface first).
- Track an overall **match rate** metric.
- Make adding an **alias** (variant name → DB id) a **one-step action** that triggers targeted re-resolution of the affected exercise records, so they then render correctly.
- Maintain a **retry queue** for names deferred by the offline LLM fallback; on reconnect, resolve and re-bind them.
- Surface **LLM-sourced matches** (`matchSource: "llm"`) prominently for review — they are the highest-risk matches and should be audited like fuzzy ones.

## Acceptance
- [ ] Unmatched / low-confidence names are captured with counts and are reviewable.
- [ ] A current match-rate figure is available.
- [ ] Adding an alias re-binds the affected exercises and they then show the correct image — verified on at least one previously-unmatched exercise.
- [ ] Names deferred while offline are retried and re-bound when connectivity returns.
- [ ] LLM-sourced matches are listed for audit.

## Constraints
- Logging must be lightweight and must not block resolution or binding.
- The alias table is the primary, most reliable coverage tool — make it trivial to extend; this is intended to be used continuously.
