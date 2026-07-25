# Feature batch 2026-07-03 — orchestrator checkpoint (disk-only, never commit)

Baseline: main = 3b05561 (v1.24.1), 840 unit tests green, DB v18, backup v5.
Branch: feature-batch-2026-07-03
Authorized: implement all 10 items + SHIP (own version choice; minor bump → plan v1.25.0).
Plan: single-pass self-implementation, order: S(schema v19+backup v6 for N5/N7) → B11 → N4 → B1 → N7 → N1 → N3 → N5 → B5 → B7 → B10. Commit per item/group on branch. Never stage unrelated tree changes; intake docs stay uncommitted.

## Milestones
- 2026-07-03: briefs read, branch created. Exploration starting.
- Unit S CONFIRMED: DB v19 + backup v6, commit 3a97ce6, targeted backup/migration tests green (BUILD SUCCESSFUL).
- B11 code in working tree (levelTitle ladder), test pending.
- B11 CONFIRMED (0f3bb32), N4 CONFIRMED (dd656e8), B1+N7 code+tests green (82b90b8) — B1/N7 UI is build-verified only (no on-device per standing rule). Next: N1.
- 2026-07-03 03:3x: resumed after session-limit kill. N1 CONFIRMED + committed. Live API calls spent: 0. Next: N3 (relative strength), then N5, B5, B7, B10.
- N3 CONFIRMED (df5f0a3). Next: N5 goals (biggest unit).
- N5 CONFIRMED (9ed1d82). Next: B5 rest-day recovery card.
- B5 CONFIRMED (5a5121a). Next: B7 monthly Wrapped.
- B7 CONFIRMED (794be34). Next: B10 widget (last item).
- LIVE-API DECISION: 0 calls spent. N4 verified via unit tests per its brief; harness cannot reach the Room-coupled buildSessionHistory path; additive-context precedents (R3 bodyweight line, B3 stall block) shipped safely without live calls.
- B10 CONFIRMED (8ff8c55). All 10 items code-complete. Full suite: 914 tests, only known H5 flake (passes isolated). Re-running suite; then ship prep (v1.25.0/vc65).
- FULL SUITE VERIFIED: 914 debug + 914 release, 0 failures (XML-tallied). H5 flake confirmed pre-existing (passes isolated).
- Release v1.25.0 (vc65) committed ac9a7e7; main fast-forwarded; assembleRelease running. Remaining: tag+push, GitHub release+asset (curl, --max-time high), API verify.
- SHIPPED 2026-07-03 ~04:1x: v1.25.0 (vc65) — main pushed 3b05561→ac9a7e7, tag v1.25.0=ac9a7e7 API-verified, release id 348376464 live (not draft), asset treningsprogram-v1.25.0.apk 103,600,660 bytes state=uploaded, download probe 206. APK md5 c71465241e6d695cb48a06d757d3d0d4, apksigner-verified (MelonNO cert). Live API calls spent: 0 of 15. BATCH COMPLETE.
