---
name: robolectric-sqlite-aarch64
description: On this aarch64 Pi, Robolectric's Android-SQLite native + Conscrypt have NO aarch64 build — how to unit-test a Room migration anyway
metadata:
  type: reference
---

Testing a Room **migration** (or anything needing a real SQLite engine) in the JVM unit harness on this Raspberry Pi (Linux **aarch64**) hits two hard walls:

1. **Robolectric's own SQLite native runtime** ships only `native/linux/x86_64` (+ mac/windows). `FrameworkSQLiteOpenHelperFactory` → Android SQLite → `nativeOpen` throws `AssertionError: native runtime not supported on Linux (aarch64)`. The legacy sqlite4java mode has no aarch64 build either. So the standard `Room.inMemoryDatabaseBuilder` / `MigrationTestHelper` / framework-open-helper approaches CANNOT run here.
2. **Conscrypt** (Robolectric bootstrap tries to init it) has no aarch64 OpenSSL native → `UnsatisfiedLinkError`.

**Working pattern (used by `R1BackfillMigrationTest`, branch `exercise-recognition-fix-2026-06`):**
- Add `@ConscryptMode(ConscryptMode.Mode.OFF)` to the Robolectric test class (skips the failing TLS provider; pure-data migrations need no TLS). Keep `@RunWith(RobolectricTestRunner::class)` only so the pure-Java `MatrixCursor` shadow is available.
- Drive the REAL migration (`AppDatabase.MIGRATION_x_y.migrate(db)`) against **xerial `org.xerial:sqlite-jdbc`** (a `testImplementation` dep — it DOES bundle `Linux/aarch64/libsqlitejdbc.so`) through a thin hand-written `SupportSQLiteDatabase` adapter over a JDBC `Connection`. Implement only `execSQL(String)`, `execSQL(String, Array)`, `query(String)`, `query(String, Array)` (surface ResultSet as `MatrixCursor`); throw `UnsupportedOperationException` for the rest of the interface — a migration only uses query/execSQL. Use a single shared `jdbc:sqlite::memory:` connection so the in-memory DB survives the whole test.
- This runs the production migration SQL against a genuine SQLite engine, so data-safety/idempotency assertions are real.

**Residual this leaves:** it tests the migration's SQL in isolation, NOT the full Room-open path (Room opening a real old-version DB and running the whole migration chain + schema-identity validation). For a **data-only** migration (no schema change) that residual is near-nil. End-to-end Room-open-with-migrations is only verifiable on-device ([[reference_ondevice_test_harness]], deload-deferred). Related: [[reference_test_harness]].
