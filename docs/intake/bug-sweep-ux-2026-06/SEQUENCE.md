# Build Sequence — Bug Sweep + UX Improvements (2026-06)

**Prepared for:** Project-lead orchestrator / coordinator.
**This is the ordering contract.** The INDEX is the analysis; this file is the enforceable sequence. Dispatch one wave at a time; do not start a wave until the prior wave's verification gate has passed.

**Stance for all of Phase 1:** **diagnose first** — every confirmed defect is root-caused before any fix; no symptom-masking. Severity bar is **all tiers** (crashes/data-loss, correctness, visual, polish). Breadth is **whole app**. Method is **both** code review **and** adversarial on-device testing.

---

## Phase 1 — Bug sweep & fixes + recovery rework

Phase 1 is organized by cluster (see INDEX). The parallel-safe clusters can run concurrently; SW-B is sequenced internally and is the critical path.

**Note on #5 (remove AI coach summary):** CANCELLED by the user. There is **no B1-removal item**; **S3 keeps its B1 coach-summary coverage unchanged**.

### Wave 1 — Resilience + recovery rework + parallel-safe sweeps (start together)

- **SW-B step 1: F3 (AI timeout/retry resilience)** — land FIRST within SW-B. It defines the network timeout/retry behavior that the S3 sweep then verifies. Touches the shared `AiRepository`/network seam — no other AI-touching work runs concurrently with it.
- **SW-A: S1 (Home + logging)** — parallel-safe, run now. **NOTE:** S1 must verify the **final Home** — i.e. after **U3 (reorder)** and **U1 (recovery panel rework)**. Sequence so S1 verifies the reworked Home, not the old layout (U3's reorder and U1's panel can land before/with S1's Home pass; if convenient, do U1+U3's Home changes first, then S1 sweeps the result).
- **SW-C: S4, S5, S6, F1, F2, U1** — parallel-safe cluster, run now. Internal coordination: **F2 ↔ S4** (same Stats screens), **F1 ↔ S6** (achievements), and **U1 supersedes S5's recovery portion** — **build U1 (recovery rework) before S5/S1 verify recovery**, so the sweeps validate the *reworked* behavior, not the old C4 view. Within SW-C this can be one worker working through them in a sensible order, or split with explicit coordination on the shared surfaces.
- **SW-D: S7, S8** — parallel-safe, run now. S8 (lifecycle) is cross-cutting; its findings are de-duplicated against per-screen sweeps at integration.

**Why U1 is in Phase 1:** it is substantial feature/model work (weighted secondary-muscle model + finer taxonomy + recovering-only display + tap→last-session), not polish. Landing it in Wave 1 means the recovery-touching sweeps (S5, S1) verify the final behavior rather than sweeping behavior that is about to change.

**Gate (Wave 1 → Wave 2):** F3 landed and verified on-device (incl. the previously-failing E2 live-deload path completing or failing gracefully with retry). **U1 (recovery rework) implemented and verified on-device** (weighted secondary muscles affect recovery per-exercise; finer taxonomy shown; only recovering muscles shown with remaining-recovery indication; tapping a recovering muscle opens its last session). SW-A / SW-C / SW-D sweeps have produced their findings, fixes diagnosed-first and applied, and **on-device verification evidence** (rotation, backgrounding/restore, process-death, empty/first-run, no-network, no-API-key, rapid taps) recorded per affected surface. A **Wave Verification Report** with confirmed PASS is required.

### Wave 2 — Program & generation sweep (SW-B remainder)

- **SW-B step 2: S3 (AI generation/adaptation/coaching)** — runs after F3, rebasing on F3's network changes; verifies the failure modes end in clear user-visible states.
- **SW-B step 3: S2 (Program tab)** — runs alongside/after S3 within the same cluster (shared `WorkoutRepository`/Program tab). The deload path couples to stall logic exercised in S6 — coordinate the stall convention with SW-C's S6 work from Wave 1.

**Why SW-B is its own wave:** it is the hottest seam (`AiRepository`) and the heaviest logic; running it after Wave 1's parallel sweeps keeps the shared seam from being edited blind by multiple workers.

**Gate (Wave 2 → Phase 2):** S2 and S3 findings diagnosed-first and fixed; generation/adaptation/coach-summary/rationale/deload exercised on-device through the adversarial states with recorded evidence; no silent hang or silent data loss remains in any AI flow. **Wave Verification Report** with confirmed PASS.

