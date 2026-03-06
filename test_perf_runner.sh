#!/bin/bash
cd apps/android
./gradlew cleanTestDebugUnitTest
./gradlew :app:testDebugUnitTest --tests "com.onyx.android.ink.ui.InkCanvasGeometryPerfTest" --info | grep -i "Performance time"
