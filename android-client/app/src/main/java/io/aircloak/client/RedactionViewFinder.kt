package io.aircloak.client

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Data class representing real-time detected sensitive token on screen OCR feed.
 */
data class DetectedTokenOverlay(
    val id: String,
    val tokenType: String,
    val xOffsetRatio: Float,
    val yOffsetRatio: Float,
    val widthRatio: Float,
    val heightRatio: Float,
    val confidence: Float
)

/**
 * RedactionViewFinder
 * Camera/Vision preview screen with real-time dynamic bounding box overlays
 * that visually redact secrets (IP addresses, auth tokens, .env strings) on monitor screens.
 */
@Composable
fun RedactionViewFinder(
    modifier: Modifier = Modifier,
    isVisionActive: Boolean = true
) {
    // Pulsing radar animation
    val infiniteTransition = rememberInfiniteTransition(label = "RadarScanner")
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ScanLine"
    )

    // Simulated OCR detection tokens (representing active monitor secrets)
    val sampleDetections = remember {
        listOf(
            DetectedTokenOverlay("1", "AWS_ACCESS_KEY", 0.15f, 0.28f, 0.45f, 0.06f, 0.99f),
            DetectedTokenOverlay("2", "PUBLIC_IPV4", 0.20f, 0.45f, 0.35f, 0.05f, 0.96f),
            DetectedTokenOverlay("3", "DB_URI_PASSWORD", 0.10f, 0.62f, 0.55f, 0.07f, 0.98f)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
    ) {
        // 1. Simulated Camera Frame / Matrix Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Subtle Cyberpunk Grid
            val gridSize = 40.dp.toPx()
            for (x in 0..(canvasWidth / gridSize).toInt()) {
                drawLine(
                    color = Color(0x1538BDF8),
                    start = Offset(x * gridSize, 0f),
                    end = Offset(x * gridSize, canvasHeight),
                    strokeWidth = 1f
                )
            }
            for (y in 0..(canvasHeight / gridSize).toInt()) {
                drawLine(
                    color = Color(0x1538BDF8),
                    start = Offset(0f, y * gridSize),
                    end = Offset(canvasWidth, y * gridSize),
                    strokeWidth = 1f
                )
            }

            if (isVisionActive) {
                // Laser Scanner Sweep Line
                val currentY = canvasHeight * scanLineY
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF00FFCC), Color(0xFF00FFCC), Color.Transparent)
                    ),
                    start = Offset(0f, currentY),
                    end = Offset(canvasWidth, currentY),
                    strokeWidth = 3.dp.toPx()
                )

                // 2. Render Redaction Bounding Boxes & Shields
                sampleDetections.forEach { token ->
                    val boxLeft = canvasWidth * token.xOffsetRatio
                    val boxTop = canvasHeight * token.yOffsetRatio
                    val boxW = canvasWidth * token.widthRatio
                    val boxH = canvasHeight * token.heightRatio

                    // Solid Redaction Privacy Block
                    drawRoundRect(
                        color = Color(0xEE090D16),
                        topLeft = Offset(boxLeft, boxTop),
                        size = Size(boxW, boxH),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )

                    // Neon Cyberpunk Warning Border
                    drawRoundRect(
                        color = Color(0xFFFF3366),
                        topLeft = Offset(boxLeft, boxTop),
                        size = Size(boxW, boxH),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
                        )
                    )
                }
            }
        }

        // 3. ViewFinder HUD Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xCC161B22),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30363D))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isVisionActive) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = if (isVisionActive) Color(0xFF00FFCC) else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isVisionActive) "NPU VISION ACTIVE (60 FPS)" else "VISION REDACTOR OFFLINE",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Surface(
                color = Color(0xCCFF3366),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "3 SECRETS CLOAKED",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // 4. ViewFinder Target Reticle in Center
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.Center)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 2.dp.toPx()
                val corner = 24.dp.toPx()
                val reticleColor = if (isVisionActive) Color(0xFF00E5FF) else Color(0xFF555555)

                // Top Left
                drawLine(reticleColor, Offset(0f, 0f), Offset(corner, 0f), stroke)
                drawLine(reticleColor, Offset(0f, 0f), Offset(0f, corner), stroke)

                // Top Right
                drawLine(reticleColor, Offset(size.width, 0f), Offset(size.width - corner, 0f), stroke)
                drawLine(reticleColor, Offset(size.width, 0f), Offset(size.width, corner), stroke)

                // Bottom Left
                drawLine(reticleColor, Offset(0f, size.height), Offset(corner, size.height), stroke)
                drawLine(reticleColor, Offset(0f, size.height), Offset(0f, size.height - corner), stroke)

                // Bottom Right
                drawLine(reticleColor, Offset(size.width, size.height), Offset(size.width - corner, size.height), stroke)
                drawLine(reticleColor, Offset(size.width, size.height), Offset(size.width, size.height - corner), stroke)
            }
        }

        // 5. Bottom Overlay Info Bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            color = Color(0xE6161B22),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30363D))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFCC00),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Zero-Leak Guard: Real-time OCR Active",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Point viewfinder at IDE or monitor screen. Sensitive keys and tokens will be automatically blacked out locally on NPU without streaming video to any network endpoint.",
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
