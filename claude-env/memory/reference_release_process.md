---
name: reference_release_process
description: "Standard GitHub release process for treningsprogram — tag format, APK naming, release body template, and step-by-step procedure"
metadata: 
  node_type: memory
  type: reference
  originSessionId: 539a7274-13f0-4205-9070-2d992f082c7f
---

## Release standard for MelonNO/treningsprogram

### Tag and version naming
- Always use **3-part semver**: `vMAJOR.MINOR.PATCH` (e.g. `v1.3.0`, never `v1.2`)
- `versionName` in `app/build.gradle.kts` matches the tag without the `v` prefix (e.g. `"1.3.0"`)
- `versionCode` increments by 1 each release (no skipping)
- MAJOR — breaking change or full rewrite; MINOR — new feature; PATCH — bug fix / polish / prompt tweak
- **The project-lead-orchestrator OWNS the version-number decision** (whether a change is PATCH / MINOR / MAJOR) — set 2026-06-24. As of 2026-06-28 the orchestrator ALSO performs the build + release itself (the separate build-release-shipper agent was removed); it executes the steps below only when the user explicitly asks to ship. The coordinator never overrides the version or ships. See [[agent-usage-guideline]].

### APK filename
`treningsprogram-vMAJOR.MINOR.PATCH.apk` (e.g. `treningsprogram-v1.3.0.apk`)
Never upload as `app-release.apk`.

### Release body template
```
## Treningsprogram vX.Y.Z

### What's new
- one bullet per feature

### Bug fixes
- one bullet per fix
(omit this section entirely if there are no bug fixes)

### Download
Install `treningsprogram-vX.Y.Z.apk` from the assets below.
Requires Android 8.0+.
```

### Release commit message
```
Release vX.Y.Z
```
(just that — no body needed)

### Step-by-step procedure (in order)
1. Bump `versionCode` (+1) and `versionName` (`"X.Y.Z"`) in `app/build.gradle.kts`
2. `git add app/build.gradle.kts && git commit -m "Release vX.Y.Z"`
3. `./build.sh assembleRelease`
4. `git tag vX.Y.Z && git push origin main --tags`
5. Create GitHub release via API (no `gh` CLI available — use `curl`):
   ```bash
   TOKEN=<from remote URL or env>
   REPO=MelonNO/treningsprogram
   curl -s -X POST "https://api.github.com/repos/$REPO/releases" \
     -H "Authorization: token $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"tag_name":"vX.Y.Z","name":"vX.Y.Z","body":"<escaped body>"}'
   ```
6. Upload APK from the `upload_url` returned in step 5:
   ```bash
   curl -s -X POST "<upload_url>?name=treningsprogram-vX.Y.Z.apk" \
     -H "Authorization: token $TOKEN" \
     -H "Content-Type: application/vnd.android.package-archive" \
     --data-binary @app/build/outputs/apk/release/app-release.apk
   ```

### Token location
**As of 2026-07-01 the token is NO LONGER in the remote URL** (coordinator scrubbed it for security). The remote is now clean `https://github.com/MelonNO/treningsprogram.git`; the PAT lives in `~/.git-credentials` (0600) via git's `store` credential helper. Retrieve it for the curl API calls with:
`TOKEN=$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill | sed -n 's/^password=//p')`
`git push` authenticates automatically via the helper. Never echo/commit the token. (Old method — extract from `https://USER:TOKEN@github.com/...` in the remote URL — no longer works.)

### Release commit body (updated practice)
The "just `Release vX.Y.Z`, no body" note below is stale — recent releases (v1.13.0…v1.17.0) use a descriptive multi-paragraph body + the standard `Co-Authored-By` / `Claude-Session` trailers. Match that.

### Known historical inconsistencies (pre-v1.3.0)
- `v1.0`: name was "v1.0 - Initial release" — drop the subtitle going forward
- `v1.1.0`, `v1.2.0`: body used "Whats new" (no apostrophe) — use "What's new" going forward
- `v1.2.0`: versionCode was not bumped in build.gradle before tagging
- `v1.2`: wrong tag (2-part), wrong APK name — superseded by v1.3.0; tag left in place for git history
