# Issue 14 — Full user-data export/import across app versions

**One-liner:** Export/import must transfer all user-related data so a new app version doesn't lose it.

## App context
User data (workout history, XP, level, achievements, PRs, settings) currently lives only in the installed app and is at risk when moving to a new app version.

## Current (incorrect) behavior
- There is no way to carry user data across app versions; updating/reinstalling risks losing everything.

## Correct behavior (target)
- The user can **export** a complete backup of their data and **import** it into another (e.g. updated) app version, fully restoring: workout history, XP, level, achievements, personal records, and settings.

## Acceptance criteria
- [ ] Export on version A produces a complete, self-contained data file.
- [ ] Importing it on version B restores history, XP, level, achievements, PRs, and settings, all correct.
- [ ] Round-trip (export → import) on the same version produces no data loss or duplication.

## Constraints & scope
- Use a **stable, versioned data format** so future app versions can read older exports; **flag schema-versioning / migration as a decision** rather than assuming.
- **Out of scope:** the exercise catalog itself (ships with the app). This is *user* data only.
- Do not include secrets/credentials in the export.
