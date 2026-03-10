## 2024-05-14 - ADB Backup Vulnerability
**Vulnerability:** The Android manifest was set to `android:allowBackup="true"`, which allows attackers with physical access and ADB to extract the full application sandbox.
**Learning:** Default Android behavior enables backups, which can lead to sensitive data exposure if a device is lost or compromised.
**Prevention:** Always default to `android:allowBackup="false"` in `AndroidManifest.xml` unless explicitly configured with restrictive backup rules using an `android:fullBackupContent` or `android:dataExtractionRules` descriptor.
