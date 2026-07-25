# Brief 08 — Rest-timer completion sound must not mute music

Type: Bug
Cluster: standalone (isolated to the rest-timer service)

> Outcome-only: this brief describes the end result and user experience. The implementation approach belongs to the orchestrator/worker.

## Context

When the rest timer finishes, the app vibrates and plays a completion sound via a high-importance notification. Grounded facts for the diagnosis: the completion sound is currently produced by TWO mechanisms at once (an explicitly played default notification ringtone AND the high-importance "Rest Complete" channel's own default sound), and the app never manages audio focus in any way.

## Current (incorrect) behavior — user's report

Music playing in another app is **muted while the rest-timer notification sound plays**, then comes back. The user wants the chime *on top of* the music, not instead of it.

## Correct behavior

When the rest timer completes while music is playing: a **single** short notification chime plays **above the music, and the music keeps playing** — no pause, no mute. Vibration stays as today. When no music is playing, completion behaves as today (chime + vibration).

## Diagnose first

Establish which mechanism causes the mute on-device (the doubled sound paths and/or how the sound is routed/focused) before fixing — the fix must address the actual cause, not just lower a volume.

## Acceptance criteria

- Done when timer completion during music playback leaves the music playing audibly throughout — at most a momentary automatic dip (Assumption A6), never a pause or full mute, and playback continues by itself afterwards.
- Done when exactly ONE completion chime is heard (the current double-fire is folded into this fix — improvement the user did not object to).
- Done when vibration on completion still occurs.
- Done when the timer's ongoing (ticking) notification remains silent as today.
- Done when completion with no music playing is unchanged from the user's perspective (a chime + vibration).

## Scope and constraints

- Only the completion signaling changes; timer mechanics, skip behavior (skip stays silent), and notification content/appearance are untouched.
- Standing constraints: build via `./build.sh`; no commits/releases unless asked; no on-device UI tests — the user will verify the audible behavior on-device; automated verification via unit tests/build only.

## Assumptions (user may override)

- A6: a brief, automatic volume dip during the chime is tolerable; a pause/mute is not.
