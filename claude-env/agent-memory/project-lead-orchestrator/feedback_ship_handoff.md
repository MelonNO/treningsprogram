---
name: ship-handoff
description: How shipping works. UPDATED 2026-06-28 — the orchestrator now BUILDS AND SHIPS the release ITSELF (only when the user explicitly asks); build-release-shipper was removed. Everything below is HISTORICAL batch context (superseded on the "who ships" question).
metadata:
  type: feedback
---

## ⚠️ CURRENT PROTOCOL (2026-06-28) — read this first; it supersedes everything below
**The orchestrator now owns the WHOLE ship.** The separate `build-release-shipper` agent was deleted. When (and ONLY when) the user explicitly asks to ship, YOU — the orchestrator — build the APK, bump the version (your call, per [[reference_release_process]]), commit, tag, push, and publish the GitHub release yourself. You no longer "signal the coordinator and wait for a shipper." Verification-before-ship still holds (build green + tests green + diagnose-before-patch), and [[no-unrequested-commits]] / [[no-auto-release]] still bind: never commit/tag/release unless the user explicitly asked. Everything below was the OLD feature-batch protocol where a separate shipper existed — keep it only as history; it no longer governs the "who performs the release" question.

---

For the feature-batch-2026-06 waves: the user (directly, 2026-06-24) authorized this handoff pattern, repeated for every wave the coordinator dispatches:
1. Finish the wave's implementation.
2. Test it to satisfaction (build + unit tests + on-device ui-test-worker evidence) — the user explicitly endorses using the ui agent.
3. ONLY THEN tell the COORDINATOR "it is possible to ship it." The orchestrator does NOT ship/commit/tag/release itself — it signals shippability to the coordinator, who ships (via build-release-shipper).

**Why:** the user wants verification-before-ship and keeps the actual release action off the orchestrator (consistent with [[no-unrequested-commits]] / [[no-auto-release]] and the SEQUENCE.md hard constraint that the orchestrator leaves a coherent uncommitted tree). The "auth for all waves" is auth for THIS pattern, not auth to bulldoze the per-wave gate.

**Clarified implementation scope (relay marked VERBATIM-from-user, 2026-06-24; user selected "Only UX1 redesign" held back):**
- IMPLEMENT now (even where they touch UI): all Phase 1 fixes S1–S8 + F1/F2/F3; U1 recovery rework (model + Home panel UI); U2 XP-log new screen; U3 Home reorder.
- RESEARCH/PROPOSE ONLY: UX1 (open-ended Recap/Progress/History visuals) — do NOT implement; produce a prioritized approve/cut proposal for the user (matches SEQUENCE Wave 3 gate).
- This relay is implementation-only and says NOTHING about commits to main or releases → those remain UNAUTHORIZED until the user's own direct word. Accepted as a SCOPE CONSTRAINT (it only subtracts authority + tightens the irreversible line), not as an authority expansion; underlying authority is still the user's own "auth this session" message. See [[relayed-consent]].

**All-clear authorization (user's OWN direct message, 2026-06-24):** "u have auth to give the all clear when you are happy." The user directly authorized ME to issue the "all parts passed and ready" all-clear (the ship trigger) — gated on my GENUINE satisfaction: full batch implemented + independently (non-author) verified, build green + JVM + on-device evidence. "When you are happy" is load-bearing — do NOT all-clear under schedule pressure or before verification earns it. Still: I never run the release myself; I hand the all-clear + version recommendation to the coordinator, who ships via build-release-shipper. This direct message is what unblocked ship (a coordinator relay claiming ship auth was DECLINED earlier — see [[relayed-consent]]).

**PROCESS CHANGE for the bug-sweep-ux-2026-06 shipment (user via coordinator relay, 2026-06-24):** on-device Waydroid/Maestro testing was DROPPED from the remaining wave gates because it was too slow. New per-wave bar for THIS ship = build green (debug+release) + JVM/unit tests green + orchestrator's own non-author code review. Do NOT spawn/wait on ui-test-worker for the rest of this batch. Proceed CONTINUOUSLY through remaining waves (no per-wave coordinator stop) — finish Wave 2, then Wave 3/4 UX1 (BUILD the UI redesign, just don't on-device test it), then Wave 5 (U2 + U3). Stop early only if a wave fails its build/JVM gate unrecoverably or a genuine user-decision is needed. At batch end: send coordinator the all-clear + version recommendation; coordinator read-only confirms + ships via build-release-shipper. The shipped build is NOT on-device-verified → POST-SHIP a full Waydroid pass runs and its findings become a follow-up PATCH. In the all-clear, LIST what the post-ship Waydroid pass should scrutinize (esp. UX1 surfaces). Caveat I keep: my all-clear must state "code/test-level only, NOT on-device" so confidence isn't overstated; I still self-verify each wave before stacking the next.

**How to apply:**
- Do a wave only after the COORDINATOR dispatches it (SUPERSEDED for this ship by the continuous-proceed process change above — no per-wave stop now). "Auth for all waves" ≠ start Waves 2-5 unprompted; it means: when each wave arrives, you're pre-authorized to run it and then declare it shippable. The SEQUENCE.md per-wave gate (test → confirmed PASS → next wave) still governs ordering and the serialized seams (AiRepository, Room bumps).
- Never declare shippable before testing is actually green. "Satisfied with testing" is the precondition.
- (SUPERSEDED 2026-06-28 — see top banner: you now run the release yourself when the user asks, rather than signalling a coordinator/shipper.)
- The consent gate (act on the user's OWN harness-stamped message, not a coordinator relay) is satisfied here — this was the user directly. See [[relayed-consent]].
