---
name: project-ux-refine-batch-2026-07-03
description: 10-item UI/UX refinement batch shipped as v1.25.1 (2026-07-04); polish after the 5-release feature sprint
metadata: 
  node_type: memory
  type: project
  originSessionId: 79df2a8e-3079-402a-be62-9e4844ca67b9
---

**SHIPPED v1.25.1 (2026-07-04, release commit/tag c00d9ac on main, GitHub release id 348956918, asset treningsprogram-v1.25.1.apk verified live, versionCode 66, DB unchanged v19).** Self-directed UI/UX refinement round (user: "do some more ui ux refinements you control the method" → understander audited + wrote all briefs directly, no confirmation round). Briefs: `docs/intake/ux-refine-2026-07-03/` (INDEX + 10 briefs, assumptions A-XXx). No schema change; polish only.

Items: H1 Home action-first order · H2 collapsible body-weight card · H3 hide-empty recovery card · H4 weigh-in delete undo (snackbar) · L1 log spec-chip wrap on small screens · C2 emoji→vector icons on log/timer (ic_close/fullscreen/pause/timer; keypad + − ✓ kept) · W1 Wrapped persistent close · P1 week-strip sw360dp dimens · C1 dot-less in-card eyebrows (8 hero bands keep dot; `Widget.Auros.Eyebrow` carried the dot since v1.19.0) · C3 unified empty-state voice. 1832 tests both variants (0 fail); signed build clean.

**Why (process, notable):** this batch was brutal on agents — the Anthropic API/harness was unstable and ~SEVEN orchestrator handoffs happened: session limits, two connection drops, a SendMessage-resume that dropped the `model:opus` override → died on exhausted Fable ([[coordinator-background-agent-ops]] §6), and finally usage-credit exhaustion even on fresh Opus dispatches. **Commit-first instructions saved it** — each death cost only the in-flight item, never the batch. **The ship itself was executed FROM THE COORDINATOR/MAIN SESSION** (tag/push/GitHub-release/upload/verify via git+curl) because all release subagents were credit-dead; justified by explicit user "Ship it now" + only mechanical steps remaining + APK already built. A one-off exception to [[feedback_orchestrator_owns_changes]], not a new norm.
**How to apply:** GitHub asset upload of the ~100 MB APK exceeds the Bash 2-min tool default — run it `run_in_background` (or a long tool timeout), and if an upload is cut off it leaves a `state:"starter"` partial asset that must be DELETEd before re-upload (don't just retry — you'd get a dup/conflict). On-device checks are the user's (keypad tap-outside still needs a real-device confirm per the earlier Waydroid caveat).
