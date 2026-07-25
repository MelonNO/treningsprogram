# Build environment

What the Pi had, and what the new machine needs.

## Toolchain versions

| Component | Version on the Pi |
|-----------|-------------------|
| JDK | OpenJDK **21.0.11** (Debian) |
| Android SDK platform | **android-34** |
| Android build-tools | **34.0.0** |
| Gradle (wrapper) | **8.6** |
| Android Gradle Plugin | **8.3.2** |
| Kotlin | **2.0.0** |
| compileSdk / targetSdk / minSdk | 34 / 34 / 26 |
| Node (for Claude Code) | **24.16.0** |
| Claude Code | **2.1.220** |

Gradle itself comes from the wrapper — no install needed.

## x86_64 host (the easy case)

```bash
# 1. JDK 21
sudo apt install openjdk-21-jdk          # or your distro's equivalent

# 2. Android SDK — commandline-tools, then:
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"

# 3. Point Gradle at it
echo "sdk.dir=$HOME/android-sdk" > local.properties

# 4. Edit build.sh — see below

# 5. Build
./build.sh assembleDebug
./build.sh test
```

### Edit `build.sh`

As shipped it hardcodes the Pi's paths:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
export ANDROID_HOME=/home/migul/android-sdk
export QEMU_LD_PREFIX=/opt/x86_64-sysroot
```

On x86_64: fix `JAVA_HOME` (the path ends `-amd64`, not `-arm64`), fix
`ANDROID_HOME`, and **delete the `QEMU_LD_PREFIX` line entirely** — see below
for why it existed.

## aarch64 host (Raspberry Pi, Apple Silicon Linux, ARM server)

Google does not ship an ARM64 build of **`aapt2`**, the Android asset packager
that AGP invokes during every build. On the Pi this was solved by running the
x86_64 `aapt2` binary under QEMU user-mode emulation:

```bash
sudo apt install qemu-user-static
# an x86_64 sysroot at /opt/x86_64-sysroot supplies the libs aapt2 links against
export QEMU_LD_PREFIX=/opt/x86_64-sysroot
```

`binfmt_misc` (set up by `qemu-user-static`) then transparently routes the
x86_64 binary through QEMU. That is the *only* reason `QEMU_LD_PREFIX` is in
`build.sh`.

Alternative, if you'd rather not emulate: pin an `aapt2` from a source that
publishes ARM64 builds and point AGP at it with
`-Pandroid.aapt2FromMavenOverride=/path/to/aapt2`.

## Gotchas carried over from the Pi

- **`./build.sh`, not `./gradlew`.** The wrapper script sets the three env vars.
  If you'd rather use `./gradlew` directly, export them from `~/.bashrc`.
- **Piping masks exit codes.** `./build.sh assembleDebug | tail` reports success
  even when the build failed — `tail`'s status wins. Use
  `set -o pipefail`, or check `${PIPESTATUS[0]}`, or just don't pipe.
- **Robolectric doesn't work on aarch64.** No native SQLite for that platform,
  so DB-migration tests can't run locally. If the new machine is x86_64 this
  restriction lifts — see `agent-memory/project-lead-orchestrator/reference_robolectric_sqlite_aarch64.md`.
- **One flaky test.** `H5DispatcherTest.consumesOnSuppliedDispatcherThread…`
  fails intermittently in full runs, passes in isolation. Thread timing in the
  SSE dispatcher, not a regression. See `memory/reference_flaky_h5dispatcher_test.md`.
- **Baseline is 916 tests, green in both variants.** Anything less means
  something didn't come across.

## On-device testing

There is a Waydroid + adb + Maestro harness documented in
`memory/reference_ondevice_test_harness.md`, with flows in `flows/`. It is
**dormant by standing instruction** — do not run it unless you explicitly
revoke that. Verification is build + unit tests; device checks are done by hand.
