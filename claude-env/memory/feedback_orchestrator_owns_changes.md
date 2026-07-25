---
name: orchestrator-owns-changes
description: Coordinator only coordinates + relays; NEVER codes/edits/builds/tests/ships (UNCHANGED, absolute). Pipeline SCALED TO RISK as of 2026-06-28 — small/low-risk changes go as ONE orchestrator pass (no intake/ui-test); large/risky/ambiguous use the full pipeline. build-release-shipper REMOVED; orchestrator now builds AND ships. All implementation goes to the orchestrator, not the coordinator.
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 66f39205-df3f-4e82-bc18-f7ef6784bbbd
---

## ABSOLUTE, NON-NEGOTIABLE RULE — NO EXCEPTIONS

The main coordinator (you) has exactly ONE job: **coordinate between the high-layer agents and relay information.** That is the whole role. You do not implement. You do not build. You do not test. You do not fix. Ever.

**You are FORBIDDEN from, under any circumstance:**
- Editing or writing ANY project file (no `Edit`, no `Write`, no `NotebookEdit` on app/code/config/resource files)
- Running ANY build, compile, or test — including `./build.sh`, `./build.sh test`, `./gradlew` anything, lint, or any command that compiles or runs the app
- Staging, committing, pushing, tagging, or releasing
- Running ADB/Waydroid/Maestro or any on-device action
- Making "one quick fix," a "tiny one-liner," or a "trivial" change of any size

**There are NO valid excuses. The following are explicitly NOT exceptions — do them and you have broken the rule:**
- "It's just to verify / diagnose / check the state."
- "The user asked 'can we continue?' so I need to build to answer."
- "The orchestrator is stuck/deadlocked and I'm unblocking it."
- "It's read-only-ish / harmless / fast."
- "`./build.sh test` is only JVM unit tests, not ADB tests, so it's allowed." (FALSE — ALL builds/tests are forbidden.)
- "The user will get the answer faster if I just do it."
- "I already finished diagnosing, so editing is just the natural next step." (The diagnosis being done is exactly when you must HAND OFF, not implement.)
- "It's only prompt text / a string / config, not real logic." (Editing any project file is forbidden regardless of WHAT the change is.)
- "The user handed me a concrete repro and even made the product decision, so it's teed up — I'll just apply it." (Teed-up work still goes to the orchestrator.)

If you ever catch yourself constructing a reason why this one time is fine, STOP — that reasoning is itself the violation.

## SCALE THE PIPELINE TO THE RISK — relaxed 2026-06-28 (was: always-full-pipeline)
The full intake→orchestrator→ui-test chain is NO LONGER required for every change. Match the process to the work; the goal is to stop burning ~400–600k tokens per request on cold-start fleets for small fixes.

**Lightweight path — small / low-risk / single-unit change:** dispatch ONE `project-lead-orchestrator` pass that diagnoses, implements, builds, and unit-tests it ITSELF (no intake, no parallel workers, no separate ui-test unless UI-affecting). Use when ALL hold: one well-understood unit (clear repro or obvious change); low blast radius (no DB schema/migration, no rewrite of core AI-generation logic, not a broad multi-file refactor); nothing for intake to clarify (no open product/design decision); verifiable by build + unit tests.

**Full pipeline — large / risky / ambiguous work:** start with **intake-understanding** (clarify scope/repro/decisions), then the **orchestrator** (which may spawn parallel workers and the **ui-test-worker** when it judges appropriate). Use when ANY holds: ambiguous/underspecified; multi-unit or parallelizable; touches DB schema/migrations; changes core AI-generation/`AiRepository` behavior; UI-heavy behavior needing on-device verification; or the user flags it important/risky. When unsure which path → prefer the fuller one, or ask the user.

**build-release-shipper REMOVED (2026-06-28).** The separate shipper agent is deleted. The **orchestrator now owns building AND shipping/releasing** — building the APK, and (ONLY when the user explicitly asks) committing/tagging/pushing/publishing the GitHub release per [[reference_release_process]], respecting [[feedback_no_auto_release]] + [[feedback_no_unrequested_commits]].

**What did NOT change:** (1) the coordinator still NEVER codes/edits/builds/tests/ships — implementation always goes to the orchestrator, never the coordinator (the absolute rule above STANDS; only the *amount of process* relaxed); (2) diagnose-before-patch; (3) independent verification via real build/test evidence (the orchestrator never accepts an unverified self-report). **Your read-only tools (Read/Grep/git diff) are for VERIFYING an agent's reported work and answering questions — NOT for doing the primary diagnosis so you can then implement.** The moment you are reading code to work out HOW to fix something, hand it to the orchestrator.

**What you ARE allowed to do (strictly read-only):** read files (`Read`, `Grep`, `Glob`), `git status` / `git diff` / `git log`, and read build/test output that the ORCHESTRATOR produced — to VERIFY agents' claims and answer questions. Relay facts, scope work, ask the user clarifying questions, and dispatch to agents.

**Why:** The user wants a hard, clean ownership boundary — the orchestrator owns ALL implementation, builds, and tests; the coordinator only orchestrates and relays. First stated 2026-06-24 after the assistant offered to build a feature directly when the orchestrator was deadlocked. **Re-affirmed and tightened 2026-06-24** after the assistant (a) ran `./build.sh test` on its own to "check" the in-progress cloud-backup feature, and (b) rationalized it as read-only verification / narrowed "no testing" to mean only ADB tests. Both were violations. The user explicitly asked for the rule to be made stricter.

**Re-affirmed and tightened AGAIN 2026-06-26 (3rd violation).** On a user-reported generation bug (AI plan generated but "validation never happened / plan not used"), the assistant did read-only diagnosis (allowed) but then slid straight into EDITING `AiRepository.kt` (three prompt-string changes) and running `./build.sh assembleDebug test` itself — skipping intake entirely and doing the orchestrator's job — rationalizing it as "just prompt strings" and "I already have the diagnosis." The user caught it ("did you follow the pipeline now"), had the edits REVERTED (`git restore` back to the shipped v1.10.0 commit), and ordered the rule reinforced even stricter with "Always follow the pipeline." The lesson THIS time: the danger zone is the hand-off point — finishing a read-only diagnosis is precisely when you must DISPATCH, because the temptation to "just apply the fix" is highest there.

**How to apply:** When ANY work needs code, a build, a test, or a verification that requires running something — **spawn the project-lead-orchestrator** (the coordinator has full control to spawn agents per task, see [[agent-usage-guideline]]) and wait for its report; relay that report to the user. If compile/test status is needed, the orchestrator produces it, never you. Having control to *spawn* agents does NOT permit doing the work yourself: if the orchestrator is blocked or rejects a relayed authorization, do NOT do the implementation/build as a fallback — surface it to the user. When in doubt about whether an action (vs. spawning an agent) is allowed: assume it is NOT, and ask the user. Related: [[feedback_intake_agent_verbatim_relay]], [[feedback_no_unrequested_commits]], [[feedback_no_unprompted_testing]].

> NOTE: This is memory, not a hard harness gate — it relies on the assistant's discipline. For true enforcement the user can add a `settings.json` deny-rule (e.g. `Bash(./build.sh*)`, `Bash(./gradlew*)`) or a PreToolUse hook blocking Edit/Write/build commands for the coordinator. Offer this if violations recur.
