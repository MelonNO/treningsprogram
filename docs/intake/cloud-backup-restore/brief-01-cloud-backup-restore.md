# BR-01 — Automatic cloud backup & cross-device restore (zero data loss)

**Type:** New feature (extends an existing manual backup feature)
**Outcome-only:** This brief describes the end result and user experience. It does **not** prescribe implementation. The "how" is the orchestrator's and its workers' decision.

---

## Context

Today the app stores all data **only on-device** (Room database + `EncryptedSharedPreferences`). A **manual** backup already exists:

- **Settings → Backup** has **Export** (writes a JSON file the user saves via the share sheet) and **Import** (picks that file, wipes current data, restores from it). Implemented in `data/repository/ExportRepository.kt` and `ui/settings/SettingsBackupFragment.kt`.
- That manual backup is **incomplete** (see "current behavior" below) and, being manual, does not survive a phone lost without a recent export.

This feature replaces the "remember to export" model with an **automatic cloud backup** tied to the user's Google account, so a lost phone results in **no data loss**, and makes the captured data **complete**. It is an evolution of the existing export/import code, not a greenfield build.

## What the user wants (end result)

A user can:
1. Lose their phone entirely.
2. Install the APK fresh on a **new phone**.
3. Sign in with the **same Google account**.
4. Find **all of their data restored automatically**, and continue training exactly where they left off — with **zero data loss**.

## Current (incomplete) behavior vs. correct behavior

**Backup trigger**
- *Current:* Manual only — the user must tap Export and save the file off-device before any loss. Anything since the last manual export is lost if the phone disappears.
- *Correct:* **Automatic.** A backup is pushed to the user's Google account **after every data change** — every completed workout, logged set, generated plan, achievement unlock, body measurement, and **settings change**. The window of possible loss is effectively zero.

**What gets backed up**
- *Current:* Workout sessions, sets, achievements, user stats, body measurements, planned exercises, and a *subset* of preferences. **Missing:** the custom/edited **exercise library**, **gym presets**, and several settings (rest-timer length, injury severity, selected gym preset, daily challenges, and others).
- *Correct:* **Absolutely all user-specific data**, including the items currently missing. The **only** exclusion is the Anthropic API key.

**The Anthropic API key**
- *Current:* Already excluded from the manual export.
- *Correct:* **Still excluded.** The API key never travels with the backup, in transit or at rest. After restore on the new phone, the user re-enters their API key once.

**Cross-version restore**
- *Current:* A backup only restores into the **exact same data format**; a backup made on an older app version is rejected by a newer install (and vice versa).
- *Correct:* An **older backup must restore cleanly into a newer app version** (forward migration / "transformation" of older backup data into the current shape). This is the "transformation" the user asked for.

**Restore semantics**
- *Current:* **Wipe-and-replace** — restoring deletes all existing data first.
- *Correct:* **Merge** into whatever is already on the new phone (per-type rules below). The new phone is not wiped.

## Restore merge rules (per data type)

- **Workout sessions + sets, and body measurements:** **Union** — keep everything from both sides. (These cannot realistically collide; a true duplicate would require the same data logged at the same instant on two devices.)
- **Achievements:** **Union** — if the new device has any achievement the backup does not, keep it; otherwise the backup/older set is the master. **No achievement is ever lost from either side.**
- **Workout plans (generated programs):** Per week, the **most recently generated plan wins** — the newer plan overwrites the older, regardless of which side it is on.
- **User stats / streaks / level / XP:** **Recomputed from the merged history + merged achievements** after the merge completes. Never copied from one side (copying would double-count or undercount). Whatever the app derives these from, it derives them again from the merged result.
- **Settings / preferences:** Decided **per setting**. If the value on the new phone **differs from the default**, the phone's value wins. If the value is **still the default**, the backup's value is used.

## Acceptance criteria ("Done when …")

- **Done when** a user signs in with their Google account and the app automatically backs up their data to that account, with a fresh backup pushed after every data change (completed workout, logged set, generated plan, achievement unlock, body measurement, settings change).
- **Done when** a user can install the APK fresh on a new phone, sign in with the same Google account, and have **all** of their data restored automatically — full workout history and sets, generated workout plans, achievements, stats/streaks/level/XP, body measurements, the custom/edited exercise library, gym presets, and all settings/preferences.
- **Done when** the **Anthropic API key is never present** in any backup artifact, and the restored app prompts the user to re-enter it.
- **Done when** a backup produced by an **older app version** restores successfully into a **newer** app version, with older data transformed into the current shape and no data lost.
- **Done when** restoring onto a phone that already contains data **merges** rather than wipes, following the per-type rules above:
  - workout sessions/sets and body measurements are unioned;
  - achievements are unioned (nothing lost from either side);
  - for each week, the most recently generated workout plan wins;
  - stats/streaks/level/XP are recomputed from the merged history and achievements;
  - each setting resolves to the phone's value if it differs from the default, otherwise the backup's value.
- **Done when** the result, from the user's perspective, is "I lost my phone and lost nothing."

## Scope and constraints

- **In scope:** Google account sign-in; automatic backup to the user's Google account after every data change; complete data capture (closing the gaps above); cross-version (older→newer) restore; merge-based restore with the per-type rules; re-prompting for the API key after restore.
- **Out of scope (unless the user later asks):** multi-device *live* sync (two phones active at once), sharing/exporting to other users, any backend server the team operates, backing up the API key.
- **Hard constraints:**
  - Build via `./build.sh`.
  - The **API key must never be written into any backup artifact**, in transit or at rest.
  - No git commits/releases and no on-device/automated UI tests unless the user explicitly asks.
  - Implementation must account for a **concurrent in-flight change to `PreferencesManager`** (an injuries feature). "Back up all settings" must be implemented against the final `PreferencesManager` surface, since its fields are actively changing.

## Considerations for whoever builds it (surfaced, not decided)

- **"After every data change" needs coalescing/debouncing.** Pushing a full backup on every single edit would hammer the network and battery. Some batching/debouncing of rapid changes is expected. *This is a how-detail for the orchestrator — flagged here only so it isn't missed; the approach is the implementer's call.*
- **Cross-version restore is an ongoing obligation.** "Older backup restores into newer app" means the backup format must be versioned and have a forward-migration path that is maintained as the data model evolves — not a one-time conversion. The orchestrator should treat this as a durable design requirement, not a single migration.
- **"Differs from the default" for settings** presumes each setting has a knowable default to compare against. The implementer will need a reliable way to tell "user-set" from "still default" for every preference; where a setting can't distinguish these, that's a decision point to raise.
- **Reconfirm with the user** (see INDEX caveat) before building — all approvals here were relayed.
