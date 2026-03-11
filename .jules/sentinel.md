## 2024-12-05 - [Critical] Fix ADB backup vulnerability
**Vulnerability:** Android application allowed ADB backups (`android:allowBackup="true"`).
**Learning:** Permitting backups on a sandboxed application allows extraction of the app sandbox via ADB, potentially exposing sensitive data and allowing full application state extraction.
**Prevention:** In Android applications, default to `android:allowBackup="false"` in the `AndroidManifest.xml` (unless explicitly configured with restrictive backup rules).