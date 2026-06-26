# B05 — Clean up downloaded update APKs

**Type:** Housekeeping
**Cluster:** Standalone (update flow)
**Outcome-only:** describes the desired end result, not the implementation.

## Context
The app can update itself by downloading a `treningsprogram-<version>.apk` into the device's public Downloads folder and launching the system installer. Over many updates these APK files accumulate and clutter the phone's storage.

## What the user wants (end result)
After the app has updated itself, the downloaded update APK(s) should be **removed automatically** so they do not pile up. The user does not want a growing pile of `treningsprogram-*.apk` files in Downloads.

## Behavioral notes (confirmed)
- **When cleanup happens:** Android does not reliably tell the app the exact moment the install finishes, so cleanup should run **on app launch** (delete leftover downloaded update APKs at startup). Same end result for the user — no pile-up — without depending on an install-finished signal.
- **Safety check (required):** only delete after **verifying the update actually completed** — i.e. the currently installed version is the new one. Do **not** delete the APK on a failed or partial install (so a user can retry installing it).

## Acceptance criteria
- Done when, after a successful self-update, the user no longer accumulates old `treningsprogram-*.apk` files in Downloads over repeated updates.
- Done when an APK whose install did **not** complete is **not** deleted (it remains available for the user to retry).
- Done when cleanup does not touch unrelated files in Downloads (only the app's own update APKs).

## Scope and constraints
- In scope: removing the app's own downloaded update APKs after a confirmed-completed update.
- Out of scope: changing how updates are downloaded or installed, or where they download to (unless the builder finds that necessary to make safe cleanup possible — surface as a decision if so).

## Considerations for whoever builds it
- "Update completed" should be a real check (installed version == the downloaded version), not just "download finished."
- Be conservative about which files are eligible for deletion — only the app's own update APKs.
- Standard cross-cutting constraints apply (build via `./build.sh`; no commits/releases or UI tests unless asked).
