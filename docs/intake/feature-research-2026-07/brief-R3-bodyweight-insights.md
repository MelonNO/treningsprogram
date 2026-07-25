# Brief R3 — Body-weight insights: trend + the AI finally knows your weight

**Type:** Feature — user-approved direction (Q2 "yes do this")
**Cluster:** Standalone worker (Home + Stats/Progress + generation prompt).

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
Body weight is half-built today: `BodyMeasurement` (date + kg) with DAO, a quick-add field + last-5 list with delete on Home, inclusion in backup/export. That's all — **no chart, no trend, and the AI generation prompt never sees body weight** (verified: no bodyweight-of-the-user input anywhere in `AiRepository`'s prompt build), even though plans contain bodyweight exercises and the user's goal (e.g. fat loss vs strength) interacts directly with weight trend. The Stats → Progress tab already hosts the strength chart and volume heatmap, so a natural home for a weight trend exists.

## What the user wants (end result)
Body weight becomes genuinely useful:

1. **Trend at a glance on Home:** alongside the existing quick-add, show current weight and the recent direction (e.g. "78.4 kg · ↓0.6 kg / 4 wks" style — smoothed, not raw last-two-points noise).
2. **A proper weight chart** in Stats → Progress: weight over time with a smoothed trend line, alongside the existing strength chart/heatmap, consistent with the app's chart styling.
3. **The AI sees body weight when generating:** the weekly-generation prompt includes current body weight and the recent trend (when data exists), so it can (a) prescribe bodyweight-exercise progressions sensibly (e.g. knowing what a set of pull-ups actually loads), and (b) keep the plan coherent with the user's goal + weight direction (e.g. fat-loss goal with weight trending up → the plan/rationale can respond). No data → prompt unchanged.
4. **Weigh-in reminder** exists as a toggleable notification type (delivered by Brief R2; this brief owns nothing notification-side).

## Acceptance criteria
- Done when Home shows current weight + a smoothed recent-trend indicator once ≥2 entries exist (graceful hidden/empty state otherwise), and quick-add still works as today.
- Done when Stats → Progress shows a body-weight chart over time with sensible axes for sparse, irregular entries (weekly-ish weigh-ins must look right), matching the app's existing chart look.
- Done when a generation performed with body-weight data present includes weight + trend in the prompt, and one without data produces today's prompt (no regression). Prompt inclusion is unit-verifiable off-device (prompt-builder test); plan *quality* effects are live-gen-only — user observes on-device, keep any live-API checking frugal.
- Done when deleting entries updates Home + chart consistently.
- Done when backup/export/import of measurements keeps working unchanged.

## Scope and constraints
- **In scope:** trend presentation (Home + Progress), prompt awareness, empty states.
- **Out of scope:** using body weight in volume/XP math (would silently change achievement thresholds — untouched); other measurement types (waist, arms — backlog); goal-weight targets; BMI-style judgments. The app informs, never moralizes.

## Decisions baked in
- Body-weight expansion approved as a direction (Q2); AI awareness explicitly included ("the AI knowing your body weight" was in the approved question).

## Assumptions (user may override)
- **A-B1:** trend = smoothed (e.g. rolling ~7-day/last-few-entries average) so single-day fluctuations don't flip the arrow.
- **A-B2:** the AI receives weight/trend as *context*, with no new hard rules — the model decides how it shapes the plan; the rationale may mention it.
- **A-B3:** chart lives in Stats → Progress (not a new tab).

## Considerations for whoever builds it
- `AiRepository` is also touched by tonight's batch-1 (rest-time generation math) — sequential releases, but rebase carefully.
- Very sparse data (2–3 points over months) must render without a misleadingly dramatic line.
