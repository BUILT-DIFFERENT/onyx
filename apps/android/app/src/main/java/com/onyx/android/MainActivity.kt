package com.onyx.android

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.onyx.android.navigation.OnyxNavHost
import com.onyx.android.ui.theme.OnyxTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val startupStartMs = SystemClock.elapsedRealtime()
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            OnyxTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OnyxNavHost()
                }
            }
        }
        window.decorView.post {
            val startupHandoffMs = SystemClock.elapsedRealtime() - startupStartMs
            Log.i(TAG, "startup_handoff_ms=$startupHandoffMs")
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
