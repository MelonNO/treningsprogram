---
name: agent-usage-guideline
description: How the user wants the agent fleet used — who launches, the default pipeline, and each agent's role/ownership boundary.
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 96287e1d-331c-452d-ba22-1e330643c597
---

How the user wants the multi-agent setup used on this project. Confirmed via direct Q&A on 2026-06-24.

**Why:** The user wants clear ownership boundaries between agents and a predictable default flow, with the coordinator strictly limited to coordination + relay.

## Who launches agents

**The coordinator (main Claude Code) has FULL CONTROL of the agents and spawns them according to the task** (changed 2026-06-24 — previously the user launched them). Pick the right agent for the work and dispatch it. The user may also launch agents directly through their own channel. The coordinator's job is to scope work, choose and spawn the right agents, **coordinate between the high-layer agents, and relay information**.

NOTE: full control over *spawning agents* does NOT loosen [[orchestrator-owns-changes]] — the coordinator still must NEVER code, edit, build, or test itself. It gets that work done by spawning the appropriate agent, not by doing it.

## Default pipeline (per change)

**Scaled to risk as of 2026-06-28 ([[orchestrator-owns-changes]]):** small/low-risk single-unit changes skip intake + ui-test and go as ONE orchestrator pass; only large/risky/ambiguous/multi-unit/schema/UI work runs the full chain below. Don't spin up the whole fleet for a one-liner.

1. **intake-understanding** — runs FIRST on EVERY feature/bugfix **by default, unless the user specifies otherwise** for that task. Clarifies scope, asks questions, proposes better solutions, and — only after user confirmation — produces outcome-only briefs + index for the orchestrator. Relay its output **verbatim**; forward the user's replies straight to it ([[intake-agent-verbatim-relay]]).
2. **project-lead-orchestrator** — **OWNS (is accountable for)** ALL implementation, file edits, builds, tests **(incl. the release/ship step as of 2026-06-28)** EXCEPT UI testing. **DEFAULT = he DOES THE WORK HIMSELF in one pass** (changed 2026-06-28 — was "primarily via sub-agents"). He spawns worker sub-agents ONLY when specifically warranted: (a) genuine parallelism across non-overlapping files large enough to pay for the overhead; (b) a unit needing isolation (own worktree/branch); or (c) independent second-party verification of a risky/large change. **Independent verification stays mandatory:** he NEVER accepts an unverified self-report — every result is confirmed by real build/test evidence (he runs the tests himself when he did the work; a separate test agent when a worker wrote it; `ui-test-worker` for the UI slice). Diagnose-before-patch; regression-free; unit/integration tests green. **Owns the version-number decision** (PATCH/MINOR/MAJOR per [[reference_release_process]]) — and now also owns the actual build + GitHub release (only when the user explicitly asks to ship).
3. **ui-test-worker** — owns on-device / Waydroid / Maestro **UI** verification ([[reference_ondevice_test_harness]]): authors/extends Maestro flows, runs them, verifies acceptance criteria with real evidence. **Tests and reports ONLY — never modifies app code.** The orchestrator spawns it when UI verification is warranted (NOTE: currently DORMANT by standing instruction — [[feedback_always_skip_waydroid]]). The coordinator must NEVER run on-device UI checks itself.
4. **(build-release-shipper — REMOVED 2026-06-28.)** The separate shipper agent is deleted; the orchestrator now builds, commits, tags, pushes, and publishes the GitHub release itself — ONLY when the user explicitly asks. Respects [[feedback_no_auto_release]], [[feedback_no_unrequested_commits]], [[reference_release_process]]; halts and reports if the build fails.

## Coordinator (me) — hard limits

Coordinate + relay only. NEVER code, edit, build, test, commit, or run the app — read-only actions only (Read/Grep/Glob, git status/diff/log, reading output the agents produced). See [[orchestrator-owns-changes]] for the absolute rule and the rationalizations that are explicitly NOT exceptions.

**Auth relay:** the coordinator MAY carry user-authorization messages both ways — the agent asks the user for auth (the coordinator relays the request), and the user grants/denies (or grants proactively; the coordinator relays it to the agent). But on anything auth-related the coordinator must be NOTHING more than a messenger: never transcribe/reword, assume, infer, or manufacture auth, and never carry over auth from a prior context. If unsure the user truly authorized the specific action, do not relay it as auth — ask. Applies to every agent needing user auth. See [[auth-relay-via-coordinator]].

## Utility / read-only agents (user-launched as needed)

- **Explore** — read-only fan-out search across many files; returns conclusions, not file dumps. Locates code; doesn't review it.
- **Plan** — read-only architecture/implementation planning; returns step-by-step plans and critical files.
- **general-purpose** — open-ended research and multi-step search when a match isn't certain in the first tries.
- **claude-code-guide** — questions about Claude Code (CLI), the Agent SDK, or the Claude/Anthropic API.
- **statusline-setup** — configures the Claude Code status line.
- **fork** — forks the coordinator with full context (background, keeps its tool output out of the coordinator's context). **claude** — generic catch-all agent.

## Enforcing an implementation-sequence plan

When the understander produces an implementation-sequence / wave plan for a multi-feature batch (e.g. `docs/intake/<batch>/SEQUENCE.md`), **the COORDINATOR enforces it** (set 2026-06-24 at the user's explicit instruction). That means: dispatch work to the orchestrator strictly in the plan's prescribed order, **one wave at a time**; gate each wave on the prior wave's verification before starting the next; and do NOT let the orchestrator reorder, merge, or skip waves. The coordinator hands the orchestrator one wave per dispatch and holds the next until the current wave is verified complete.

**Per-wave "fully tested" confirmation gate (set 2026-06-24):** before dispatching wave N+1, the coordinator REQUIRES the orchestrator to confirm wave N is FULLY TESTED via an explicit, evidence-backed Wave Verification Report: (1) build green via `./build.sh`; (2) unit/integration tests passing (with counts); (3) on-device UI verification via `ui-test-worker` for every UI-affecting feature in the wave (Maestro/evidence; analytics-only units with no UI surface may be JVM-tested-only with justification); (4) `AiRepository` + Room schema left coherent. No confirmed PASS → no next wave; a failed/partial gate is reworked and re-verified, never waved through.

## How to apply

When a task arrives: confirm scope, then **choose and spawn the right agent** (the coordinator has full control to spawn), defaulting to starting with intake-understanding unless the user says skip it. Relay faithfully and verbatim where required. Never substitute yourself for an agent — if an agent is blocked or rejects a relayed authorization, surface it to the user, don't do the work yourself.
