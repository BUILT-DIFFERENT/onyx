package com.onyx.android

import android.app.Application
import android.util.Log
import com.onyx.android.recognition.MyScriptEngine
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OnyxApplication : Application() {
    @Inject
    lateinit var myScriptEngine: MyScriptEngine

    override fun onCreate() {
        super.onCreate()
        Log.d("OnyxApp", "Initializing OnyxApplication...")

        // Initialize MyScript engine asynchronously to avoid blocking app startup.
        // Asset copying can take significant time on first launch.
        // Using a simple thread since this is a one-shot init with no cancellation needed.
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
