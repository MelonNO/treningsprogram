# Brief 01 — Remove AM/PM from the day-reset time labels (use 24-hour)

**Type:** Feature (polish)
**Cluster:** A — Settings IA / nav (one worker). This label lives on the **day-reset control, which moves into the new "App Settings" screen (item 4)** — make the change wherever the control ends up.
**Outcome-only:** Describes the end result and user experience; does not prescribe implementation.

## Context
The day-reset ("day boundary") hour picker currently shows 12-hour AM/PM labels — `12:00 AM (midnight)`, `4:00 AM — default`, `6:00 AM`, etc. — and its hint text reads "Default 4:00 AM." The supported range is only 00:00–06:00. This is the **only** AM/PM time surface in the app.

## What the user wants (end result)
Show these times in **24-hour format** with no "AM"/"PM" anywhere.

## Acceptance criteria (Done when …)
- The day-reset hour options display in 24-hour format (e.g. `00:00 (midnight)`, `04:00`, `06:00`) — no "AM" or "PM" text anywhere.
- The default option is still clearly indicated (e.g. `04:00 — default`).
- The accompanying hint/description no longer says "4:00 AM"; it reads the 24-hour equivalent ("04:00").
- No other user-facing time label regresses (this is the only AM/PM surface).

## Scope and constraints
- **In scope:** the day-reset picker labels + its hint text.
- **Out of scope:** any 24-hour vs 12-hour preference toggle — the change is unconditional.

## Decisions baked in
- 24-hour format (locked; user did not override the default).

## Assumptions (user may override)
- **[A1-1]** Keep the "(midnight)" annotation on `00:00` and the "— default" tag on the default hour. A "noon" annotation is unnecessary since the range is ≤ 06:00.

## Considerations for whoever builds it
- This rides along with **item 4** (the day-reset control moves from Training Profile into the new App Settings screen). Same worker; apply the label change in the control's new home.

## Standing constraints
- Build with `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.
