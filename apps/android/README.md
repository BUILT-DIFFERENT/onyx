# Android (authoring)

Kotlin + Room + Convex Android client. Offline-first with an op queue and deterministic replay.

## Java requirement
- A JDK is required (17+ recommended).
- Set `JAVA_HOME` to your JDK root directory.
- `gradlew.bat` and `scripts/gradlew.js` will try `JAVA_HOME` first and then fall back to common Windows installs (Microsoft JDK, Eclipse Adoptium, Android Studio JBR).
- Android SDK is resolved from `ANDROID_HOME`/`ANDROID_SDK_ROOT` first, then `%LOCALAPPDATA%\Android\Sdk`.

Windows (cmd):
```bat
setx JAVA_HOME "C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot"
setx ANDROID_HOME "%LOCALAPPDATA%\Android\Sdk"
```

Planned areas:
- `app/` main Android module
- `app/src/main` app code
- `app/src/test` unit tests
- `app/src/androidTest` instrumentation tests
