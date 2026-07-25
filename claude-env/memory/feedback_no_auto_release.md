---
name: feedback-no-auto-release
description: Never push a GitHub release unless the user explicitly asks for one
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 38712c01-9417-469c-ba17-e601d4e56e30
---

Do NOT create GitHub releases automatically after building or committing.

**Why:** User does not want unsolicited releases pushed to GitHub — they want control over when a release is published.

**How to apply:** After building an APK or pushing code, stop there. Only create a GitHub release when the user explicitly says something like "push a release", "create a release", or "release this".
