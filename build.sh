#!/bin/bash
# Convenience wrapper for building on this Raspberry Pi
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
export ANDROID_HOME=/home/migul/android-sdk
export QEMU_LD_PREFIX=/opt/x86_64-sysroot

./gradlew "$@"
