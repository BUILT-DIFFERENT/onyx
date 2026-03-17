## 2024-05-18 - ADB Backup Vulnerability
**Vulnerability:** Android application allowBackup attribute set to true allows full application sandbox extraction via ADB backup.
**Learning:** Defaulting allowBackup to true can expose sensitive data stored in the app's sandbox.
**Prevention:** Always default to android:allowBackup="false" in the AndroidManifest.xml unless explicitly configured with restrictive backup rules.
