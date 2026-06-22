# fedb-03 · U-BIND — write-time binding

**Role:** Resolve and store the DB id at the moments exercises enter the app — never at render.

**Depends on:** `fedb-02` (resolver).

## Scope / deliver
- Hook `resolve(name, hints)` into **every creation path** where an exercise enters the app: AI plan-generation output, user-added custom exercises, and any import.
- Write `exerciseDbId` (or null) + `matchConfidence` + `matchSource` + `resolvedAt` onto the record **at creation**.
- Pass any known `muscle`/`equipment` for the prescribed exercise into the resolver as `hints` (disambiguation only).
- Re-resolution happens **only on explicit demand** (e.g. after an alias-table update, or when a deferred offline LLM resolution completes — both via `fedb-06`), never per render.

## Acceptance
- [ ] A newly generated plan has every exercise's `exerciseDbId` (or explicit null) populated at creation, with `matchSource` recorded.
- [ ] A user-entered custom exercise also passes through resolution and stores its result (or null) — and remains fully usable if null.
- [ ] No resolution call occurs during rendering (verify render path does not call `resolve`).
- [ ] An alias-table update can trigger targeted re-resolution of affected records.
- [ ] A name deferred by the offline LLM fallback re-binds automatically when its resolution later succeeds.

## Constraints
- Binding must be **non-blocking**: if resolution returns null, the exercise is created and usable exactly as before — no errors, no missing-data states beyond a null id.
- Do not alter exercise names or selection based on resolution. Resolution only attaches an image id.
