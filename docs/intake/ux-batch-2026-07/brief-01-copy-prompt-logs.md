# Brief 01 — Copy all prompt logs to clipboard

**Type:** Feature
**Cluster:** Independent
**Outcome-only:** This brief describes the end result and user experience. It deliberately does not prescribe implementation.

## Context
The app already keeps a **Prompt Log** (Profile → Settings) — a rolling record of the app's AI calls, each entry holding the exact prompt sent and the AI's response. Today the user can copy one entry's prompt or one entry's response at a time. The user maintains external "Change docs" and wants an easy way to hand the *whole* log to another tool/coach for feedback on the app's automatic workout generation.

## What the user wants (end result)
A single **"Copy all"** action that places the **entire set** of prompt-log entries — every entry's prompt together with its AI response — onto the **clipboard** as one block of text, ready to paste elsewhere. Clipboard only; no Android share sheet.

## Acceptance criteria (Done when …)
- There is one clearly-labelled action that copies **all** stored prompt-log entries at once (not just the most recent).
- Each copied entry includes both its **prompt** and its **AI response**, in a readable order, with enough separation/labelling that entries are distinguishable when pasted.
- The action copies to the **system clipboard**; a brief confirmation (e.g. a snackbar) tells the user it worked.
- Pasting into another app yields the full text of every entry.
- If the log is empty, the action does nothing destructive and tells the user there's nothing to copy.

## Scope and constraints
- **In scope:** the prompt-log entries and their responses only.
- **Out of scope:** bundling the user profile, workout history, or the "rejected plan" logs — the user scoped this specifically to the prompt-log content. (If the builder sees value in optionally including those, surface it; do not add it unprompted.)
- **No share sheet**, clipboard only.
- **No API-key concern:** the API key is sent as an HTTP header and is never part of the prompt/response text, so no stripping is required. (Builder should still sanity-check that no future field embeds it.)

## Decisions baked in
- Copy the **full set** of entries, both prompt and response, to **clipboard**.

## Assumptions (user may override)
- **[A-1]** The action lives near the existing Prompt Log screen (Profile → Settings). Placement isn't load-bearing to the user.

## Considerations for whoever builds it
- The Prompt Log is capped (a rolling window of recent entries) — "all" means all currently-stored entries, which is expected and fine.
- Very large logs produce a large clipboard payload; that's acceptable for the paste-into-another-tool use case.

## Standing constraints
- Build with `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.
