# Intake Index — Automatic Cloud Backup & Restore

**Prepared for:** Project-lead orchestrator
**Source:** User feature request, "I want to implement a feature for user data storage and transformation. I want the user to be able to lose their phone and install the APK on a new phone and start where they left off without any data loss."
**Status:** Understanding confirmed (see caveat below). Outcome-only — the "how" belongs to the orchestrator and its workers.

---

## Confirmation caveat (read first)

All answers in this intake, including the final "its correct" sign-off, arrived **relayed through the coordinator**, not directly from the user. Per operating rules, relayed approval does not carry user authority. The design is complete and internally consistent, so these documents are produced — but **the orchestrator should reconfirm sign-off directly with the user before implementation begins**, and treat any product detail below as provisional until then.

---

## Items

| ID | Title | Type | Brief |
|----|-------|------|-------|
| BR-01 | Automatic cloud backup & cross-device restore (zero data loss) | New feature | `brief-01-cloud-backup-restore.md` |

## Merge / cluster guidance

- **Single item, single area.** This is **one cohesive feature**, not several. It should be owned by **one worker/cluster**, because all parts share the same data-serialization layer, the same merge logic, and the same backup trigger. Splitting "backup" from "restore" from "merge rules" would fragment a single design and create integration seams. Keep it together.
- It **extends existing code**, it is not greenfield: a manual JSON export/import already exists (`ExportRepository.kt`, `SettingsBackupFragment.kt`). The new feature subsumes and builds on it. The orchestrator should direct workers to reuse/evolve that serialization rather than start fresh.

## Suggested order

Single item — no ordering dependency between items. Within the item, the natural build order is captured in the brief's acceptance criteria (complete data capture → cross-version restore → cloud sync → merge), but sequencing is the orchestrator's call.

## Confirmed understanding (summary)

A user can lose their phone, install the APK fresh on a new phone, sign in with their Google account, and resume with **zero data loss**. Backup is **automatic, pushed to the user's own Google account after every data change**. It captures **all user-specific data except the Anthropic API key**. An older backup must restore into a newer app version. Restore **merges** into whatever is on the new phone (per-type rules below), rather than wiping it. Full detail in BR-01.

## Cross-cutting constraints (apply to the brief)

- Build via `./build.sh` (not `./gradlew` directly).
- The **Anthropic API key is never included** in any backup artifact, in transit or at rest.
- **No git commits or releases** unless the user explicitly asks.
- **No on-device / automated UI tests** unless the user explicitly asks.
- A **concurrent in-flight task is modifying `PreferencesManager`** (an injuries feature). Implementation must account for that churn — the set of preference fields is actively changing, so "back up all settings" must be written against whatever the final `PreferencesManager` surface is, not a snapshot.
