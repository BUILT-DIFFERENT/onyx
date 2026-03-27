## 2024-05-24 - [Disable ADB Backup for Application Data Security]
**Vulnerability:** Android application manifest had `android:allowBackup="true"`, permitting physical data extraction via ADB backup.
**Learning:** Default Android behavior enables backups, which can inadvertently expose local private data like Room databases to physical or USB debugging access.
**Prevention:** Always verify `android:allowBackup="false"` in `AndroidManifest.xml` unless specifically configured with scoped backup rules.
