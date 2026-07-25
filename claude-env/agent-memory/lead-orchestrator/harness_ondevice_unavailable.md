---
name: harness-ondevice-unavailable
description: On-device Android e2e harness (emulator/AVD/Maestro) is NOT installed on this Pi
metadata:
  type: reference
---

As of 2026-06-23, this Pi has NO working on-device Android test harness:
- `~/android-sdk/platform-tools/adb` exists, and `~/android-sdk/cmdline-tools/.../bin/avdmanager` + `sdkmanager` exist.
- BUT: no `emulator` binary anywhere on disk, no system images (`~/android-sdk/system-images` absent), no AVD created (`~/.android/avd` empty), and no Maestro (`~/.maestro`, `which maestro` both absent).
- `/dev/kvm` IS present (so KVM accel is possible in principle).

**Why:** Means the brief's specified AVD+KVM+Maestro behavioral verification for UI/navigation/persistence items cannot be executed here without first installing an emulator + x86 system image + Maestro — a large, uncertain setup (x86 image under QEMU on ARM is the constrained path the briefs themselves flag).

**How to apply:** For UI/behavioral acceptance criteria, verify as far as possible via `./build.sh assembleDebug` (compile), `./build.sh test` (JVM/Robolectric logic), and code-path reasoning. Mark the actual on-device behavioral confirmation BLOCKED, with unblock path = install emulator+system image+Maestro (or user runs the app on a physical device / their own setup). Do NOT fabricate on-device results. See [[harness-build-test]].
