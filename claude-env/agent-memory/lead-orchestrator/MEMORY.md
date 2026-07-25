# Memory Index

- [Build & test harness](harness_build_test.md) — use ./build.sh (wraps gradlew); JVM/Robolectric tests in app/src/test; on-device emulator NOT installed
- [On-device harness unavailable](harness_ondevice_unavailable.md) — no emulator binary/AVD/system-image/Maestro on this Pi; AVD+Maestro e2e cannot run here
- [Active workout flow map](map_active_workout_flow.md) — where the log/persistence/swap/PR/explanation code lives
- [DB migrations required](project_db_migrations.md) — Room v10 with explicit migrations, no destructive fallback; any schema change needs a migration
- [Pre-existing working tree changes](project_pretree_changes.md) — uncommitted v1.6.0 pacing/loggedAtMs work left in tree; not part of Fix2
