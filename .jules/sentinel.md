## 2024-03-12 - Disable ADB Backup Extraction
**Vulnerability:** Android apps default to allowing full app data extraction via ADB backup when `android:allowBackup="true"`.
**Learning:** This exposes the entire application sandbox (including offline local databases, preferences, and potentially tokens) to physical extraction without requiring root access.
**Prevention:** Explicitly set `android:allowBackup="false"` in `AndroidManifest.xml` unless restrictive backup rules are specifically configured.
