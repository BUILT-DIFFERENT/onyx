## 2024-05-20 - Prevent Full Application Sandbox Extraction via ADB Backup
**Vulnerability:** Android apps default to allowing full backups of application data (`android:allowBackup="true"`), which can expose sensitive offline-first data (e.g., Room database) if the device is backed up via ADB or Google Drive without user consent.
**Learning:** For offline-first applications that store sensitive user data locally, full backups pose a significant security risk. The `allowBackup` attribute must be explicitly set to `false` in the manifest unless granular backup rules are configured.
**Prevention:** Default to `android:allowBackup="false"` in the `AndroidManifest.xml` to prevent extraction of the application sandbox.
