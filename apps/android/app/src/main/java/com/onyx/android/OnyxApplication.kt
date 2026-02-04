package com.onyx.android

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.onyx.android.data.OnyxDatabase
import com.onyx.android.data.repository.NoteRepository
import com.onyx.android.data.serialization.StrokeSerializer
import com.onyx.android.device.DeviceIdentity
import com.onyx.android.recognition.MyScriptEngine

class OnyxApplication : Application() {
    lateinit var database: OnyxDatabase
    lateinit var noteRepository: NoteRepository
    lateinit var deviceIdentity: DeviceIdentity
    lateinit var myScriptEngine: MyScriptEngine

    override fun onCreate() {
        super.onCreate()
        Log.d("OnyxApp", "Initializing OnyxApplication...")

        database =
            Room
                .databaseBuilder(
                    applicationContext,
                    OnyxDatabase::class.java,
                    OnyxDatabase.DATABASE_NAME,
                ).fallbackToDestructiveMigration()
                .build()
        Log.d("OnyxApp", "Database initialized")

        deviceIdentity = DeviceIdentity(applicationContext)
        Log.d("OnyxApp", "DeviceIdentity initialized: ${deviceIdentity.getDeviceId()}")

        noteRepository =
            NoteRepository(
                noteDao = database.noteDao(),
                pageDao = database.pageDao(),
                strokeDao = database.strokeDao(),
                recognitionDao = database.recognitionDao(),
                deviceIdentity = deviceIdentity,
                strokeSerializer = StrokeSerializer,
            )
        Log.d("OnyxApp", "NoteRepository initialized")

        // Initialize MyScript engine (singleton, lazily initialized on first use)
        myScriptEngine = MyScriptEngine(applicationContext)
        val initResult = myScriptEngine.initialize()
        if (initResult.isFailure) {
            Log.e(
                "OnyxApp",
                "MyScript initialization failed: ${initResult.exceptionOrNull()?.message}",
            )
            // App can still run but recognition features will be unavailable
            // ViewModels should check isInitialized() before calling getEngine()
        } else {
            Log.i("OnyxApp", "MyScript engine initialized successfully")
        }

        Log.d("OnyxApp", "OnyxApplication initialization complete")
    }
}
