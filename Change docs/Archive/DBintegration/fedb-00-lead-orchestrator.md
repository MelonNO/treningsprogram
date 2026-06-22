# free-exercise-db Image Integration — LEAD / Orchestrator Brief

**Addressed to: the LEAD agent.** Integrate exercise images from the bundled free-exercise-db dataset by **freezing shared contracts, dispatching the six sub-agents (`fedb-01`…`fedb-06`), integrating their work, and verifying a complete, working, offline integration end-to-end.** You are done only when every unit is verified and the end-to-end acceptance tests pass.

---

## Hard constraints (never violate)
1. **Do NOT constrain exercise selection to the DB.** The AI and user remain free to use any exercise name. The DB is an **image source only**, never a gate on what exercises exist.
2. **Images are additive.** A missing/unmatched image must never block, hide, rename, or alter an exercise. Logging, stats, swaps, history, and the workout flow work identically with or without an image.
3. **Never show a wrong image.** A confidently-wrong near-duplicate (e.g. Incline shown for Decline) is worse than no image. When uncertain → placeholder.
4. **Attributes narrow; the name identifies.** Muscle group / equipment / mechanic are **disambiguators only** — used to choose among *name-similar* candidates. They must **never** be used to pick an image for a named exercise on their own. **Name-fail → placeholder, never a "best muscle-group match."**
5. **Resolve once at write time; render by id.** No name-matching at render time.

---

## Hosting decision (default chosen — confirm before fedb-01)
- **Default (personal app): bundle the ENTIRE dataset — the JSON and all images — into the app.** Fully offline, self-contained, no network dependency. The dataset is public domain, so bundling/redistribution is allowed. (~100 MB, dominated by images; the JSON is small.)
- **Shipped variant (only if distributing):** bundle the JSON (small) for offline search/metadata; **lazy-load + disk-cache images** on first use so app size stays small. Resolution: the deterministic steps (exact/alias/fuzzy) are fully offline (the JSON is always bundled); the **LLM fallback needs connectivity and defers gracefully when offline** (placeholder now, re-bind when online).

---

## Agent structure
- **LEAD (you):** freeze contracts (Step 0) → dispatch sub-agents → integrate → run end-to-end verification → maintain the completion ledger → final report. Maximize parallelism where contracts allow; serialize on dependency/file overlap.
- **Sub-agents:** one per work unit; each receives its `fedb-0N` brief.

---

## Step 0 — freeze shared contracts (before dispatch)
- **Exercise record additions** (nullable so unmatched exercises stay valid): `exerciseDbId: string|null`, `matchConfidence: number|null`, `matchSource: "exact"|"alias"|"fuzzy"|"llm"|"none"`, `resolvedAt: timestamp`.
- **Resolver interface:** `resolve(name, hints?: { muscle?, equipment? }) -> { id, confidence, source } | null`. The optional hints carry any muscle-group/equipment the app already knows for the prescribed exercise (used only as disambiguators).
- **Image source builder:** `getImageSource(dbId, frame: 0|1) -> local asset path` (bundled) or cached/remote (shipped variant). Abstract this so render doesn't care which hosting mode is active.
- **Placeholder contract:** one neutral placeholder used whenever `exerciseDbId` is null or an image fails to load.
- **Catalog query interface** (so later features can search without re-parsing): `queryCatalog({ muscle?, equipment?, level?, ... }) -> entries[]`.

---

## Work units → sub-agent briefs
- `fedb-01` **U-DATA** — bundle dataset, build local catalog + query + image source. *(prerequisite)*
- `fedb-02` **U-RESOLVE** — name→id resolver with attribute disambiguation. *(dep: 01)*
- `fedb-03` **U-BIND** — write-time binding hooks. *(dep: 02)*
- `fedb-04` **U-RENDER** — render by id + graceful fallback. *(dep: 01 + Step 0; parallel with 02)*
- `fedb-05` **U-BACKFILL** — resolve existing data + audit. *(dep: 03)*
- `fedb-06` **U-LOG** — logging, metrics, curation loop. *(cross-cutting)*

## Dependency & parallel plan
1. **U-DATA** first.
2. After contracts frozen: **U-RESOLVE** ∥ **U-RENDER**.
3. **U-BIND** after U-RESOLVE; **U-LOG** alongside.
4. **U-BACKFILL** after U-BIND.

---

## Completion enforcement
Maintain a ledger: each unit `not-started → in-progress → implemented → VERIFIED` or `BLOCKED (reason)`. Do not stop while anything is unverified. A unit is **VERIFIED** only when its acceptance criteria pass under real execution.

**End-to-end acceptance (all must pass):**
- [ ] App runs fully offline; bundled images display for common lifts with no network.
- [ ] Generate a plan containing common lifts **and a deliberately off-DB / invented exercise** → common lifts show correct images; the invented one shows the placeholder and is fully usable.
- [ ] Hard-negative set (Incline vs Decline, Close- vs Wide-grip, Barbell vs Dumbbell of the same lift) **never cross-resolves** — correct id or placeholder, never the wrong sibling.
- [ ] A name that doesn't resolve produces a **placeholder**, never a muscle-group-substituted image.
- [ ] When deterministic steps miss, the LLM fallback attempts a shortlist-constrained match; **offline**, the name shows a placeholder and is queued, then re-binds when back online.
- [ ] No name-matching at render time; all bindings persisted at write time.
- [ ] Backfill complete; miss/low-confidence report reviewed; fuzzy matches audited.
- [ ] Adding an alias re-binds and fixes a previously-unmatched exercise.
- [ ] **Exercise selection is unchanged** — nothing in this integration restricts which exercises can be created or prescribed.

**Final report:** ledger (all VERIFIED or BLOCKED+reason), hosting mode used, current match rate, residual risks, new dependencies.

---

## Decisions (resolve up front; defaults below)
- **Hosting:** default = bundle everything (personal). Switch to JSON+lazy-images only if distributing.
- **LLM fallback (resolver step 5): ON.** Runs whenever the deterministic steps find no match, before returning null. Constrained to a shortlist, may return "none", its pick is discriminator-gated, and offline it defers (placeholder now → re-bind when online). LLM-sourced matches are logged for audit as the highest-risk category.
- **Placeholder style:** single neutral asset, consistent with app visuals.

## Reference facts (verified)
- **free-exercise-db** (yuhonas), public domain (Unlicense), 800+ exercises, two images each (`/0.jpg`, `/1.jpg`).
- Combined JSON: `https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json`
- Image prefix (if not bundling): `https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/<path>`
- Per-entry schema: `id, name, force, level, mechanic, equipment, primaryMuscles[], secondaryMuscles[], instructions[], category, images[]`.
- Caveats: `force`, `mechanic`, `equipment` are **null in some entries**; a small number of duplicate images exist.
