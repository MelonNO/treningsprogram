# fedb-05 · U-BACKFILL — resolve existing data + audit

**Role:** Bind everything already in the app and surface what needs human attention.

**Depends on:** `fedb-03` (binding), `fedb-02` (resolver).

## Scope / deliver
- Run the resolver over **all existing** plan / history / custom exercise names; populate `exerciseDbId` (or explicit null) on each.
- Produce a **report** listing: (a) unmatched names, (b) low-confidence / fuzzy matches.
- Provide a way to correct entries and **seed the alias table** (`fedb-06`) from this report.

## Acceptance
- [ ] Every existing exercise record has an `exerciseDbId` or explicit null after the run.
- [ ] A miss / low-confidence report exists and has been reviewed; obvious aliases added.
- [ ] Fuzzy matches have been **spot-audited** — confirm no silently-wrong images (especially the discriminator categories from `fedb-02`).
- [ ] Re-running the backfill is idempotent (doesn't duplicate or churn already-correct bindings).

## Constraints
- Backfill must not modify exercise **names** or selection — only attach/clear image ids.
- Treat the audit as mandatory: an unaudited fuzzy match is not "done".
