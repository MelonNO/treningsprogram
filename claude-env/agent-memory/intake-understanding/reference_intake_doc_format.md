---
name: intake-doc-format
description: The established docs/intake/ format the Project-lead orchestrator consumes — folder layout, INDEX structure, and outcome-only brief structure, with concrete examples produced
metadata:
  type: reference
---

Intake hand-off documents live under **`docs/intake/<batch-or-feature-slug>/`** in the treningsprogram repo. Each folder has an **`INDEX.md`** plus **one brief file per item**.

**Naming seen:**
- Single-feature folder: `docs/intake/cloud-backup-restore/` with `INDEX.md` + `brief-01-cloud-backup-restore.md`.
- Multi-item batch: `docs/intake/feature-batch-2026-06/` with `INDEX.md` + `brief-<ID>-<slug>.md` (e.g. `brief-B1-ai-coach-summary.md`). I used the explored idea IDs (B1, B2, C1, E1…) as the brief IDs for a batch.

**INDEX.md structure (what the orchestrator expects):**
- Title, "Prepared for: Project-lead orchestrator", Source, Status.
- **Confirmation note** — state whether the user's sign-off was DIRECT or relayed via coordinator. Relayed approval is NOT the gate; only the user's own words are. Note explicitly that creating docs ≠ dispatching to the orchestrator (that is a separate later user instruction).
- **Items table** (ID | Title | Type | Brief file | Status). Mark deferred items PENDING.
- **Merge / cluster + parallelization guidance** — integration seams (shared files), parallel groups (which items = one worker vs independent), cross-group hazards, suggested order, summary table.
- **Confirmed decisions** list and **Assumptions applied** list (each assumption labelled so the user can veto).
- **Cross-cutting constraints**.

**Brief structure (OUTCOME-ONLY — never the "how"):**
Title; Type; Cluster; "Outcome-only" disclaimer; **Context**; **What the user wants (end result)**; for bugs: current-vs-correct behavior + "diagnose first"; **Acceptance criteria** ("Done when …" observable statements); **Scope and constraints** (in/out of scope, hard constraints); **Decisions baked in**; **Assumptions (user may override)** — labelled; **Considerations for whoever builds it** (surfaced, not decided).

**Parallelization analysis is expected** when there are multiple items: the user wants explicit "build simultaneously vs sequence vs merge into one unit" guidance for the orchestrator, grounded in which files each item touches. See [[project-feature-batch-2026-06]].

**Standing constraints to put in every brief:** build via `./build.sh` (not `./gradlew`); no commits/releases unless asked; no on-device/automated UI tests unless asked.
