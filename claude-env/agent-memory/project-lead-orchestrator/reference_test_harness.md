---
name: reference-test-harness
description: What test harnesses actually work on this Pi — JVM unit tests yes, Android on-device (AVD/KVM/Maestro) NO
metadata:
  type: reference
---

Build/test on this Raspberry Pi 5 (aarch64) for the treningsprogram Android app:

- **JVM / Robolectric unit tests WORK**: `./build.sh test` (or `./build.sh :app:testDebugUnitTest`). Use `build.sh`, never `./gradlew` directly — it sets JAVA_HOME, ANDROID_HOME, QEMU_LD_PREFIX. Existing tests live in `app/src/test/java/com/migul/treningsprogram/`.
- **On-device harness (AVD + KVM + Maestro) is NOT available** as of 2026-06. Verified: arch is aarch64; `/proc/cpuinfo` has 0 CPUs with vmx/svm (no nested virt for x86_64 images); no emulator binary (`android-sdk/emulator/` absent); no AVDs (`~/.android/avd` absent); no system-images; Maestro not installed; `adb devices` shows nothing connected.
- **Consequence:** any item whose acceptance is behavioral/UI (persistence-across-process-kill, navigation, swap visuals, layout) CANNOT be truly verified on this host. The honest status for such items is "code-reviewed + compiles + logic unit-tested where extractable, BLOCKED on on-device verification" — do NOT claim on-device verification that did not happen.

**How to apply:** Push as much verification as possible onto the JVM by extracting pure logic into testable companion functions (the codebase already does this: `GamificationRepository.isWeightPr`, `LogWorkoutViewModel.resumeIndexFor`, `LogWorkoutViewModel.insertAfter`). For UI/persistence behavior, fall back to compile + diff review + unit tests on the extracted logic, and flag the on-device gap explicitly. See [[feedback-no-unprompted-testing]].