### Phase 1 completion gate

All S* sweeps, F1/F2/F3, and **U1** are diagnosed-first/built, fixed, and **verified on-device**, with findings recorded against the all-tiers bar and de-duplicated across the cross-cutting S8 pass. The app is in a **stable, verified state**. Only then does Phase 2 begin.

---

## Phase 2 — UX improvements (UX1) + confirmed UX additions (U2, U3)

Runs only after the Phase 1 completion gate passes (polish lands on a stabilized, correct app).

### Wave 3 — UX proposal → user approval (UX1 only)

- The understander/orchestrator presents a **prioritized UX item list** (brief UX1, which **absorbs user-tested #7 — richer Recap visuals/more graphs**), grounded in Phase-1 on-device findings, weighted to **Recap / Progress / History**, each tagged MUST-HAVE candidate / NICE-TO-HAVE.
- **GATE — user approves/cuts each item.** No UX1 item is implemented until the user picks it. This is a hard stop: the user authorized the *batch shape*, not a blanket green light on the open-ended UX1 changes.
- **Scope note:** this gate applies to **UX1 only**. **U2 (XP log) and U3 (Home reorder) are already-confirmed concrete user items** — they are **not** subject to this approve/cut gate and proceed to Wave 4/5 as specified.

### Wave 4 — UX1 implementation (approved items only)

- Build only the user-approved UX1 items. Verify each on-device; ensure no Phase-1 fix is regressed and the Stats/History area tells one coherent story.

### Wave 5 — Confirmed concrete UX additions (U2, U3)

- **U2 (XP log):** build the XP-event capture (forward-recording only) + tappable XP history. Attaches to the gamification surface (coordinate with S6's verified XP/level math). Verify on-device, incl. the empty/first-run state before any events accrue.
- **U3 (Home reorder):** apply the fixed top→bottom order (XP bar → Weekly Challenge → Today's Plan → Body weight → Muscle recovery → Recent workout). If not already applied alongside S1 in Phase 1, do it here; verify all sections still function. (U3 may instead be done in Phase 1 alongside S1's Home pass — either is acceptable as long as S1 verifies the final order.)

Waves 4 and 5 are largely independent (different surfaces) and may run in parallel.

**Gate (Phase 2 done):** each approved UX1 item plus U2 and U3 delivers its stated outcome with on-device evidence; no regression of Phase-1 correctness. **Wave Verification Report** with confirmed PASS.

---

## Critical path & hazards (carry forward from INDEX)

- **Critical path:** **SW-B** (F3 → S3 → S2). It owns the hottest seam and the heaviest logic. Everything else parallelizes around it.
- **Never edit `AiRepository` blind concurrently** — F3 and S3 are sequenced for exactly this reason; C4-nudge/B2/B3-style prompt edits from prior work also live on this seam.
- **F1 ↔ S6:** pruning orphan rows (F1) must not disturb valid unlock state (S6 verifies).
- **F2 ↔ S4:** legacy PR-widget retirement and the Stats sweep edit the same screens — coordinate.
- **S7 ↔ in-flight cloud backup:** coordinate on `PreferencesManager`/`ExportRepository`.
- **S8 (lifecycle)** overlaps every surface — run as a cross-cutting pass; de-duplicate findings at integration.
- **U1 ↔ S5/S1 (recovery rework):** U1 supersedes the old C4 recovery behavior — **build U1 before S5/S1 verify recovery** so the sweeps validate the reworked view. If U1's weighted model feeds the AI prompt, it joins the `AiRepository` seam (coordinate with F3/S3).
- **U3 ↔ S1 (Home):** the Home reorder must be reflected when S1 sweeps Home — apply U3 before/with S1's Home pass.
- **U2 ↔ S6 (XP):** U2's new XP-event capture must not disturb the XP/level math S6 verifies — coordinate; U2 builds in Phase 2 after S6 is settled.

## Standing constraints

- Build via `./build.sh`; no commits/releases unless the user explicitly asks.
- On-device UI verification is **required** for every UI-affecting fix/feature before its wave passes (the user's "everything / as thorough as possible" instruction satisfies the usual opt-in for on-device testing — scoped to this sweep only).
- Diagnose before fixing, every time.
