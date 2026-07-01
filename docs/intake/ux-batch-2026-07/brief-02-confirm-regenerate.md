# Brief 02 — "Are you sure?" before regenerating the program

**Type:** Safety feature
**Cluster:** Independent (touches the Program/settings generate action; minor layout overlap with the icon sweep — see INDEX hazard note)
**Outcome-only:** This brief describes the end result and user experience. It deliberately does not prescribe implementation.

## Context
Generating/regenerating the AI program replaces the current program. The user wants a light guard so they don't wipe their program with an accidental tap. Note: the *default* regenerate flow already preserves days that have logged workouts, and the "do another day today" flow already confirms — so this guard is specifically about the **Generate / Regenerate-program action** in the AI/Program settings, which overwrites the existing program.

## What the user wants (end result)
Before the **Generate / Regenerate-program** action runs and overwrites the existing program, show a **simple confirmation** ("Are you sure? This will replace your current program" — yes/no). On confirm, proceed exactly as today; on cancel, nothing changes.

## Acceptance criteria (Done when …)
- Tapping the **Generate/Regenerate-program** button no longer immediately overwrites the program — it first shows a confirmation prompt.
- Confirming proceeds with generation exactly as before.
- Cancelling leaves the current program untouched and returns the user to where they were.
- The guard applies to the settings-side Generate/Regenerate-program action only.

## Scope and constraints
- **In scope:** the one Generate/Regenerate-program action in AI/Program settings.
- **Out of scope:** the single-day regenerate, the "do another day today" flow (already confirmed), and the silent auto-attribute move in item 10 (explicitly *not* confirmed — see brief 10). Do not add confirmations to those here.
- It is a **simple confirmation**, **not a hard block** — the user can always proceed.

## Decisions baked in
- Simple yes/no confirmation, not a block.
- Only the settings Generate/Regenerate-program action.

## Assumptions (user may override)
- **[A-2a]** First-time generation (when no program exists yet) may skip the prompt, since there is nothing to overwrite — confirm with the user if the builder wants to prompt there too.

## Considerations for whoever builds it
- Keep the wording plain and specifically about *replacing the current program*, so it reads as a safety net, not a nag.
- If the icon sweep (item 6) restyles this button, coordinate so the confirm wiring survives the restyle (see INDEX hazard note).

## Standing constraints
- Build with `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.
