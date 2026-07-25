---
name: feedback-method-delegation
description: User routinely delegates method/UI-shape choices ("you choose", "make what makes the most sense") — accept, decide, and log as veto-able; don't push design questions back
metadata:
  type: feedback
---

When the user answers a design question with "you choose" / "make whatever makes the most sense in terms of UI and user experience", treat that as an explicit delegation: make the call myself (or leave it to the builder for pure UI shape), and record it in the INDEX under a "Decisions made under delegation (veto-able)" section instead of re-asking.

**Why:** recurring pattern across batches — ux-refine 2026-07-03 ("method-delegated no-confirm"), stage3 (assume-and-log mode), and qol-batch-2026-07-25 (item 2 strictness "you choise"; item 4 top-level layout and detail presentation both delegated). The user decides OUTCOMES and readily hands over the how/shape; pushing the question back a second time is friction they don't want.

**How to apply:** still ask the outcome question once, in plain either/or form ([[plain-language-questions]]). If delegated: pick the option that best fits the app's established conventions (e.g. strict-gate philosophy → chose hard-guarantee exclusion in 2c), label it clearly as my choice under delegation, and make it veto-able in the INDEX. For UI-shape delegations, pass them to the builder as "delegated — make what fits the app's cohesion" rather than inventing a fixed spec.
