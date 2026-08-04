#!/bin/bash
# Convenience wrapper for building on this Arch Linux x86_64 machine
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export ANDROID_HOME=/home/migul/android-sdk
# no QEMU_LD_PREFIX — x86_64 runs aapt2 natively

./gradlew "$@"
