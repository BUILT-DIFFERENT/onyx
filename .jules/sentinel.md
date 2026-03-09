## 2024-05-24 - [Disable ADB Backup]
**Vulnerability:** Android apps with `android:allowBackup="true"` allow full application sandbox extraction via ADB backup.
**Learning:** This is a severe security risk, as malicious actors with physical access can easily retrieve sensitive data, including authentication tokens and application databases.
**Prevention:** In Android applications, default to `android:allowBackup="false"` in `AndroidManifest.xml` unless explicitly configured with restrictive backup rules.
