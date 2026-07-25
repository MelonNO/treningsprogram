# Brief 02 — Auto-rebalance default ON

**Type:** Feature (default change)
**Cluster:** A — shares the auto-rebalance seam with **item 4** (which relocates the toggle). One worker owns both.
**Outcome-only:** Describes the end result and user experience; does not prescribe implementation.

## Context
The "auto-rebalance week" toggle currently defaults **OFF**. When ON, changing a day's primary muscle focus rebalances the rest of the week. The toggle lives today inside the Program tab's program-options dialog; item 4 moves it into the new "App Settings" screen.

## What the user wants (end result)
The toggle should default **ON** for anyone who hasn't explicitly chosen a value — while never overriding a choice a user already made.

## Acceptance criteria (Done when …)
- A fresh install (or a user who has never touched the toggle) has auto-rebalance **ON** by default.
- A user who **previously set it explicitly** — ON *or* OFF — keeps their own choice after updating; the new default does **not** flip an explicit OFF back to ON.
- The toggle (in its new App Settings home, per item 4) reflects the effective value.

## Diagnose first
- The behavior hinges on distinguishing "never explicitly set" from "explicitly set to OFF." The current preference stores only a boolean whose default is `false`, with no "unset" sentinel — so on-update, an existing user who never touched it is indistinguishable from one who set it OFF unless a migration/sentinel is introduced. The **outcome** above is what matters; the mechanism is the builder's call, but they must not silently override users who deliberately turned it off.

## Scope and constraints
- **In scope:** the default value + one-time preservation of any explicit prior choice.
- **Out of scope:** what auto-rebalance *does* when ON (unchanged).

## Decisions baked in
- New default **ON** for the unset case; never override an explicit prior choice.

## Considerations for whoever builds it
- Coordinate with **item 4** (toggle relocation) — same worker.
- Distinguishing "unset" from "explicit OFF" likely needs a sentinel/migration; that is the builder's decision, as long as the acceptance criteria hold.

## Standing constraints
- Build with `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.
