# fedb-01 · U-DATA — bundle dataset, catalog, query & image source

**Role:** Prerequisite. Make the dataset part of the app and queryable, and provide the image source.

## Scope / deliver
- **Bundle the dataset into the app** (default hosting mode): include the combined JSON and the full `exercises/` image tree as app assets, so everything works offline. (Shipped variant: bundle the JSON, leave images to lazy-load + disk-cache — implement `getImageSource` so the caller is agnostic.)
- Load the JSON into a local **catalog keyed by `id`**.
- Precompute, per entry, a **normalized name** and a **token set** (for the resolver, `fedb-02`).
- Implement **`getImageSource(dbId, frame)`** returning a local bundled asset path (default) or cached/remote (shipped variant).
- Implement **`queryCatalog({ muscle?, equipment?, level?, category? })`** returning matching entries, so later features (alternatives/search) can use the catalog without re-parsing. *(Building those features is out of scope here — only expose the query surface.)*

## Acceptance
- [ ] All 800+ entries load; each exposes id, name, normalized name, token set, muscles, equipment (may be null), level, and image references.
- [ ] In the default mode, images resolve from **bundled assets with no network** (verify in airplane mode).
- [ ] `getImageSource(id, 0)` returns a valid source for a sample of entries.
- [ ] `queryCatalog({ muscle: "chest" })` returns the expected set; `queryCatalog({ equipment: "dumbbell", level: "beginner" })` filters correctly (tolerate null fields — see constraints).

## Constraints
- Public domain (Unlicense) — bundling/redistribution is allowed.
- `force`, `mechanic`, `equipment` are **null in some entries**; `queryCatalog` must not crash on nulls and should treat null as "unknown", not "no match".
- A few duplicate images exist (cosmetic) — no action needed.
- Do not deduplicate, rename, or prune exercises in a way that breaks the `id`→image mapping.

## Reference
- JSON: `https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json`
- Images: `https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/<path>` (e.g. `Air_Bike/0.jpg`)
