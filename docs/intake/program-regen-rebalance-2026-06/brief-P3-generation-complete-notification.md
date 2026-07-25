# P3 — System notification when generation finishes while the app is backgrounded

**Type:** Feature
**Cluster:** Generation-wait UX (with **P5**). Both wrap the generation lifecycle (completion + during-wait).
**Outcome-only:** describes the desired end result, not the implementation.

## Context
AI generation can take a while, and the user may minimize the app during it. There is no signal when generation finishes while backgrounded. The app already holds notification permission and uses notification channels (the rest timer), so the infrastructure exists.

## What the user wants (end result)
- When a generation runs and the app is **backgrounded/minimized**, post a **system notification** when generation reaches a **terminal outcome**.
- Terminal outcome = **either a successful generation OR a terminal failure** (after all 3 attempts fail). ("Notify when it is done trying, either a successful gen or three fails.")
- **Only when backgrounded** — if the app is in the **foreground** at completion, **no notification** (the on-screen status is enough).
- Applies to **all** generation types (start-of-week auto-gen, full "Generate AI Program", single-day regen, and the P1/P2 rebalances).
- **Tapping** the notification **opens the app to the Program tab**.
- **Wording is the builder's choice.**

## Current vs correct behavior
- **Current:** no notification on generation completion; a backgrounded user has no idea when it finished.
- **Correct:** a backgrounded user gets a system notification on success or terminal failure, for any generation type; tapping it opens the Program tab.

## Acceptance criteria (observable)
- **Done when** completing any generation while the app is **backgrounded** posts a system notification.
- **Done when** the notification fires on **both** success **and** terminal failure (after 3 failed attempts).
- **Done when** **no** notification is posted if the app is in the **foreground** at completion.
- **Done when** it covers **all** generation entry points.
- **Done when** tapping the notification **opens the app to the Program tab**.

## Scope and constraints
- **In scope:** posting a completion notification (success/terminal-failure) for backgrounded generations across all entry points; deep-link/tap → Program tab.
- Reuses the existing notification infrastructure and the runtime `POST_NOTIFICATIONS` permission (Android 13+).
- **Out of scope:** progress/ongoing notifications during generation; changing generation logic.
- **Standard cross-cutting constraints:** build via `./build.sh`; no commits/releases unless asked; no on-device/UI tests unless asked.

## Decisions confirmed by the user (2026-06-28, via coordinator)
- All generation types; **only when backgrounded**; notify on **success and terminal failure**; tap → **Program tab**; **wording is the builder's choice**.

## Decisions deferred / assumptions (user may override)
- **[P3-A1] Copy** is the builder's to write. Suggested: success ≈ "Your workout plan is ready"; failure ≈ "Couldn't generate your plan — tap to try again."
- **[P3-A2] If notification permission is denied**, simply post nothing (no crash, no nagging) — graceful degradation assumed.
- **[P3-A3]** Whether success and failure use distinct copy/channels is left to the builder.
