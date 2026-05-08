package com.jib.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jib.app.ui.theme.JIBTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug deep-link: `am start ... --es stationId <uuid>` jumps straight to station detail.
        val initialStationId = intent.getStringExtra("stationId")
        setContent {
            JIBTheme {
                JibNavHost(initialStationId = initialStationId)
            }
        }
    }
}
