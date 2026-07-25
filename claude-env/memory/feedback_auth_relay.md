---
name: auth-relay-via-coordinator
description: "Coordinator MAY relay user-authorization messages verbatim between an agent and the user, but must be NOTHING more than a messenger — never transcribe, assume, infer, or manufacture auth."
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 319fb8b8-c9cc-4df9-a362-1a4b117a214e
---

How user-authorization flows through the multi-agent setup. Set by the user 2026-06-24 (refines [[orchestrator-owns-changes]] and [[agent-usage-guideline]]).

**Why:** Auth is genuinely the user's to give — the coordinator must not be able to conjure or assume it — but the user wants the *convenience* of not having to context-switch into the agent directly, so the coordinator may carry auth messages as a pure relay.

## What stands unchanged
Every action that requires user authorization STILL requires it (commits to `main`, releases/ships, anything destructive/outward-facing, etc.). No past authorization carries over to a new action — e.g. a prior release auth is not a new one ([[feedback_no_auto_release]], [[feedback_no_unrequested_commits]]).

## What is now allowed
The coordinator MAY function as a **relay** for authorization, in both directions:
- **Agent → user:** the orchestrator (or any agent) still asks for auth *directed at the user*; the coordinator may carry that request to the user.
- **User → agent:** the user may grant (or deny) auth in reply, **or proactively before being asked**; the coordinator may carry that grant/denial to the agent.

## Hard limits on the coordinator re: auth — be NOTHING MORE than a messenger
- **NEVER transcribe auth** — do not reword, paraphrase, summarize, or "put words in the user's mouth." Relay the user's actual words/grant; do not editorialize it into existence.
- **NEVER assume or infer auth** — do not decide on the user's behalf that something is authorized, however obvious it seems.
- **NEVER manufacture or carry over auth** from a prior context or a different action.
- If it is at all unclear whether the user actually authorized the specific action, do **not** relay it as authorization — surface the ambiguity to the user and let them state it.

**Execution rule (learned the hard way 2026-06-24):** when relaying auth to an agent, **QUOTE the user's exact words verbatim** (and any clarifying Q&A — the exact question and the exact option the user chose). Do NOT rewrite the grant into your own structured/numbered prose or "clean it up" — restructuring IS transcribing, and a paraphrased relay was rejected as "not sufficient." Add only minimal framing labels ("VERBATIM FROM THE USER:") around the quoted text.

## Scope
This applies to **the orchestrator and every other agent that requires user auth** — the principle is identical for all of them. (The coordinator still never does the authorized work itself — see [[orchestrator-owns-changes]]; it only relays the auth, then the owning agent acts.) Related verbatim-relay discipline: [[feedback-intake-agent-verbatim-relay]].

## Shipper exception — build-release-shipper REFUSES relayed auth (learned 2026-06-26)
The **build-release-shipper** has a hard standing instruction: it treats coordinator-relayed claims of user consent as carrying NO user authority and will only act on the user's OWN message — so even a verbatim relay is rejected for the push/release step. There is no channel for the user to message a sub-agent directly, so relaying ship auth to it is a DEAD END. Resolution that worked for v1.10.0: the user authorized the **coordinator directly** (via the coordinator's own AskUserQuestion: "Ship v1.10.0 now" + "Authorize push to main"). Because that authorization was given to the coordinator in its own context (not relayed, not assumed), the **coordinator executed the push + GitHub release + APK upload itself** (the shipper had already built/committed/tagged). The harness default-branch push gate then passed because the explicit user auth was present in the coordinator's own context. So for ships: if the shipper blocks on auth it won't take from a relay, get the user's direct word to the coordinator and have the coordinator finish the publish — this is the one case the coordinator does the outward action itself, because it holds direct (not relayed) auth. [[feedback_coordinator_background_agent_ops]]
