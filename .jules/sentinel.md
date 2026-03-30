
## 2024-03-24 - Disable Android Backup
**Vulnerability:** Application sandbox extraction via ADB backup (android:allowBackup="true").
**Learning:** By default, Android applications can be fully backed up via ADB, allowing attackers with physical access to extract sensitive app data (like tokens or offline databases) from the sandbox.
**Prevention:** Always default to `android:allowBackup="false"` in AndroidManifest.xml unless explicitly configured with restrictive backup rules using an XML configuration.
