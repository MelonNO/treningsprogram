---
name: harness-build-test
description: Build/test commands for the treningsprogram Android app on the Pi
metadata:
  type: reference
---

Build and test with `./build.sh <gradle-task>` (NOT `./gradlew` directly) — build.sh exports JAVA_HOME (Java 21 ARM64), ANDROID_HOME (/home/migul/android-sdk), QEMU_LD_PREFIX (/opt/x86_64-sysroot, for x86 aapt2 under QEMU). It just forwards args to ./gradlew.

Common: `./build.sh assembleDebug`, `./build.sh test`, `./build.sh lint`, `./build.sh installDebug`.

JVM/Robolectric unit tests live in `app/src/test/java/com/migul/treningsprogram/` (junit 4.13.2 + robolectric 4.13 + androidx.test + roborazzi). Existing tests: ExtractJsonTest, ProgramJsonParsingTest, ExerciseCatalogTest, ExerciseDbResolverTest, ProgramViewModelTest. This is the fast harness for any pure-logic rule (e.g. PR-baseline rule).

See [[harness-ondevice-unavailable]] for why on-device e2e can't run here.
