package com.onyx.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.onyx.android.navigation.OnyxNavHost
import com.onyx.android.ui.theme.OnyxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnyxTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OnyxNavHost()
                }
            }
        }
    }
}
