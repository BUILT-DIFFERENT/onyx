package com.onyx.android

import android.app.Application
import android.util.Log
import com.onyx.android.data.OnyxDatabase
import com.onyx.android.data.repository.NoteRepository
import com.onyx.android.data.serialization.StrokeSerializer
import com.onyx.android.data.thumbnail.ThumbnailGenerator
import com.onyx.android.device.DeviceIdentity
import com.onyx.android.pdf.PdfAssetStorage
import com.onyx.android.pdf.PdfPasswordStore
import com.onyx.android.recognition.MyScriptEngine

class OnyxApplication : Application(), AppContainer {
    override lateinit var database: OnyxDatabase
    override lateinit var noteRepository: NoteRepository
    override lateinit var deviceIdentity: DeviceIdentity
    override lateinit var pdfPasswordStore: PdfPasswordStore
    override lateinit var myScriptEngine: MyScriptEngine

    override fun onCreate() {
        super.onCreate()
        Log.d("OnyxApp", "Initializing OnyxApplication...")

        database = OnyxDatabase.build(applicationContext)
        Log.d("OnyxApp", "Database initialized")

        deviceIdentity = DeviceIdentity(applicationContext)
        Log.d("OnyxApp", "DeviceIdentity initialized: ${deviceIdentity.getDeviceId()}")

        pdfPasswordStore = PdfPasswordStore()
        Log.d("OnyxApp", "PdfPasswordStore initialized")

        val pdfAssetStorage = PdfAssetStorage(applicationContext)
        val thumbnailGenerator =
            ThumbnailGenerator(
                context = applicationContext,
                thumbnailDao = database.thumbnailDao(),
                noteDao = database.noteDao(),
                pageDao = database.pageDao(),
                pdfAssetStorage = pdfAssetStorage,
            )
        Log.d("OnyxApp", "ThumbnailGenerator initialized")

        noteRepository =
            NoteRepository(
                noteDao = database.noteDao(),
                pageDao = database.pageDao(),
                strokeDao = database.strokeDao(),
                recognitionDao = database.recognitionDao(),
                folderDao = database.folderDao(),
                tagDao = database.tagDao(),
                deviceIdentity = deviceIdentity,
                strokeSerializer = StrokeSerializer,
                thumbnailGenerator = thumbnailGenerator,
            )
        Log.d("OnyxApp", "NoteRepository initialized")

        // Initialize MyScript engine asynchronously to avoid blocking app startup.
        // Asset copying can take significant time on first launch.
        // Using a simple thread since this is a one-shot init with no cancellation needed.
        myScriptEngine = MyScriptEngine(applicationContext)
        Thread {
            val initResult = myScriptEngine.initialize()
            if (initResult.isFailure) {
                Log.e(
                    "OnyxApp",
                    "MyScript initialization failed: ${initResult.exceptionOrNull()?.message}",
                )
            } else {
                Log.i("OnyxApp", "MyScript engine initialized successfully")
            }
        }.start()

        Log.d("OnyxApp", "OnyxApplication initialization complete")
    }
}
