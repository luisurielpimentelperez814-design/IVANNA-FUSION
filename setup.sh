#!/bin/bash
echo "sdk.dir=$HOME/android-ndk-r26b" > app/local.properties
echo "ndk.dir=$HOME/android-ndk-r26b" >> app/local.properties
echo "Añade esto al build.gradle: ndkVersion '26.2.11394342'"
