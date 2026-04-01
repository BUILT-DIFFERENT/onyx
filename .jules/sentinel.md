## 2024-05-18 - Prevent Full Application Sandbox Extraction via ADB Backup
**Vulnerability:** The AndroidManifest.xml had `android:allowBackup="true"` without restrictive rules.
**Learning:** This allows an attacker with physical access or ADB access to extract sensitive application data from the application's sandbox using `adb backup`.
**Prevention:** Default to `android:allowBackup="false"` unless explicitly configured with restrictive backup rules.
