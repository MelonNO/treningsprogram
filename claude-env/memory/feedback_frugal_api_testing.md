---
name: feedback_frugal_api_testing
description: Keep live API verification frugal and decision-driven — no exploratory sweeps
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 0bc9a036-2db1-49b1-a569-6e553d9e82ee
---

When verifying changes against the live Anthropic API (the workout app's generation calls, key at `/home/migul/treningsprogram/claude k`), keep it MINIMAL and decision-driven. The user (2026-06-27): "calm it down a bit with the api testing, make sure the tests are worth it."

**Why:** during the v1.10.6 round, live verification burned through the API key's credit balance (~35+ full-generation calls) AND tripped the user's monthly Claude Code platform spend limit (`claude.ai/settings/usage`) — halting the agents mid-run. These are two SEPARATE budgets: the Anthropic API key credits (the app's generation calls) and the Claude Code subscription spend limit (running the agents themselves). Both got exhausted.

**How to apply:** before any live API run, define the exact decision each call informs and run the MINIMUM to decide. Cap the live phase (e.g. ≤8 generation loops), STOP EARLY once a result is clear, and never re-verify things already proven (cite the prior numbers instead of re-spending). Do NOT re-verify on each model/variant exploratorily. Lean A/B beats a broad sweep. Don't run live calls at all until the user confirms credits are available. [[feedback_coordinator_background_agent_ops]] [[feedback_always_skip_waydroid]]
