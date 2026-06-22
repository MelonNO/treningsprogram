# test-01 · Environment Setup (install & verify the toolchain)

Goal: a working, headless, reproducible test environment on the Debian host. Verify each step before moving on. **Confirm current versions/coordinates against the linked official docs** — the ones below are illustrative.

## Prerequisites
- JDK 17 (`java -version`). Maestro and modern Gradle need it.
- Android SDK command-line tools, plus the project's Gradle wrapper (`./gradlew`).

## 1. KVM acceleration (bare metal — this is the big speed lever)
```bash
sudo apt-get install -y cpu-checker qemu-kvm
kvm-ok                      # expect: "KVM acceleration can be used"
sudo usermod -aG kvm "$USER"   # then log out / back in
ls -l /dev/kvm                 # should exist and be group-accessible
emulator -accel-check          # emulator's own check
```
If `kvm-ok` fails: enable VT-x/AMD-V in BIOS.

## 2. Android SDK + emulator
```bash
yes | sdkmanager --licenses
sdkmanager "platform-tools" "emulator" "build-tools;34.0.0" \
           "platforms;android-34" \
           "system-images;android-34;google_apis;x86_64"
```
Use an **x86_64** image matched to the host (no ARM translation) and the lowest API the app supports.

## 3. Create a headless AVD
```bash
echo "no" | avdmanager create avd -n test -d pixel_6 \
  -k "system-images;android-34;google_apis;x86_64"
```

## 4. Boot headless, snapshot, kill animations
```bash
# headless build supersedes -no-window; software GL for no-GPU hosts
emulator -avd test -no-window -gpu swiftshader_indirect -no-boot-anim &
adb wait-for-device
adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done; echo booted'

# deterministic UI
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
```
Boot once, then save a snapshot and quick-boot from it for fast, clean restarts before each e2e run.

## 5. Robolectric (JVM unit/UI tests)
`build.gradle` (module):
```groovy
android { testOptions { unitTests { isIncludeAndroidResources = true } } }
dependencies {
  testImplementation "org.robolectric:robolectric:4.13"   // 4.10+ for native graphics
  testImplementation "junit:junit:4.13.2"
  testImplementation "androidx.compose.ui:ui-test-junit4:<compose-version>"  // if Compose
  debugImplementation "androidx.compose.ui:ui-test-manifest:<compose-version>"
}
```
Run: `./gradlew testDebugUnitTest`.

## 6. Roborazzi (JVM screenshot tests — no emulator)
```groovy
plugins { id "io.github.takahirom.roborazzi" version "<latest>" }
dependencies {
  testImplementation "io.github.takahirom.roborazzi:roborazzi:<v>"
  testImplementation "io.github.takahirom.roborazzi:roborazzi-compose:<v>"
  testImplementation "io.github.takahirom.roborazzi:roborazzi-junit-rule:<v>"
}
```
Tests use `@GraphicsMode(GraphicsMode.Mode.NATIVE)` and fixed device qualifiers.
Tasks: `./gradlew recordRoborazziDebug` (goldens), `verifyRoborazziDebug` (CI check), `compareRoborazziDebug` (diff report under `build/reports/roborazzi/`).

## 7. Maestro (on-device UI automation + agent MCP)
```bash
curl -fsSL "https://get.maestro.mobile.dev" | bash
export PATH="$PATH:$HOME/.maestro/bin"
maestro --version
maestro test flows/smoke.yaml      # run a flow against the running AVD
maestro studio                     # author/inspect flows interactively
maestro mcp                        # MCP server so the agent drives the device
```

## 8. Inspection helpers (ADB)
```bash
adb logcat                         # live logs
adb exec-out screencap -p > s.png  # screenshot
adb shell uiautomator dump && adb pull /sdcard/window_dump.xml  # UI hierarchy
```

## Acceptance (all must pass)
- [ ] `kvm-ok` reports acceleration usable; `/dev/kvm` accessible.
- [ ] Emulator boots **headless** and `sys.boot_completed` flips to 1.
- [ ] `./gradlew testDebugUnitTest` runs a sample Robolectric test green.
- [ ] `recordRoborazziDebug` produces a golden PNG; `verifyRoborazziDebug` passes.
- [ ] `maestro test` runs a trivial flow against the AVD; `maestro mcp` starts.

## Official docs to version-pin against
- Android emulator (headless/CI/acceleration): https://developer.android.com/studio/run/emulator-commandline
- Robolectric: https://robolectric.org/
- Roborazzi: https://github.com/takahirom/roborazzi
- Maestro: https://docs.maestro.dev/
