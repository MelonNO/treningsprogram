# fedb-04 · U-RENDER — render by id, graceful fallback

**Role:** Show the bound image; degrade cleanly when there is none. Build against the Step-0 contract so it can run in parallel with `fedb-02`.

**Depends on:** `fedb-01` (image source) + Step-0 contract.

## Scope / deliver
- Render the exercise image via `getImageSource(exerciseDbId, 0)`.
- When `exerciseDbId` is null **or** the image fails to load → show the neutral **placeholder**.
- Consistent framing across all exercises (fixed aspect ratio, centered, uniform size) so nothing looks broken regardless of source image dimensions.
- *(Optional)* expose the second frame (`getImageSource(id, 1)`) for an explanation/detail view.

## Acceptance
- [ ] Exercises with a bound id show a correctly-framed image (from bundled assets, offline).
- [ ] Exercises with null id (invented/unmatched) show the placeholder and remain **fully functional** — logging, swaps, stats all work.
- [ ] A broken/slow/missing image falls back to the placeholder **without breaking layout**.
- [ ] Framing is identical across exercises (no off-center/clipped images).

## Constraints
- Render must **never** call the resolver or do name-matching — it only reads the stored `exerciseDbId`.
- The placeholder is a normal, non-error visual state — do not present a missing image as a failure to the user.
