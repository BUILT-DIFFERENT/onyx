# Initial Setup Guide (Manual Configuration)

This guide covers manual configuration steps you need to complete before running `/start-work` on Plan A.

---

## 1. MyScript Certificate Setup (v4.3.0)

You have a MyScript developer account and license. **SDK v4.x uses a Java class for the certificate, not a bytes file.**

### Certificate Format (v4.x Change)
In SDK v4.x, the certificate is a **Java class** that you download from the MyScript Developer Portal, not a `.bytes` file.

### Certificate Location
```
apps/android/app/src/main/java/com/myscript/certificate/
```

### Steps
1. Log in to the [MyScript Developer Portal](https://developer.myscript.com/)
2. Navigate to: Dashboard → Your Application → Download Certificate
3. Download the certificate - it will be a Java file named `MyCertificate.java`
4. Create the certificate package directory:
   ```bash
   mkdir -p apps/android/app/src/main/java/com/myscript/certificate
   ```
5. Copy the certificate file:
   ```bash
   cp /path/to/MyCertificate.java apps/android/app/src/main/java/com/myscript/certificate/
   ```

### Verification
The certificate should be at:
```
apps/android/app/src/main/java/com/myscript/certificate/MyCertificate.java
```

And should look something like:
```java
package com.myscript.certificate;

public class MyCertificate {
    public static final byte[] getBytes() {
        return new byte[] { /* ... certificate bytes ... */ };
    }
}
```

### Code Reference (v4.x)
In your Kotlin code, you'll initialize the engine like this:
```kotlin
import com.myscript.certificate.MyCertificate
import com.myscript.iink.Engine

// Initialize engine with certificate class
val engine = Engine.create(MyCertificate.getBytes())
```

### Migration from v2.x
If upgrading from v2.x (`.bytes` file):
- Delete the old `myscript-certificate.bytes` from assets
- Download fresh certificate (Java class) from portal
- Update Engine initialization code

---

## 2. MyScript Recognition Assets (v4.3.0)

MyScript requires language recognition assets to perform handwriting recognition. These are separate from the certificate.

### Assets Download URL (v4.3.0)
Recognition assets are downloaded from:
```
https://download.myscript.com/iink/recognitionAssets_iink_4.3
```

This URL is referenced in `myscript-examples/samples/settings.gradle`:
```groovy
gradle.ext.iinkResourcesURL = "https://download.myscript.com/iink/recognitionAssets_iink_4.3"
```

### Assets Location
The recognition assets must be available at runtime in internal storage:
```
{filesDir}/myscript-recognition-assets/
```

**PLAN A DECISION: Bundle in APK (Option A)**

For Plan A (offline editor), we bundle assets in the APK for simplicity:
- Assets are bundled at build time in APK assets folder
- Copied to internal storage at first launch
- No network dependency on first launch

**Why not download on first launch for Plan A:**
- Adds network complexity (error handling, progress UI)
- Requires internet on first launch
- More failure modes to handle
- Can be added in Plan C if APK size is a concern

### Steps (AUTHORITATIVE for Plan A)

1. **Download recognition assets ZIP** from:
   ```
   https://download.myscript.com/iink/recognitionAssets_iink_4.3
   ```
   Specifically: `myscript-iink-recognition-text-en_US.zip`

2. **Extract to APK assets**:
   ```
   apps/android/app/src/main/assets/myscript-assets/
   ```
   This creates:
   ```
   assets/myscript-assets/
   ├── conf/
   │   └── en_US.conf
   └── resources/
       └── en_US/
           └── *.res
   ```

3. **Runtime copy** (handled by `MyScriptEngine.initialize()`):
   At first launch, copy from APK assets to internal storage:
   ```
   {filesDir}/myscript-recognition-assets/
   ```

4. **Engine configuration**:
   ```kotlin
   conf.setStringArray("configuration-manager.search-path", 
     arrayOf("{filesDir}/myscript-recognition-assets"))
   ```

### Verification
- [ ] `ls apps/android/app/src/main/assets/myscript-assets/conf/en_US.conf` exists
- [ ] After first launch: `adb shell ls /data/data/com.onyx.android/files/myscript-recognition-assets/conf/` shows files

### Code Reference (v4.x)
```kotlin
// Configure engine to use recognition assets
val conf = engine.configuration
val assetsPath = File(context.filesDir, "myscript-recognition-assets")

// Set search path for recognition resources
conf.setStringArray("configuration-manager.search-path", 
  arrayOf(assetsPath.absolutePath))

// Configure language(s)
conf.setString("lang", "en_US")  // Primary language

// For Raw Content recognition (freeform ink):
// No additional config needed - use "Raw Content" part type
```

### Language Packs
Common language identifiers:
- `en_US` - English (US)
- `en_GB` - English (UK)  
- `de_DE` - German
- `fr_FR` - French
- `es_ES` - Spanish
- `zh_CN` - Chinese (Simplified)
- `ja_JP` - Japanese

### Verification
After assets are extracted:
```
{filesDir}/myscript-recognition-assets/
├── conf/
├── resources/
│   ├── en_US/
│   │   ├── *.res
│   │   └── ...
│   └── ...
└── ...
```

---

## 3. Android SDK Setup

Ensure you have the following installed in Android Studio:

### SDK Manager → SDK Platforms
- Android 14 (API 34) or later for Compile SDK
- Android 11 (API 30) for your test tablet

### SDK Manager → SDK Tools
- Android SDK Build-Tools (latest)
- Android SDK Platform-Tools
- Android Emulator (for backup testing)

---

## 4. Tablet Connection (Optional)

To test on your physical tablet:

1. Enable Developer Options on tablet (tap Build Number 7 times in Settings → About)
2. Enable USB Debugging in Developer Options
3. Connect via USB or set up Wireless Debugging
4. Run `adb devices` to verify connection

---

## 5. Environment Variables (Optional)

Set any required local environment variables in `.env.local` or via your shell.

---

## Checklist

Before running `/start-work`:

- [ ] MyScript certificate (Java class) placed at `apps/android/app/src/main/java/com/myscript/certificate/MyCertificate.java`
- [ ] MyScript recognition assets available (either bundled or download-on-first-launch configured)
- [ ] Android Studio installed with required SDKs
- [ ] Physical tablet connected (optional but recommended)

---

## Notes

- The Android project directory (`apps/android/`) will be created by the first tasks in Plan A
- **SDK v4.x uses a Java class for the certificate** (not a `.bytes` file like v2.x)
- Recognition assets can be downloaded at first launch from `https://download.myscript.com/iink/recognitionAssets_iink_4.3`
- If you haven't created your MyScript developer account yet, visit: https://developer.myscript.com/
- Reference sample: `myscript-examples/samples/offscreen-interactivity/` for OffscreenEditor pattern
