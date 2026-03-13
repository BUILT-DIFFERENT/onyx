## 2024-05-24 - Prevent ADB Backup Extraction
**Vulnerability:** Android apps with `android:allowBackup="true"` (the default) allow extraction of the full application sandbox via ADB backup, exposing local app data.
**Learning:** Default to `android:allowBackup="false"` in `AndroidManifest.xml` unless explicitly configured with restrictive backup rules to prevent full application sandbox extraction.
**Prevention:** Always set `android:allowBackup="false"` in Android applications.
