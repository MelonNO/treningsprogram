---
name: intake-agent-verbatim-relay
description: "How to relay the intake-understanding agent — show its output verbatim, route the user's replies straight to it"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 66f39205-df3f-4e82-bc18-f7ef6784bbbd
---

For the **intake-understanding** agent specifically: always show the user the agent's EXACT output (verbatim, in full — never a summary or paraphrase), and always route the user's responses straight back to that agent rather than acting on them yourself.

**Why:** The user treats the intake agent as a conversation partner they're talking to directly. Summarizing its output or answering on its behalf hides information they want and breaks the back-and-forth they expect. They explicitly asked for this on 2026-06-24.

**How to apply:** When the intake agent returns, paste its complete final message verbatim (clearly marked as the agent's output). When the user replies, forward their message verbatim to the same agent via SendMessage (using its agentId, which keeps its context) — do not interpret, pre-decide, or implement their answer yourself. Relay the agent's exact reply back each round. Related: [[feedback_no_unrequested_commits]].
