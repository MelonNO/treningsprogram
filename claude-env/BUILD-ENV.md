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

## Arch Linux, x86_64 — the target machine

```bash
# 1. JDK 21 + Node (for Claude Code)
sudo pacman -S jdk21-openjdk nodejs npm
sudo archlinux-java set java-21-openjdk      # make 21 the default
archlinux-java status                        # verify
```

Arch installs it at **`/usr/lib/jvm/java-21-openjdk`** — no `-amd64`/`-arm64`
suffix, unlike Debian. That exact path goes into `build.sh`.

```bash
# 2. Android SDK — commandline-tools route (recommended)
mkdir -p ~/android-sdk/cmdline-tools
# download "Command line tools only" (Linux) from
#   https://developer.android.com/studio#command-line-tools-only
# unzip so the layout is ~/android-sdk/cmdline-tools/latest/bin/sdkmanager
cd ~/android-sdk/cmdline-tools && unzip ~/Downloads/commandlinetools-linux-*.zip \
  && mv cmdline-tools latest

export ANDROID_HOME=~/android-sdk
~/android-sdk/cmdline-tools/latest/bin/sdkmanager \
  "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

The commandline-tools route is preferred over the AUR `android-sdk` family: it
matches the Pi's layout exactly (`~/android-sdk`, self-owned), needs no AUR
helper, and avoids the `/opt/android-sdk` + `android-sdk-users` group permissions
dance. If you'd rather use the AUR packages, `ANDROID_HOME` becomes
`/opt/android-sdk` and you must add yourself to the `android-sdk-users` group.

```bash
# 3. Point Gradle at it
echo "sdk.dir=$HOME/android-sdk" > local.properties

# 4. Edit build.sh — see below

# 5. Build
./build.sh assembleDebug
./build.sh test
```

Working `build.sh` for Arch x86_64:

```bash
#!/bin/bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export ANDROID_HOME=$HOME/android-sdk
# no QEMU_LD_PREFIX — x86_64 runs aapt2 natively

./gradlew "$@"
```

Arch ships current packages, so expect JDK **21.0.x** (any patch level is fine —
the build pins language level, not patch) and Node ≥ 24. Both are newer than the
Pi's and neither has caused a problem for this project.

## Other x86_64 distros

```bash
sudo apt install openjdk-21-jdk       # Debian/Ubuntu — path ends -amd64
sudo dnf install java-21-openjdk-devel # Fedora
```

Then the same SDK, `local.properties`, and `build.sh` steps as above.

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
- **Robolectric didn't work on aarch64.** No native SQLite for that platform, so
  DB-migration tests couldn't run on the Pi. **On Arch x86_64 this restriction
  lifts** — those tests become runnable for the first time, which matters for
  item 02 of the current batch (DB v19→v20). See
  `agent-memory/project-lead-orchestrator/reference_robolectric_sqlite_aarch64.md`.
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
