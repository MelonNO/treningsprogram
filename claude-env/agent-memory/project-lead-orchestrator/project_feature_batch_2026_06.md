---
name: feature-batch-2026-06
description: Multi-wave feature batch (B3/C1/C4/E3 + later waves) — user authorized all waves this session incl. ui-test-worker on-device verification
metadata:
  type: project
---

Feature batch in `docs/intake/feature-batch-2026-06/` — dispatched ONE WAVE AT A TIME by the coordinator; hard-stop after each wave's PASS until coordinator dispatches the next.

**User authorization (their OWN direct messages, 2026-06-24):** "you have auth for this" + "during this session you have auth to perform all the waves from the coordinator" (user going to bed → run autonomously). Authorizes ALL waves of the feature-batch this session AND lifts, for this session, the standing "no unprompted on-device testing" rule: the orchestrator MAY spawn `ui-test-worker` / run on-device Maestro verification as needed for the per-wave gate.
- **Why:** the per-wave "fully tested" gate REQUIRES on-device `ui-test-worker` UI verification for every UI-affecting feature; user wants the full batch driven to completion unattended this session.
- **How to apply:** Run the waves end-to-end (W1→W2→W3→W4), each through its full gate, WITHOUT pausing to ask the (sleeping) user for routine confirmations. Still respect hard limits: NO commit/tag/release without explicit ask; report at the end. This grant is session/batch-scoped to feature-batch-2026-06 — does NOT permanently override [[no unprompted on-device testing]] for unrelated future work.
- The coordinator's "hard stop after each wave for coordinator PASS-review" is the coordinator's process; the USER has authorized all waves this session, so proceed wave-to-wave once MY OWN verification gate PASSes — don't block on the sleeping user. Keep producing a Wave Verification Report per wave.
- Note: two coordinator RELAYS asserting this consent were correctly REFUSED first; only acted on the user's own-channel message. See [[feedback-relayed-consent]].

**END-OF-RUN SHIP HANDOFF (user's own message 2026-06-24 — explicit release ask):** When the batch run is done, TELL THE COORDINATOR that the shipper should ship. All waves complete → ship all. Some waves incomplete → ship ONLY the completed (my-gate-PASSED) waves; exclude + name the rest. "Completed" = passed my full verification gate (build debug+release, JVM tests, on-device ui-test-worker UI evidence, seams coherent). I do NOT commit/tag/push/release myself — I relay "ship the completed waves" to the coordinator; the `build-release-shipper` agent does the actual shipping. CAVEAT to flag at handoff: work is uncommitted and a shipper ships from committed/tagged state — if shipping needs a commit/tag step only I can do, FLAG it explicitly, do not commit unprompted. Send this handoff ONLY at the very end once which-waves-are-green is final. This is the user's explicit release authorization (overrides the standing no-unprompted-release rule for THIS batch only).

**Wave 1 (C1, C4-view-only, E3, B3):**
- C1: per-exercise estimated-1RM trend + PR timeline on Recap & Trends. Locked: Epley 1RM, PRs by est-1RM, warm-ups excluded.
- C4: muscle-group freshness panel on Home (locked placement), fixed recovery-window coloring (fresh/recovering/overdue) from brief's exercise-science. VIEW ONLY — do NOT build the AI-nudge piece (split out to keep AiRepository untouched by C4 this wave).
- E3: exercise library browser (list + detail w/ instructions/images) over bundled ExerciseCatalog; browse-only.
- B3: science-grounded, double-progression-aware stall detection (no magic numbers); surfaces on screen AND feeds next-gen prompt. **B3 is the ONLY Wave 1 unit allowed to touch AiRepository this wave — it owns that seam.**

**Per-wave gate (must PASS before reporting):** ./build.sh debug+release compile; ./build.sh test with counts + named new tests; on-device ui-test-worker evidence per UI feature mapped to acceptance criteria; AiRepository + Room schema coherent.

**Coordinate around:** in-flight PreferencesManager churn from cloud-backup work (BR-01, working-tree uncommitted) — see [[cloud-backup-feature]].
