---
name: project-bug-sweep-ux-batch-2026-06
description: Confirmed app-wide bug-sweep + UX-improvement intake batch (post-v1.8.0) at docs/intake/bug-sweep-ux-2026-06/ — scope, two-phase sequence, and the achievements-fix reversal
metadata:
  type: project
---

Confirmed intake batch produced 2026-06-24 at `docs/intake/bug-sweep-ux-2026-06/` (INDEX.md, SEQUENCE.md, 8 sweep briefs S1–S8, 3 fix briefs F1/F2/F3, 1 UX brief UX1, plus a follow-up **concrete user-tested track U1–U3**). User's direct go-aheads: "seems good go ahead" (main batch) and "Go with the understanders reccomendation combine them" (the U-items).

**Concrete user-tested items (from real-device testing, folded into this batch as a separate track):**
- **U1 — recovery view rework (Phase 1; SW-C):** (#1) weighted secondary-muscle model — different exercises affect different muscles to different degrees; (#3) finer muscle taxonomy (upper/lower back, front/side/rear delts, biceps/triceps split from Arms, quads/hamstrings/glutes/calves split from Legs); (#2) show ONLY recovering muscles (hide rested/ready/untrained) with remaining-recovery indication; (#4) tap a recovering muscle → opens the last session that trained it (NOT a new/generated workout). Builder owns the exact taxonomy + per-exercise weights.
- **U2 — XP log (Phase 2; gamification/S6):** tap XP bar → XP history; records FORWARD ONLY (no backfill).
- **U3 — Home reorder (Phase 2 or alongside S1; reorder only):** top→bottom XP bar → Weekly Challenge → Today's Plan → Body weight → Muscle recovery → Recent workout. Body-weight widget already exists on Home.
- **#7 (richer Recap visuals/more graphs):** MERGED into UX1, not a separate brief; graph set = understander/orchestrator best judgment.
- **#5 (remove AI coach summary / B1): CANCELLED by the user — B1 is KEPT; S3 retains B1 coverage unchanged.** Note for future intake: B1 stays in the app; do not treat it as slated for removal.

Current C4/MuscleRecovery baseline (pre-U1, for context): 7 broad groups (Chest/Back/Legs/Shoulders/Arms/Core/Glutes), time-since-last-trained only, no secondary-muscle attribution; has an UNTRAINED state. The app tracks total XP in UserStats (surfaced on Profile) but had NO per-event XP history (U2 adds it).

**Why:** post-v1.8.0 (8 features shipped overnight, per-wave verified but never adversarially bug-swept) the user wanted a large app-wide bug-search-and-fix pass PLUS UX improvements.

**Locked scope:** WHOLE APP; BOTH on-device adversarial testing AND code review; ALL severity tiers (crashes→minor polish); EXHAUSTIVE; diagnose-first; UX weighted to the user's named worst-feeling flows = **Recap, Progress, History**; no don't-touch zones.

**Key decisions baked in:**
- Two-phase: **bugs-first (Phase 1) → UX-second (Phase 2)**, one INDEX.
- PR-truth (P-3): **retire/replace the legacy warm-up-counting PR widget** with the correct C1 estimated-1RM logic (F2).
- **Achievements >200 / orphan rows: FIX NOW (F1)** — this REVERSES the prior "don't apply" recorded in the user's auto-memory [[project-achievements-orphan-rows]] (was 2026-06-23). Future intake: the achievements-count fix is now authorized/expected, not deferred.
- AI 120s OkHttp timeout / silent-hang: **FIX as first-class item (F3, =P-1)** — the resilience seam (timeout+retry+clear failure UX) spans every AI flow; F3 lives on the hot `AiRepository`/network seam and must be sequenced before S3.

**How to apply:** if the user revisits any of these, this batch is the authoritative scope. NOT yet dispatched to orchestrator — handoff is a separate later user instruction. The achievements-fix reversal is the load-bearing fact to carry forward.

See [[feature-backup-export]] for the related backup/export gaps that S7 surfaces (backup-set expansion left as a deferred decision).
