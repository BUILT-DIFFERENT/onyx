## 2024-03-08 - Android ADB Backup Vulnerability
**Vulnerability:** Android apps with `android:allowBackup="true"` allow their entire application sandbox, including databases, shared preferences, and files, to be extracted using ADB backup, even without root access.
**Learning:** Default Android configurations can lead to full data extraction. `allowBackup` should be explicitly disabled unless specifically needed and restricted.
**Prevention:** Always set `android:allowBackup="false"` in `AndroidManifest.xml` (unless explicitly configured with restrictive backup rules) to prevent full application sandbox extraction via ADB backup.
