package io.aircloak.client

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {

    private lateinit var motionDetector: MotionDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize hardware IMU accelerometer shake gesture detector
        motionDetector = MotionDetector(this) {
            runOnUiThread {
                Toast.makeText(
                    this,
                    "⚠️ AirCloak: Emergency IMU Shake Purge Activated. Clipboard Cleared!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        setContent {
            var currentScreen by remember { mutableStateOf("dashboard") }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0D1117)
                ) {
                    when (currentScreen) {
                        "dashboard" -> {
                            DashboardScreen(
                                onNavigateToVision = { currentScreen = "vision" },
                                onTriggerEmergencyPurge = {
                                    Toast.makeText(
                                        this,
                                        "🛡️ Vault Purged: Clipboard & Volatile Keys Zeroized.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                        "vision" -> {
                            RedactionViewFinder(
                                isVisionActive = true
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        motionDetector.startListening()
    }

    override fun onPause() {
        super.onPause()
        motionDetector.stopListening()
    }
}
