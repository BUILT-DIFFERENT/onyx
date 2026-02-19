package com.onyx.android.recognition

import android.content.Context
import android.util.Log
import com.myscript.certificate.MyCertificate
import com.myscript.iink.Engine
import java.io.File

/**
 * MyScript Engine wrapper - Application singleton.
 *
 * OWNERSHIP MODEL:
 * - MyScriptEngine: Application singleton, owns Engine only
 * - MyScriptPageManager: Per-ViewModel, owns OffscreenEditor per page
 *
 * This class:
 * - Creates and holds the Engine singleton
 * - Configures recognition assets path
 * - Exposes getEngine() for MyScriptPageManager to create OffscreenEditors
 *
 * Does NOT:
 * - Create OffscreenEditor (that's MyScriptPageManager's job)
 * - Manage ContentPackage/ContentPart (per-page, owned by MyScriptPageManager)
 *
 * Reference: myscript-examples/samples/offscreen-interactivity/
 */
@Suppress("TooManyFunctions")
class MyScriptEngine(
    private val context: Context,
) {
    @Volatile
    private var engine: Engine? = null

    /**
     * Get the Engine instance for creating page-specific OffscreenEditors.
     * Used by NoteEditorViewModel to create MyScriptPageManager instances.
     *
     * IMPORTANT: Call initialize() before getEngine().
     * Throws if engine not initialized.
     */
    fun getEngine(): Engine =
        checkNotNull(engine) {
            "MyScriptEngine not initialized. Call initialize() first."
        }

    /**
     * Check if engine is ready for use.
     */
    fun isInitialized(): Boolean = engine != null

    fun initialize(): Result<Unit> =
        runCatching {
            close()

            // Create engine with certificate (v4.x uses Java class, not bytes)
            engine = Engine.create(MyCertificate.getBytes())

            // Configure recognition assets path
            // Assets are bundled in APK at: assets/myscript-assets/recognition-assets/
            // Copied at first launch to: {filesDir}/myscript-recognition-assets/
            val conf = checkNotNull(engine).configuration
            val assetsPath = File(context.filesDir, "myscript-recognition-assets")
            ensureRecognitionAssetsPresent(assetsPath)
            val recognitionSearchPath = resolveRecognitionAssetsRoot(assetsPath)
            val tempFolder = File(context.cacheDir, "myscript-temp").apply { mkdirs() }

            conf.setStringArray(
                "configuration-manager.search-path",
                arrayOf(recognitionSearchPath.absolutePath),
            )
            conf.setString("content-package.temp-folder", tempFolder.absolutePath)
            // Use Canadian assets by default; allow US as fallback.
            conf.setString("lang", "en_CA")

            // NOTE: OffscreenEditor and ContentPackage are NOT created here.
            // They are per-page, managed by MyScriptPageManager.
            // MyScriptPageManager uses engine.createOffscreenEditor() per page.
        }.onSuccess {
            Log.i("MyScript", "MyScript v4.3 Engine initialized")
        }.onFailure { e ->
            Log.e("MyScript", "Engine initialization failed: ${e.message}", e)
            engine?.close()
            engine = null
        }

    @Synchronized
    fun restart(): Result<Unit> {
        Log.w("MyScript", "Restarting MyScript engine")
        return initialize()
    }

    @Synchronized
    fun ensureInitialized(): Result<Engine> {
        if (engine == null) {
            val result = initialize()
            if (result.isFailure) {
                return Result.failure(checkNotNull(result.exceptionOrNull()))
            }
        }
        return Result.success(getEngine())
    }

    fun close() {
        engine?.close()
        engine = null
    }

    /**
     * Copy bundled assets from APK to filesDir.
     * Called once at first initialization.
     */
    private fun copyAssetsToFilesDir(
        context: Context,
        assetPath: String,
        targetDir: File,
    ) {
        val assetManager = context.assets
        val files = assetManager.list(assetPath) ?: return

        targetDir.mkdirs()

        for (filename in files) {
            val srcPath = "$assetPath/$filename"
            val targetFile = File(targetDir, filename)

            // Check if it's a directory (has children)
            val children = assetManager.list(srcPath)
            if (!children.isNullOrEmpty()) {
                // Recurse into subdirectory
                copyAssetsToFilesDir(context, srcPath, targetFile)
                continue
            }

            copyAssetFile(assetManager, srcPath, targetFile)
        }
    }

    private fun ensureRecognitionAssetsPresent(assetsPath: File) {
        val existingRoot = resolveRecognitionAssetsRoot(assetsPath)
        if (hasRecognitionConfig(existingRoot)) {
            return
        }

        if (assetsPath.exists()) {
            assetsPath.deleteRecursively()
        }
        copyAssetsToFilesDir(context, "myscript-assets/recognition-assets", assetsPath)
    }

    private fun resolveRecognitionAssetsRoot(assetsPath: File): File {
        val legacyNestedRoot = File(assetsPath, "recognition-assets")
        return when {
            hasRecognitionConfig(assetsPath) -> assetsPath
            hasRecognitionConfig(legacyNestedRoot) -> legacyNestedRoot
            else -> assetsPath
        }
    }

    private fun hasRecognitionConfig(path: File): Boolean {
        val hasConfDir = File(path, "conf").isDirectory
        val hasResourcesDir = File(path, "resources").isDirectory
        return hasConfDir && hasResourcesDir
    }

    private fun copyAssetFile(
        assetManager: android.content.res.AssetManager,
        srcPath: String,
        targetFile: File,
    ) {
        targetFile.parentFile?.mkdirs()
        assetManager.open(srcPath).use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
